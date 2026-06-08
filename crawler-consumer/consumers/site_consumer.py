import os
import time
from datetime import datetime, timezone
from loguru import logger
from consumers.base_consumer import BaseConsumer
from crawlers.site_crawler import AsyncSiteCrawler
from db.repository import CursorRepository
from config import RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USER, RABBITMQ_PASS, QUEUE_SITE_CRAWL, EXCHANGE_TASKS
import pika
import json


class SiteConsumer(BaseConsumer):

    def __init__(self, rabbitmq_url: str):
        super().__init__(QUEUE_SITE_CRAWL, rabbitmq_url)
        self.repo = CursorRepository()

    async def process(self, message: dict):
        task_id = message["task_id"]
        payload = message["payload"]
        since = payload.get("cursor", {}).get("last_updated_at")

        await self.repo.connect()
        start = time.monotonic()

        try:
            await self.repo.update_task_status(task_id, "RUNNING")

            username = payload.get("username") or os.getenv("CRAWLER_USERNAME", "")
            password = payload.get("password") or os.getenv("CRAWLER_PASSWORD", "")

            crawler = AsyncSiteCrawler(username, password)
            records, _ = await crawler.run(since=since)

            await self._upsert_site_info(records)

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
