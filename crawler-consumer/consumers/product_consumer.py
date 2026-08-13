"""
产品数据消费者 —— 监听 product.crawl 队列，执行产品数据爬取。

处理流程:
1. 从 RabbitMQ 消费任务消息（包含 site_config_id、domain、type、category）
2. 将任务状态更新为 RUNNING
3. Shopify 使用专用接口；其他常见商城引擎复用 WooCommerce 选择器
4. 以子进程方式执行 Scrapy 命令，统计生成的商品数量
5. 将任务状态更新为 SUCCESS 或 FAILED
6. 将执行结果发布到 task.result 交换机
"""

import asyncio
import json
import os
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from loguru import logger
from consumers.base_consumer import BaseConsumer
from db.repository import CursorRepository
from config import QUEUE_PRODUCT_CRAWL, EXCHANGE_TASKS
import pika


class ProductConsumer(BaseConsumer):
    """产品数据爬取消费者 —— 继承自 BaseConsumer。

    监听 RabbitMQ 的 product.crawl 队列，调用 Shopify Scrapy 爬虫子进程执行产品爬取。

    参数:
        rabbitmq_url (str): RabbitMQ AMQP 连接 URL
    """

    def __init__(self, rabbitmq_url: str):
        """初始化 ProductConsumer。

        参数:
            rabbitmq_url (str): RabbitMQ AMQP 连接 URL
        """
        super().__init__(QUEUE_PRODUCT_CRAWL, rabbitmq_url)
        self.repo = CursorRepository()
        # Scrapy 项目路径：环境变量用于 Docker 部署，否则回退到本地相对路径
        env_path = os.environ.get("SCRAPY_PROJECT_PATH")
        if env_path:
            self.scrapy_project = Path(env_path)
        else:
            self.scrapy_project = Path(__file__).parent.parent.parent / "crawler-service" / "app" / "crawler" / "ecommerce_spider"

    async def process(self, message: dict):
        """处理产品爬取任务的核心逻辑。

        根据消息中的 site_type 分发到不同的爬取策略。

        参数:
            message (dict): 任务消息，格式为:
                {
                    "task_id": "任务ID",
                    "payload": {
                        "site_config_id": 站点配置ID,
                        "domain": "目标域名",
                        "type": "shopify",
                        "category": "产品分类"
                    }
                }

        异常:
            Exception: 任意异常均会记录失败状态并重新抛出
        """
        task_id = message["task_id"]
        payload = message["payload"]
        site_config_id = payload["site_config_id"]
        domain = payload["domain"]
        site_type = payload["type"]
        category = payload.get("category", "未知分类")

        await self.repo.connect()
        start = time.monotonic()

        try:
            await self.repo.update_task_status(task_id, "RUNNING", progress=10, progress_message="正在准备商品采集")

            supported_types = {
                "shopify", "woocommerce", "bigcommerce", "opencart", "magento",
                "prestashop", "shopline", "ecwid", "wix", "squarespace", "custom",
            }
            site_type = site_type.lower().strip()
            if site_type not in supported_types:
                raise Exception(f"Unsupported product crawl type: {site_type}. Supported: {', '.join(sorted(supported_types))}.")
            await self.repo.update_task_progress(task_id, 20, f"正在连接 {domain}")
            site_config = await self.repo.get_site_config(site_config_id)
            selectors = self._selector_config(site_config) if site_type != "shopify" else None
            crawl_task = asyncio.create_task(self._run_platform(domain, category, site_type, selectors))
            progress = 25
            while not crawl_task.done():
                try:
                    await asyncio.wait_for(asyncio.shield(crawl_task), timeout=3)
                except asyncio.TimeoutError:
                    progress = min(90, progress + 3)
                    await self.repo.update_task_progress(task_id, progress, "正在采集商品并写入数据库")
            result = await crawl_task
            await self.repo.update_task_progress(task_id, 95, "正在汇总采集结果")

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
        """启动 Shopify 爬虫子进程。

        参数:
            domain (str): 目标 Shopify 站点域名
            category (str): 产品分类名称

        返回:
            int: 成功生成的商品数量

        异常:
            Exception: Scrapy 子进程返回非零退出码时抛出
        """
        cmd = [
            "scrapy", "crawl", "shopify_crawl_fast",
            "-a", f"domain={domain}",
            "-a", f"category={category}",
            "-a", "mode=prod",
        ]
        return await self._exec_scrapy(cmd)

    async def _run_platform(self, domain: str, category: str, site_type: str, selectors: dict | None = None) -> int:
        """Dispatch a crawl; every non-Shopify engine uses the Woo selector spider."""
        if site_type == "shopify":
            return await self._run_shopify(domain, category)
        cmd = [
            "scrapy", "crawl", "platform_crawl",
            "-a", f"domain={domain}",
            "-a", f"category={category}",
            "-a", f"platform={site_type}",
            "-a", "selector_profile=woocommerce",
            "-a", "mode=prod",
        ]
        if selectors:
            cmd.extend(["-a", f"config_json={json.dumps(selectors, ensure_ascii=False)}"])
        return await self._exec_scrapy(cmd)

    @staticmethod
    def _selector_config(site_config: dict | None) -> dict:
        """Convert a bound WooCommerce template into spider selector keys."""
        templates = (site_config or {}).get("templates") or []
        if not templates:
            return {}
        template = templates[0]
        field_map = {
            "title_selector": "title", "price_selector": "price",
            "price_regex": "price_regex", "description_selector": "description",
            "images_selector": "images", "currency": "currency",
            "breadcrumb_links_selector": "breadcrumb_links",
            "breadcrumb_last_selector": "breadcrumb_last",
            "site_map_selector": "site_map",
        }
        selectors = {
            target: template[source]
            for source, target in field_map.items()
            if template.get(source)
        }
        extra = template.get("extra_selectors")
        if extra:
            try:
                selectors.update(json.loads(extra) if isinstance(extra, str) else extra)
            except (TypeError, json.JSONDecodeError):
                logger.warning("Ignoring invalid extra_selectors on site config")
        return selectors

    async def _exec_scrapy(self, cmd: list[str]) -> int:
        """以子进程方式执行 Scrapy 命令，统计生产的商品数量。

        在 self.scrapy_project 目录下执行给定的 Scrapy 命令，
        通过统计标准输出中 "成功生成商品" 出现的次数来估算商品数量。

        参数:
            cmd (list[str]): Scrapy 命令行参数列表

        返回:
            int: 生成的商品数量（最小值 0）

        异常:
            Exception: 子进程返回非零退出码时抛出，包含错误信息的前 500 字
        """
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

        # Scrapy sends its logs to stderr; include both streams so task history
        # reflects the records actually flushed by the persistence pipeline.
        output = stdout.decode() + stderr.decode()
        batches = re.findall(r"批量入库成功：(\d+) 条记录", output)
        item_count = sum(int(count) for count in batches) or output.count("成功生成商品")
        logger.info(f"📦 Scrapy produced ~{item_count} items")
        return max(item_count, 0)

    def _publish_result(self, task_id, status, rows_affected, new_cursor, duration_ms, error=None):
        """将任务执行结果发布到 RabbitMQ task.result 队列。

        参数:
            task_id (str): 任务唯一 ID
            status (str): 执行状态，"success" 或 "failed"
            rows_affected (int): 影响的数据库行数（生成商品数）
            new_cursor (dict | None): 新的游标信息（产品爬取暂不使用）
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
