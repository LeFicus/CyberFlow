"""
产品数据消费者 —— 监听 product.crawl 队列，执行产品数据爬取。

处理流程:
1. 从 RabbitMQ 消费任务消息（包含 site_config_id、domain、type、category）
2. 将任务状态更新为 RUNNING
3. Shopify、BigCommerce 使用独立爬虫；WooCommerce 等平台使用通用地图爬虫
4. 以子进程方式执行 Scrapy 命令，统计生成的商品数量
5. 将任务状态更新为 SUCCESS 或 FAILED
6. 将执行结果发布到 task.result 交换机
"""

import asyncio
import codecs
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


PRODUCT_CRAWL_TIMEOUT_SECONDS = max(60, int(os.getenv("PRODUCT_CRAWL_TIMEOUT_SECONDS", "1800")))


class ProductConsumer(BaseConsumer):
    """产品数据爬取消费者 —— 继承自 BaseConsumer。

    监听 RabbitMQ 的 product.crawl 队列，按商城平台调用对应 Scrapy 爬虫执行产品爬取。

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
            crawl_task = asyncio.create_task(
                self._run_platform(domain, category, site_type, selectors, task_id)
            )
            progress = 25
            deadline = time.monotonic() + PRODUCT_CRAWL_TIMEOUT_SECONDS
            while not crawl_task.done():
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    crawl_task.cancel()
                    await asyncio.gather(crawl_task, return_exceptions=True)
                    raise TimeoutError(
                        f"商品爬虫超过 {PRODUCT_CRAWL_TIMEOUT_SECONDS // 60} 分钟未完成"
                    )
                try:
                    await asyncio.wait_for(asyncio.shield(crawl_task), timeout=min(3, remaining))
                except asyncio.TimeoutError:
                    progress = min(94, progress + 1)
                    elapsed = int(time.monotonic() - start)
                    await self.repo.update_task_progress(
                        task_id, progress, f"正在采集商品并写入数据库（已运行 {elapsed // 60} 分钟）"
                    )
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

    async def _run_shopify(self, domain: str, category: str,
                           task_id: str | None = None) -> int:
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
        return await self._exec_scrapy(cmd, task_id)

    async def _run_platform(self, domain: str, category: str, site_type: str,
                            selectors: dict | None = None,
                            task_id: str | None = None) -> int:
        """Dispatch a crawl to its platform-specific spider."""
        if site_type == "shopify":
            return await self._run_shopify(domain, category, task_id)
        if site_type == "bigcommerce":
            return await self._run_bigcommerce(domain, category, selectors, task_id)
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
        return await self._exec_scrapy(cmd, task_id)

    async def _run_bigcommerce(self, domain: str, category: str,
                               selectors: dict | None = None,
                               task_id: str | None = None) -> int:
        """Run the dedicated BigCommerce sitemap and JSON-LD spider."""
        cmd = [
            "scrapy", "crawl", "bigcommerce_crawl",
            "-a", f"domain={domain}",
            "-a", f"category={category}",
            "-a", "mode=prod",
        ]
        if selectors:
            cmd.extend(["-a", f"config_json={json.dumps(selectors, ensure_ascii=False)}"])
        return await self._exec_scrapy(cmd, task_id)

    @staticmethod
    def _selector_config(site_config: dict | None) -> dict:
        """Convert a bound selector template into spider selector keys."""
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

    async def _exec_scrapy(self, cmd: list[str], task_id: str | None = None) -> int:
        """执行 Scrapy，并将合并后的 stdout/stderr 完整流式写入任务日志。

        在 self.scrapy_project 目录下执行给定的 Scrapy 命令，
        通过统计标准输出中 "成功生成商品" 出现的次数来估算商品数量。

        参数:
            cmd (list[str]): Scrapy 命令行参数列表
            task_id (str | None): 对应任务 ID；提供时持久化完整日志

        返回:
            int: 生成的商品数量（最小值 0）

        异常:
            Exception: 子进程返回非零退出码时抛出，包含日志末尾的错误信息
        """
        command_text = " ".join(cmd)
        logger.info(f"🚀 Running: {command_text}")
        if task_id:
            await self.repo.reset_task_log(task_id)
            await self.repo.append_task_log(
                task_id,
                f"[{datetime.now(timezone.utc).isoformat()}] $ {command_text}\n",
            )

        proc = await asyncio.create_subprocess_exec(
            *cmd,
            cwd=str(self.scrapy_project),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
        )

        pending_log = []
        pending_size = 0
        last_flush = time.monotonic()
        scan_buffer = ""
        error_tail = ""
        batch_count = 0
        generated_count = 0

        async def flush_log():
            nonlocal pending_log, pending_size, last_flush
            if task_id and pending_log:
                content = "".join(pending_log)
                pending_log = []
                pending_size = 0
                await self.repo.append_task_log(task_id, content)
                last_flush = time.monotonic()

        def collect_stats(text: str, final: bool = False):
            nonlocal scan_buffer, error_tail, batch_count, generated_count
            error_tail = (error_tail + text)[-4000:]
            scan_buffer += text
            lines = scan_buffer.splitlines(keepends=True)
            if lines and not final and not lines[-1].endswith(("\n", "\r")):
                scan_buffer = lines.pop()
            else:
                scan_buffer = ""
            for line in lines:
                batches = re.findall(r"批量入库成功：(\d+) 条记录", line)
                batch_count += sum(int(count) for count in batches)
                generated_count += line.count("成功生成商品")

        async def stream_output():
            nonlocal pending_size, last_flush
            decoder = codecs.getincrementaldecoder("utf-8")(errors="replace")
            while True:
                try:
                    # Wake once a second even when Scrapy is temporarily quiet,
                    # otherwise a small pending log buffer can appear frozen.
                    chunk = await asyncio.wait_for(proc.stdout.read(16384), timeout=1.0)
                except asyncio.TimeoutError:
                    await flush_log()
                    continue
                if not chunk:
                    break
                text = decoder.decode(chunk)
                if text:
                    pending_log.append(text)
                    pending_size += len(text.encode("utf-8"))
                    collect_stats(text)
                    if pending_size >= 32768 or time.monotonic() - last_flush >= 1:
                        await flush_log()
            tail = decoder.decode(b"", final=True)
            if tail:
                pending_log.append(tail)
                pending_size += len(tail.encode("utf-8"))
                collect_stats(tail)
            collect_stats("", final=True)
            await flush_log()

        stream_task = asyncio.create_task(stream_output())
        try:
            await asyncio.wait_for(
                asyncio.shield(stream_task), timeout=PRODUCT_CRAWL_TIMEOUT_SECONDS
            )
            await proc.wait()
        except asyncio.TimeoutError as exc:
            logger.error(
                f"⏱️ Scrapy timed out after {PRODUCT_CRAWL_TIMEOUT_SECONDS}s; terminating process"
            )
            proc.kill()
            await proc.wait()
            await stream_task
            if task_id:
                await self.repo.append_task_log(
                    task_id,
                    f"\n[CyberFlow] 爬虫超过 {PRODUCT_CRAWL_TIMEOUT_SECONDS} 秒，已自动终止。\n",
                )
            raise TimeoutError(
                f"Scrapy 爬虫超过 {PRODUCT_CRAWL_TIMEOUT_SECONDS // 60} 分钟未完成，已自动终止"
            ) from exc
        except asyncio.CancelledError:
            if proc.returncode is None:
                proc.kill()
            await proc.wait()
            await asyncio.shield(stream_task)
            if task_id:
                await asyncio.shield(self.repo.append_task_log(
                    task_id, "\n[CyberFlow] 任务被取消，爬虫子进程已终止。\n"
                ))
            raise

        if proc.returncode != 0:
            error_msg = error_tail.strip() or "Unknown error"
            raise Exception(f"Scrapy exited {proc.returncode}: {error_msg}")

        item_count = batch_count or generated_count
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
