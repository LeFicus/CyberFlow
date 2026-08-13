"""
订单数据消费者 —— 监听 order.crawl 队列，执行订单数据增量爬取。

处理流程:
1. 从 RabbitMQ 消费任务消息（包含 task_id 和 payload）
2. 将任务状态更新为 RUNNING
3. 调用 AsyncOrderCrawler 登录支付平台，按 order_id 增量拉取订单
4. 每条订单关联 site_info 表获取 admin_name、theme_name、product_category
5. 将订单数据 UPSERT 到 orders 表
6. 更新爬取游标（最大 order_id），标记增量位置
7. 将任务状态更新为 SUCCESS 或 FAILED
8. 将执行结果发布到 task.result 交换机
"""

import time
from datetime import datetime, timezone
from loguru import logger
from consumers.base_consumer import BaseConsumer
from crawlers.order_crawler import AsyncOrderCrawler
from db.repository import CursorRepository
from config import QUEUE_ORDER_CRAWL, EXCHANGE_TASKS
from config import (
    PAYMENT_API_BASE_URL,
    PAYMENT_API_ACCOUNT,
    PAYMENT_API_PASSWORD,
    VERIFY_SSL,
)
import pika
import json


class OrderConsumer(BaseConsumer):
    """订单数据爬取消费者 —— 继承自 BaseConsumer。

    监听 RabbitMQ 的 order.crawl 队列，执行订单数据的增量爬取。
    登录支付平台后按 order_id 增量拉取订单，关联 site_info 表补全站点上下文，
    最后将结果写入 orders 表。

    参数:
        rabbitmq_url (str): RabbitMQ AMQP 连接 URL
    """

    def __init__(self, rabbitmq_url: str):
        """初始化 OrderConsumer。

        参数:
            rabbitmq_url (str): RabbitMQ AMQP 连接 URL
        """
        super().__init__(QUEUE_ORDER_CRAWL, rabbitmq_url)
        self.repo = CursorRepository()

    async def process(self, message: dict):
        """处理订单爬取任务的核心逻辑。

        参数:
            message (dict): 任务消息，格式为:
                {
                    "task_id": "任务ID",
                    "payload": {
                        "cursor": {
                            "max_order_id": "上一次爬取的最大订单ID"
                        }
                    }
                }

        异常:
            Exception: 任意异常均会记录失败状态并重新抛出
        """
        task_id = message["task_id"]
        payload = message["payload"]
        # 提取增量游标——上次爬取的最大订单 ID，默认为 "0"
        since_order_id = payload.get("cursor", {}).get("max_order_id", "0")

        await self.repo.connect()
        start = time.monotonic()

        try:
            await self.repo.update_task_status(task_id, "RUNNING", progress=10, progress_message="正在连接支付平台")

            platform = payload.get("platform", {})
            strategy = payload.get("strategy", {})
            base_url = platform.get("baseUrl") or platform.get("base_url") or PAYMENT_API_BASE_URL
            username = platform.get("account") or platform.get("username") or PAYMENT_API_ACCOUNT
            password = platform.get("password") or PAYMENT_API_PASSWORD
            verify_ssl = _as_bool(platform.get("verifySsl", VERIFY_SSL))
            excluded_cards = strategy.get("filterCardNumberExclude") or []

            crawler = AsyncOrderCrawler(
                base_url,
                verify_ssl=verify_ssl,
                page_size=int(strategy.get("pageSize", 100)),
                filter_card_number_exclude=excluded_cards,
            )
            await self.repo.update_task_progress(task_id, 35, "正在拉取增量订单")
            records, new_cursor = await crawler.run(username, password, since_order_id)

            # 将订单写入数据库，同时关联 site_info 表
            await self.repo.update_task_progress(task_id, 75, f"正在保存 {len(records)} 条订单")
            saved_count = await self._save_orders(records)

            await self.repo.update_cursor("order_crawler", new_cursor)
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "SUCCESS", rows_affected=saved_count, duration_ms=duration_ms
            )

            self._publish_result(task_id, "success", saved_count,
                                {"max_order_id": new_cursor}, duration_ms)
            logger.success(f"✅ Order crawl done: fetched={len(records)}, saved={saved_count}, cursor={new_cursor}")

        except Exception as e:
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "FAILED", error_msg=str(e), duration_ms=duration_ms
            )
            self._publish_result(task_id, "failed", 0, None, duration_ms, str(e))
            logger.error(f"❌ Order crawl failed: {e}")
            raise
        finally:
            await self.repo.close()

    async def _save_orders(self, records: list[dict]) -> int:
        """批量写入订单数据，关联 site_info 表补全站点上下文。

        对每条订单记录：
        1. 根据 product_host（域名）查询 site_info 表获取 admin_name、
           theme_name 和 product_category
        2. 使用 INSERT ... ON DUPLICATE KEY UPDATE 实现幂等写入，
           已存在的订单更新 amount 和 pay_status_text

        参数:
            records (list[dict]): 订单记录列表
        """
        logger.info(f"💾 Saving {len(records)} order records")
        saved_count = 0
        site_matched = 0
        async with self.repo.pool.acquire() as conn:
            async with conn.cursor() as cur:
                for r in records:
                    # 根据订单的 product_host 查询站点上下文信息
                    product_host = (r.get("product_host") or "").replace("www.", "")
                    admin_name = ""
                    theme_name = ""
                    product_category = ""
                    if product_host:
                        await cur.execute(
                            "SELECT admin_name, theme_name, product_category FROM site_info WHERE site_domain=%s",
                            (product_host,),
                        )
                        site_row = await cur.fetchone()
                        if site_row:
                            admin_name, theme_name, product_category = site_row[0], site_row[1], site_row[2]
                            site_matched += 1

                    await cur.execute(
                        """INSERT INTO orders (id, amount, currency, create_time, product_host,
                           pay_status_text, customer_ip_country, shipping_email,
                           admin_name, theme_name, product_category)
                           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                           ON DUPLICATE KEY UPDATE
                           amount=VALUES(amount), pay_status_text=VALUES(pay_status_text)""",
                        (r.get("id"), r.get("amount"), r.get("currency"),
                         r.get("create_time"), product_host,
                         r.get("pay_status_text"), r.get("timeZone"),
                         r.get("shipping_email"), admin_name, theme_name, product_category),
                    )
                    saved_count += 1
        logger.info(f"💾 Order save complete: saved={saved_count}, site_matched={site_matched}")
        return saved_count

    def _publish_result(self, task_id, status, rows_affected, new_cursor, duration_ms, error=None):
        """将任务执行结果发布到 RabbitMQ task.result 队列。

        参数:
            task_id (str): 任务唯一 ID
            status (str): 执行状态，"success" 或 "failed"
            rows_affected (int): 影响的数据库行数
            new_cursor (dict | None): 新的游标信息（成功时包含 max_order_id）
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
