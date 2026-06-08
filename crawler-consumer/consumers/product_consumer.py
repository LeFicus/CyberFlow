import asyncio
import json
import os
import time
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from loguru import logger
from consumers.base_consumer import BaseConsumer
from db.repository import CursorRepository
from config import QUEUE_PRODUCT_CRAWL, EXCHANGE_TASKS
import pika


class ProductConsumer(BaseConsumer):

    def __init__(self, rabbitmq_url: str):
        super().__init__(QUEUE_PRODUCT_CRAWL, rabbitmq_url)
        self.repo = CursorRepository()
        # Scrapy project is at the same level as crawler-consumer
        self.scrapy_project = Path(__file__).parent.parent.parent / "crawler-service" / "app" / "crawler" / "ecommerce_spider"

    async def process(self, message: dict):
        task_id = message["task_id"]
        payload = message["payload"]
        site_config_id = payload["site_config_id"]
        domain = payload["domain"]
        site_type = payload["type"]
        category = payload.get("category", "未知分类")

        await self.repo.connect()
        start = time.monotonic()

        try:
            await self.repo.update_task_status(task_id, "RUNNING")

            if site_type == "shopify":
                result = await self._run_shopify(domain, category)
            else:
                result = await self._run_woo(domain, category, site_config_id)

            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "SUCCESS", rows_affected=result, duration_ms=duration_ms
            )
            self._publish_result(task_id, "success", result, None, duration_ms)
            logger.success(f"✅ Product crawl done: {domain} ({result} items)")

        except Exception as e:
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "FAILED", error_msg=str(e), duration_ms=duration_ms
            )
            self._publish_result(task_id, "failed", 0, None, duration_ms, str(e))
            logger.error(f"❌ Product crawl failed: {e}")
            raise
        finally:
            await self.repo.close()

    async def _run_shopify(self, domain: str, category: str) -> int:
        """Run Shopify spider as subprocess."""
        cmd = [
            "scrapy", "crawl", "shopify_crawl_fast",
            "-a", f"domain={domain}",
            "-a", f"category={category}",
            "-a", "mode=prod",
        ]
        return await self._exec_scrapy(cmd)

    async def _run_woo(self, domain: str, category: str, site_config_id: int) -> int:
        """Run WooCommerce spider with merged selectors from DB."""
        config = await self.repo.get_site_config(site_config_id)
        if not config:
            raise Exception(f"Site config {site_config_id} not found")

        merged = self._merge_selectors(config.get("templates", []))
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".json", delete=False, encoding="utf-8"
        ) as f:
            json.dump(merged, f, ensure_ascii=False)
            config_file = f.name

        try:
            cmd = [
                "scrapy", "crawl", "woo_crawl",
                "-a", f"domain={domain}",
                "-a", f"category={category}",
                "-a", f"config_file={config_file}",
                "-a", "mode=prod",
            ]
            return await self._exec_scrapy(cmd)
        finally:
            os.unlink(config_file)

    def _merge_selectors(self, templates: list[dict]) -> dict:
        """Merge all templates' selectors: same key → XPath union with |."""
        field_keys = [
            "title_selector", "price_selector", "description_selector",
            "images_selector", "breadcrumb_links_selector",
            "breadcrumb_last_selector", "site_map_selector",
        ]
        merged = {"price_regex": r"[\d.,]+", "currency": "USD"}

        for key in field_keys:
            parts = []
            for t in templates:
                val = t.get(key)
                if val:
                    parts.append(val.strip())
                extra = t.get("extra_selectors")
                if extra:
                    if isinstance(extra, str):
                        try:
                            extra = json.loads(extra)
                        except json.JSONDecodeError:
                            extra = None
                    if isinstance(extra, dict):
                        json_key = key.replace("_selector", "")
                        if json_key in extra:
                            parts.append(extra[json_key].strip())

            if parts:
                seen = set()
                unique_parts = []
                for p in parts:
                    if p not in seen:
                        seen.add(p)
                        unique_parts.append(p)
                merged[key] = " | ".join(unique_parts)

        # Pick currency from first template that has one
        for t in templates:
            if t.get("currency"):
                merged["currency"] = t["currency"]
                break

        return merged

    async def _exec_scrapy(self, cmd: list[str]) -> int:
        """Execute Scrapy command and count items."""
        logger.info(f"🚀 Running: {' '.join(cmd)}")
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            cwd=str(self.scrapy_project),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await proc.communicate()

        if proc.returncode != 0:
            error_msg = stderr.decode()[:500] if stderr else "Unknown error"
            raise Exception(f"Scrapy exited {proc.returncode}: {error_msg}")

        output = stdout.decode()
        item_count = output.count("成功生成商品")
        logger.info(f"📦 Scrapy produced ~{item_count} items")
        return max(item_count, 0)

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
