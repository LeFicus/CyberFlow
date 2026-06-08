import os
import time
from datetime import datetime, timezone
from loguru import logger
from consumers.base_consumer import BaseConsumer
from crawlers.order_crawler import AsyncOrderCrawler
from db.repository import CursorRepository
from config import QUEUE_ORDER_CRAWL, EXCHANGE_TASKS
import pika
import json


class OrderConsumer(BaseConsumer):

    def __init__(self, rabbitmq_url: str):
        super().__init__(QUEUE_ORDER_CRAWL, rabbitmq_url)
        self.repo = CursorRepository()

    async def process(self, message: dict):
        task_id = message["task_id"]
        payload = message["payload"]
        since_order_id = payload.get("cursor", {}).get("max_order_id", "0")

        await self.repo.connect()
        start = time.monotonic()

        try:
            await self.repo.update_task_status(task_id, "RUNNING")

            crawler = AsyncOrderCrawler()
            username = os.getenv("CRAWLER_USERNAME", "")
            password = os.getenv("CRAWLER_PASSWORD", "")

            records, new_cursor = await crawler.run(username, password, since_order_id)

            await self._save_orders(records)

            await self.repo.update_cursor("order_crawler", new_cursor)
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "SUCCESS", rows_affected=len(records), duration_ms=duration_ms
            )

            self._publish_result(task_id, "success", len(records),
                                {"max_order_id": new_cursor}, duration_ms)
            logger.success(f"✅ Order crawl done: {len(records)} records, cursor={new_cursor}")

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

    async def _save_orders(self, records: list[dict]):
        """Bulk insert orders with dedup, cross-referencing site_info."""
        async with self.repo.pool.acquire() as conn:
            async with conn.cursor() as cur:
                for r in records:
                    product_host = r.get("product_host", "")
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

                    await cur.execute(
                        """INSERT INTO orders (id, amount, currency, create_time, product_host,
                           pay_status_text, customer_ip_country, shipping_email,
                           admin_name, theme_name, product_category)
                           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                           ON DUPLICATE KEY UPDATE
                           amount=VALUES(amount), pay_status_text=VALUES(pay_status_text)""",
                        (r.get("id"), r.get("amount"), r.get("currency"),
                         r.get("create_time"), product_host,
                         r.get("pay_status_text"), r.get("customer_ip_country"),
                         r.get("shipping_email"), admin_name, theme_name, product_category),
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
