"""
站点信息消费者 —— 监听 site.crawl 队列，执行站点信息增量爬取。

处理流程:
1. 从 RabbitMQ 消费任务消息（包含 task_id 和 payload）
2. 将任务状态更新为 RUNNING
3. 调用 AsyncSiteCrawler 登录管理平台，获取域名列表和站点映射
4. 将爬取的站点信息 UPSERT 到 site_info 表
5. 更新爬取游标（当前 UTC 时间），标记增量位置
6. 将任务状态更新为 SUCCESS 或 FAILED
7. 将执行结果发布到 task.result 交换机，供 Spring Boot 回调
"""

import time
from datetime import datetime, timezone
from loguru import logger
from consumers.base_consumer import BaseConsumer
from crawlers.site_crawler import AsyncSiteCrawler
from db.repository import CursorRepository
from config import (
    ADMIN_API_BASE_URL,
    ADMIN_API_USERNAME,
    ADMIN_API_PASSWORD,
    VERIFY_SSL,
    QUEUE_SITE_CRAWL,
    EXCHANGE_TASKS,
)
import pika
import json


class SiteConsumer(BaseConsumer):
    """站点信息爬取消费者 —— 继承自 BaseConsumer。

    监听 RabbitMQ 的 site.crawl 队列，执行站点信息的增量爬取。
    包括登录管理平台、获取域名列表与站点映射、将结果写入 site_info 表。

    参数:
        rabbitmq_url (str): RabbitMQ AMQP 连接 URL
    """

    def __init__(self, rabbitmq_url: str):
        """初始化 SiteConsumer。

        参数:
            rabbitmq_url (str): RabbitMQ AMQP 连接 URL
        """
        super().__init__(QUEUE_SITE_CRAWL, rabbitmq_url)
        self.repo = CursorRepository()

    async def process(self, message: dict):
        """处理站点爬取任务的核心逻辑。

        参数:
            message (dict): 任务消息，格式为:
                {
                    "task_id": "任务ID",
                    "payload": {
                        "username": "管理员用户名",
                        "password": "管理员密码",
                        "cursor": {
                            "last_updated_at": "增量时间戳（可选）"
                        }
                    }
                }

        异常:
            Exception: 任意异常均会记录失败状态并重新抛出
        """
        task_id = message["task_id"]
        payload = message["payload"]
        # 从 payload 中提取增量游标——上次更新时间
        since = payload.get("cursor", {}).get("last_updated_at")

        await self.repo.connect()
        start = time.monotonic()

        try:
            await self.repo.update_task_status(task_id, "RUNNING", progress=10, progress_message="正在连接站点管理平台")

            platform = payload.get("platform", {})
            strategy = payload.get("strategy", {})
            base_url = platform.get("baseUrl") or platform.get("base_url") or ADMIN_API_BASE_URL
            username = platform.get("username") or ADMIN_API_USERNAME
            password = platform.get("password") or ADMIN_API_PASSWORD
            verify_ssl = _as_bool(platform.get("verifySsl", VERIFY_SSL))
            page_size = int(strategy.get("pageSize", 100))

            crawler = AsyncSiteCrawler(
                base_url,
                username,
                password,
                verify_ssl=verify_ssl,
                page_size=page_size,
                skip_site_check=_as_bool(strategy.get("skipSiteCheck", True)),
                fetch_admin_login_url=_as_bool(strategy.get("fetchAdminLoginUrl", False)),
                filter_built_only=_as_bool(strategy.get("filterBuiltOnly", False)),
            )
            await self.repo.update_task_progress(task_id, 35, "正在拉取站点与域名数据")
            records, _ = await crawler.run(since=since)

            # 将爬取结果批量 UPSERT 到 site_info 表
            await self.repo.update_task_progress(task_id, 75, f"正在保存 {len(records)} 条站点记录")
            await self._upsert_site_info(records)

            # 以当前 UTC 时间作为新的游标值
            new_cursor = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            await self.repo.update_cursor("site_crawler", new_cursor)

            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "SUCCESS", rows_affected=len(records), duration_ms=duration_ms
            )

            self._publish_result(task_id, "success", len(records),
                                {"last_updated_at": new_cursor}, duration_ms)
            logger.success(f"✅ Site crawl done: {len(records)} records")

        except Exception as e:
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "FAILED", error_msg=str(e), duration_ms=duration_ms
            )
            self._publish_result(task_id, "failed", 0, None, duration_ms, str(e))
            logger.error(f"❌ Site crawl failed: {e}")
            raise
        finally:
            await self.repo.close()

    async def _upsert_site_info(self, records: list[dict]):
        """将站点记录批量写入 site_info 表（UPSERT 语义）。

        使用 INSERT ... ON DUPLICATE KEY UPDATE 确保幂等性：
        若 (username, site_domain) 组合已存在则更新 admin_name、theme_name 和
        product_category 字段，否则插入新行。

        参数:
            records (list[dict]): 站点记录列表，每条记录包含:
                username, site_domain, admin_name, theme_name, product_category
        """
        async with self.repo.pool.acquire() as conn:
            async with conn.cursor() as cur:
                for r in records:
                    await cur.execute(
                        """INSERT INTO site_info (username, site_domain, admin_name, theme_name, product_category, created_at)
                           VALUES (%s, %s, %s, %s, %s, NOW())
                           ON DUPLICATE KEY UPDATE
                             admin_name=VALUES(admin_name),
                             theme_name=VALUES(theme_name),
                             product_category=VALUES(product_category)""",
                        (r["username"], r["site_domain"], r.get("admin_name"),
                         r.get("theme_name"), r.get("product_category")),
                    )

    def _publish_result(self, task_id, status, rows_affected, new_cursor, duration_ms, error=None):
        """将任务执行结果发布到 RabbitMQ task.result 队列。

        使用同步的 BlockingConnection 发布结果消息（简化的 fire-and-forget 模式），
        供 Spring Boot 端监听 task.result 队列以更新 UI 状态。

        参数:
            task_id (str): 任务唯一 ID
            status (str): 执行状态，"success" 或 "failed"
            rows_affected (int): 影响的数据库行数
            new_cursor (dict | None): 新的游标信息（成功时）
            duration_ms (int): 任务耗时（毫秒）
            error (str | None): 错误信息（失败时）
        """
        params = pika.URLParameters(self.rabbitmq_url)
        conn = pika.BlockingConnection(params)
        ch = conn.channel()
        result = {
            "task_id": task_id,
            "status": status,
            "rows_affected": rows_affected,
            "new_cursor": new_cursor,
            "duration_ms": duration_ms,
            "error": error,
            "finished_at": datetime.now(timezone.utc).isoformat(),
        }
        ch.basic_publish(
            exchange=EXCHANGE_TASKS,
            routing_key="crawler.task.result",
            body=json.dumps(result),
            properties=pika.BasicProperties(content_type="application/json"),
        )
        conn.close()


def _as_bool(value) -> bool:
    if isinstance(value, bool):
        return value
    if value is None:
        return False
    return str(value).lower() in {"1", "true", "yes", "on"}
