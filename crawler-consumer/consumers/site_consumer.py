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
from crawlers.site_index_crawler import AsyncSiteIndexCrawler, normalize_domain
from db.repository import CursorRepository, TaskCancelledError
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
        if message.get("type") == "site_index":
            await self._process_site_index(task_id, payload)
            return

        # 从 payload 中提取增量游标——上次更新时间
        requested_since = payload.get("cursor", {}).get("last_updated_at")

        await self.repo.connect()
        start = time.monotonic()

        try:
            await self.repo.reset_task_log(task_id)
            await self.append_task_log(
                self.repo, task_id,
                f"站点爬取开始：执行已建站全量镜像（原游标={requested_since or '无'}）",
            )
            await self.repo.wait_for_task_control(task_id)
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
            await self.append_task_log(self.repo, task_id, f"已连接站点管理平台，分页大小={page_size}")
            await self.repo.update_task_progress(task_id, 35, "正在拉取站点与域名数据")
            # Deleting domains abandoned upstream requires a complete remote
            # snapshot; an incremental response cannot safely drive deletion.
            records, _ = await crawler.run(since=None)
            await self.append_task_log(self.repo, task_id, f"站点数据拉取完成：获取 {len(records)} 条")

            # 将爬取结果批量 UPSERT 到 site_info 表
            await self.repo.update_task_progress(task_id, 75, f"正在保存 {len(records)} 条站点记录")
            await self.append_task_log(self.repo, task_id, f"开始保存 {len(records)} 条站点记录")
            deleted_sites, deleted_history = await self._upsert_site_info(records)
            await self.append_task_log(
                self.repo, task_id,
                f"镜像清理完成：删除废弃站点 {deleted_sites} 个、历史收录 {deleted_history} 条",
            )

            # 以当前 UTC 时间作为新的游标值
            new_cursor = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            await self.repo.update_cursor("site_crawler", new_cursor)

            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "SUCCESS", rows_affected=len(records), duration_ms=duration_ms
            )

            self._publish_result(task_id, "success", len(records),
                                {"last_updated_at": new_cursor}, duration_ms)
            await self.append_task_log(self.repo, task_id, f"站点爬取成功：保存 {len(records)} 条，耗时 {duration_ms} ms")
            logger.success(f"✅ Site crawl done: {len(records)} records")

        except TaskCancelledError as e:
            await self.append_task_log(self.repo, task_id, f"站点爬取已取消：{e}")
            logger.info(f"⏹️ Site crawl cancelled by operator: {task_id} ({e})")
            return
        except Exception as e:
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.append_task_log(self.repo, task_id, f"站点爬取失败：{e}")
            await self.repo.update_task_status(
                task_id, "FAILED", error_msg=str(e), duration_ms=duration_ms
            )
            self._publish_result(task_id, "failed", 0, None, duration_ms, str(e))
            logger.error(f"❌ Site crawl failed: {e}")
            raise
        finally:
            await self.repo.close()

    async def _process_site_index(self, task_id: str, payload: dict):
        """Fetch and persist today's indexing snapshot for every remote site."""
        await self.repo.connect()
        start = time.monotonic()
        try:
            await self.repo.reset_task_log(task_id)
            await self.append_task_log(self.repo, task_id, "收录统计开始")
            await self.repo.wait_for_task_control(task_id)
            await self.repo.update_task_status(
                task_id, "RUNNING", progress=10, progress_message="正在连接收录统计平台"
            )
            platform = payload.get("platform", {})
            strategy = payload.get("strategy", {})
            crawler = AsyncSiteIndexCrawler(
                platform.get("baseUrl") or platform.get("base_url") or ADMIN_API_BASE_URL,
                platform.get("username") or ADMIN_API_USERNAME,
                platform.get("password") or ADMIN_API_PASSWORD,
                verify_ssl=_as_bool(platform.get("verifySsl", VERIFY_SSL)),
                page_size=int(strategy.get("pageSize", 100)),
            )
            await self.append_task_log(self.repo, task_id, f"已连接收录统计平台，分页大小={crawler.page_size}")
            await self.repo.update_task_progress(task_id, 35, "正在拉取站点收录统计")
            records = await crawler.run()
            await self.append_task_log(self.repo, task_id, f"收录数据拉取完成：获取 {len(records)} 条")
            await self.repo.update_task_progress(task_id, 80, f"正在保存 {len(records)} 条收录记录")
            await self.append_task_log(self.repo, task_id, f"开始保存 {len(records)} 条收录记录")
            rows_affected, skipped_records, deleted_history = await self._upsert_index_history(records)
            await self.append_task_log(
                self.repo, task_id,
                f"收录关联校验完成：跳过未匹配已建站主数据 {skipped_records} 条、"
                f"清理孤立收录历史 {deleted_history} 条",
            )
            new_cursor = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
            await self.repo.update_cursor("site_index_crawler", new_cursor)

            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "SUCCESS", rows_affected=rows_affected, duration_ms=duration_ms
            )
            self._publish_result(
                task_id, "success", rows_affected,
                {"last_recorded_at": new_cursor}, duration_ms,
            )
            await self.append_task_log(self.repo, task_id, f"收录统计成功：保存 {rows_affected} 条，耗时 {duration_ms} ms")
            logger.success(f"✅ Site index crawl done: {rows_affected} records")
        except TaskCancelledError as e:
            await self.append_task_log(self.repo, task_id, f"收录统计已取消：{e}")
            logger.info(f"⏹️ Site index crawl cancelled by operator: {task_id} ({e})")
            return
        except Exception as e:
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.append_task_log(self.repo, task_id, f"收录统计失败：{e}")
            await self.repo.update_task_status(
                task_id, "FAILED", error_msg=str(e), duration_ms=duration_ms
            )
            self._publish_result(task_id, "failed", 0, None, duration_ms, str(e))
            logger.error(f"❌ Site index crawl failed: {e}")
            raise
        finally:
            await self.repo.close()

    async def _upsert_index_history(self, records: list[dict]) -> tuple[int, int, int]:
        """Refresh indexing only for sites present in the built-site master."""
        normalized = {
            normalize_domain(r.get("site_domain")): r
            for r in records if normalize_domain(r.get("site_domain"))
        }
        if not normalized:
            raise RuntimeError("远端未返回任何已建站收录数据，已停止镜像清理")
        remote_count = len(normalized)

        async with self.repo.pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(
                    """SELECT LOWER(CASE WHEN LEFT(TRIM(site_domain), 4)='www.'
                           THEN SUBSTRING(TRIM(site_domain), 5) ELSE TRIM(site_domain) END)
                       FROM site_info"""
                )
                built_domains = {row[0] for row in await cur.fetchall()}
                normalized = {
                    domain: item for domain, item in normalized.items()
                    if domain in built_domains
                }
                if not normalized:
                    raise RuntimeError("收录数据未匹配到已建站主数据，已停止入库")
                await cur.execute(
                    """SELECT LOWER(CASE WHEN LEFT(TRIM(site_domain), 4)='www.'
                           THEN SUBSTRING(TRIM(site_domain), 5) ELSE TRIM(site_domain) END)
                       FROM site_indexing_history
                       WHERE recorded_at >= CURDATE()
                         AND recorded_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)"""
                )
                existing = {row[0] for row in await cur.fetchall()}
                updates = [
                    (item["index_count"], item["product_count"], item.get("server_name"),
                     item.get("server_ip"), item.get("last_submitted_at"), domain)
                    for domain, item in normalized.items() if domain in existing
                ]
                inserts = [
                    (domain, item["index_count"], item["product_count"], item.get("server_name"),
                     item.get("server_ip"), item.get("last_submitted_at"))
                    for domain, item in normalized.items() if domain not in existing
                ]
                if updates:
                    await cur.executemany(
                        """UPDATE site_indexing_history
                           SET index_count=%s, product_count=%s, server_name=%s, server_ip=%s,
                               last_submitted_at=%s, recorded_at=NOW()
                           WHERE LOWER(CASE WHEN LEFT(TRIM(site_domain), 4)='www.'
                               THEN SUBSTRING(TRIM(site_domain), 5) ELSE TRIM(site_domain) END)=%s
                             AND recorded_at >= CURDATE()
                             AND recorded_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)""",
                        updates,
                    )
                if inserts:
                    await cur.executemany(
                        """INSERT INTO site_indexing_history
                           (site_domain, index_count, product_count, server_name, server_ip, last_submitted_at, recorded_at)
                           VALUES (%s, %s, %s, %s, %s, %s, NOW())""",
                        inserts,
                    )

                await cur.executemany(
                    """UPDATE site_info
                       SET builder_username=COALESCE(NULLIF(builder_username, ''), NULLIF(%s, '')),
                           server_name=COALESCE(NULLIF(server_name, ''), NULLIF(%s, '')),
                           server_ip=COALESCE(NULLIF(server_ip, ''), NULLIF(%s, '')),
                           admin_name=COALESCE(NULLIF(admin_name, ''), NULLIF(%s, '')),
                           user_group=COALESCE(NULLIF(user_group, ''), NULLIF(%s, '')),
                           theme_name=COALESCE(NULLIF(theme_name, ''), NULLIF(%s, '')),
                           last_submitted_at=COALESCE(%s, last_submitted_at)
                       WHERE LOWER(CASE WHEN LEFT(TRIM(site_domain), 4)='www.'
                           THEN SUBSTRING(TRIM(site_domain), 5) ELSE TRIM(site_domain) END)=%s""",
                    [
                        (item.get("builder_username"), item.get("server_name"),
                         item.get("server_ip"), item.get("admin_name"), item.get("user_group"),
                         item.get("theme_name"), item.get("last_submitted_at"), domain)
                        for domain, item in normalized.items()
                    ],
                )
                deleted_history = await self._delete_orphan_history(cur)
        return len(normalized), remote_count - len(normalized), deleted_history

    @staticmethod
    async def _delete_orphan_history(cur) -> int:
        """Remove index history for domains no longer in the built-site master."""
        await cur.execute(
            """DELETE h FROM site_indexing_history h
               LEFT JOIN site_info s
                 ON LOWER(CASE WHEN LEFT(TRIM(h.site_domain), 4)='www.'
                     THEN SUBSTRING(TRIM(h.site_domain), 5) ELSE TRIM(h.site_domain) END)
                  = LOWER(CASE WHEN LEFT(TRIM(s.site_domain), 4)='www.'
                     THEN SUBSTRING(TRIM(s.site_domain), 5) ELSE TRIM(s.site_domain) END)
               WHERE s.id IS NULL"""
        )
        return max(0, cur.rowcount)

    @staticmethod
    async def _prepare_active_domain_table(cur, domains) -> None:
        """Create a connection-local set used for safe authoritative deletion."""
        await cur.execute("DROP TEMPORARY TABLE IF EXISTS tmp_active_site_domains")
        await cur.execute(
            """CREATE TEMPORARY TABLE tmp_active_site_domains (
                   site_domain VARCHAR(255) PRIMARY KEY
               ) ENGINE=MEMORY"""
        )
        await cur.executemany(
            "INSERT IGNORE INTO tmp_active_site_domains (site_domain) VALUES (%s)",
            [(domain,) for domain in domains],
        )

    @staticmethod
    async def _delete_inactive_sites(cur) -> tuple[int, int]:
        """Delete current-site data absent from the latest built-site snapshot."""
        normalized_history = (
            "LOWER(CASE WHEN LEFT(TRIM(h.site_domain), 4)='www.' "
            "THEN SUBSTRING(TRIM(h.site_domain), 5) ELSE TRIM(h.site_domain) END)"
        )
        normalized_site = (
            "LOWER(CASE WHEN LEFT(TRIM(s.site_domain), 4)='www.' "
            "THEN SUBSTRING(TRIM(s.site_domain), 5) ELSE TRIM(s.site_domain) END)"
        )
        await cur.execute(
            f"""DELETE h FROM site_indexing_history h
                LEFT JOIN tmp_active_site_domains active
                  ON active.site_domain={normalized_history}
                WHERE active.site_domain IS NULL"""
        )
        deleted_history = max(0, cur.rowcount)
        await cur.execute(
            f"""DELETE s FROM site_info s
                LEFT JOIN tmp_active_site_domains active
                  ON active.site_domain={normalized_site}
                WHERE active.site_domain IS NULL"""
        )
        deleted_sites = max(0, cur.rowcount)
        return deleted_history, deleted_sites

    async def _upsert_site_info(self, records: list[dict]) -> tuple[int, int]:
        """将最新已建站集合写入 site_info，并删除远端已废弃站点。

        使用 INSERT ... ON DUPLICATE KEY UPDATE 确保幂等性：
        若 (username, site_domain) 组合已存在则更新 admin_name、theme_name 和
        product_category 字段，否则插入新行。

        参数:
            records (list[dict]): 站点记录列表，每条记录包含:
                username, site_domain, admin_name, theme_name, product_category
        """
        normalized = {
            normalize_domain(r.get("site_domain")): r
            for r in records if normalize_domain(r.get("site_domain"))
        }
        if not normalized:
            raise RuntimeError("远端未返回任何已建站数据，已停止镜像清理")

        async with self.repo.pool.acquire() as conn:
            async with conn.cursor() as cur:
                await self._prepare_active_domain_table(cur, normalized.keys())
                for domain, r in normalized.items():
                    await cur.execute(
                        """INSERT INTO site_info (username, builder_username, site_domain, server_name, server_ip, admin_name, user_group,
                           theme_name, product_category, last_submitted_at, domain_applied_at, created_at)
                           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                           ON DUPLICATE KEY UPDATE
                             builder_username=COALESCE(NULLIF(VALUES(builder_username), ''), builder_username),
                             server_name=COALESCE(NULLIF(VALUES(server_name), ''), server_name),
                             server_ip=COALESCE(NULLIF(VALUES(server_ip), ''), server_ip),
                             admin_name=VALUES(admin_name),
                             user_group=VALUES(user_group),
                             theme_name=VALUES(theme_name),
                             product_category=VALUES(product_category),
                             last_submitted_at=COALESCE(VALUES(last_submitted_at), last_submitted_at),
                             domain_applied_at=COALESCE(VALUES(domain_applied_at), domain_applied_at),
                             created_at=COALESCE(VALUES(created_at), created_at)""",
                        (r["username"], r.get("builder_username"), domain, r.get("server_name"),
                         r.get("server_ip"), r.get("admin_name"),
                         r.get("user_group"), r.get("theme_name"), r.get("product_category"),
                         r.get("last_submitted_at"), r.get("domain_applied_at"), r.get("created_at")),
                    )
                deleted_history, deleted_sites = await self._delete_inactive_sites(cur)
        return deleted_sites, deleted_history

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
