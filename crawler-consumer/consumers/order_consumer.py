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
from urllib.parse import urlparse
from datetime import datetime, timezone
from loguru import logger
from consumers.base_consumer import BaseConsumer
from crawlers.order_crawler import AsyncOrderCrawler
from db.repository import CursorRepository, TaskCancelledError
from config import QUEUE_ORDER_CRAWL, EXCHANGE_TASKS
from config import VERIFY_SSL
import pika
import json

from consumers.order_identity import (
    build_dedupe_keys,
    parse_shipping_address,
    shipping_address_json,
)


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
        user_group = str(payload.get("user_group") or payload.get("userGroup") or "").strip()
        if not user_group or len(user_group) > 32 or not user_group.replace("_", "").replace("-", "").isalnum():
            raise ValueError("Order crawl task requires an existing site group")
        # 提取增量游标——上次爬取的最大订单 ID，默认为 "0"
        since_order_id = payload.get("cursor", {}).get("max_order_id", "0")

        await self.repo.connect()
        start = time.monotonic()

        try:
            await self.repo.reset_task_log(task_id)
            await self.append_task_log(
                self.repo, task_id,
                f"订单爬取开始：用户组={user_group}，增量游标={since_order_id}",
            )
            await self.repo.wait_for_task_control(task_id)
            await self.repo.update_task_status(task_id, "RUNNING", progress=10, progress_message=f"正在连接 {user_group} 组支付平台")

            platform = payload.get("platform", {})
            strategy = payload.get("strategy", {})
            base_url = str(platform.get("baseUrl") or platform.get("base_url") or "").strip()
            username = str(platform.get("account") or platform.get("username") or "").strip()
            password = str(platform.get("password") or "").strip()
            if not base_url or not username or not password or password == "******":
                raise ValueError(f"{user_group} 组 Payment API 配置不完整")
            verify_ssl = _as_bool(platform.get("verifySsl", VERIFY_SSL))
            excluded_cards = strategy.get("filterCardNumberExclude") or []

            crawler = AsyncOrderCrawler(
                base_url,
                verify_ssl=verify_ssl,
                page_size=int(strategy.get("pageSize", 100)),
                filter_card_number_exclude=excluded_cards,
            )
            await self.append_task_log(
                self.repo, task_id,
                f"已连接支付平台，分页大小={crawler.page_size}，开始拉取订单",
            )
            await self.repo.update_task_progress(task_id, 35, "正在拉取增量订单")
            records, new_cursor = await crawler.run(username, password, since_order_id)
            await self.append_task_log(self.repo, task_id, f"订单拉取完成：获取 {len(records)} 条，新的游标={new_cursor}")

            # 将订单写入数据库，同时关联 site_info 表
            await self.repo.update_task_progress(task_id, 75, f"正在保存 {len(records)} 条订单")
            await self.append_task_log(self.repo, task_id, f"开始保存 {len(records)} 条订单及商品详情")
            saved_count = await self._save_orders(records, user_group)

            await self.repo.update_cursor(f"order_crawler_{user_group}", new_cursor)
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.repo.update_task_status(
                task_id, "SUCCESS", rows_affected=saved_count, duration_ms=duration_ms
            )

            self._publish_result(task_id, "success", saved_count,
                                {"max_order_id": new_cursor}, duration_ms)
            await self.append_task_log(
                self.repo, task_id,
                f"订单爬取成功：保存 {saved_count} 条，耗时 {duration_ms} ms",
            )
            logger.success(f"✅ Order crawl done: fetched={len(records)}, saved={saved_count}, cursor={new_cursor}")

        except TaskCancelledError as e:
            await self.append_task_log(self.repo, task_id, f"订单爬取已取消：{e}")
            logger.info(f"⏹️ Order crawl cancelled by operator: {task_id} ({e})")
            return
        except Exception as e:
            duration_ms = int((time.monotonic() - start) * 1000)
            await self.append_task_log(self.repo, task_id, f"订单爬取失败：{e}")
            await self.repo.update_task_status(
                task_id, "FAILED", error_msg=str(e), duration_ms=duration_ms
            )
            self._publish_result(task_id, "failed", 0, None, duration_ms, str(e))
            logger.error(f"❌ Order crawl failed: {e}")
            raise
        finally:
            await self.repo.close()

    async def _save_orders(self, records: list[dict], user_group: str) -> int:
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
        affected_days: set[tuple[str, str]] = set()
        async with self.repo.pool.acquire() as conn:
            async with conn.cursor() as cur:
                for r in records:
                    # 根据订单的 product_host 查询站点上下文信息
                    product_host = self._normalize_domain(r.get("product_host"))
                    admin_name = ""
                    theme_name = ""
                    product_category = ""
                    site_tag = 0
                    site_user_group = ""
                    if product_host:
                        await cur.execute(
                            """SELECT admin_name, user_group, theme_name, product_category, site_tag
                               FROM site_info
                               WHERE LOWER(CASE WHEN LEFT(site_domain, 4) = 'www.'
                                   THEN SUBSTRING(site_domain, 5) ELSE site_domain END)=%s
                               LIMIT 1""",
                            (product_host.lower(),),
                        )
                        site_row = await cur.fetchone()
                        if site_row:
                            admin_name, site_user_group, theme_name, product_category, site_tag = site_row
                            site_matched += 1

                    # The site master table is the source of truth for group display.
                    # The task group is only a fallback for an order whose site has not
                    # yet been mirrored locally.
                    effective_user_group = site_user_group or user_group
                    product_info = self._product_info_json(r.get("productInfo", r.get("product_info")))
                    raw_shipping_address = r.get("shippingAddress", r.get("shipping_address"))
                    shipping_address = shipping_address_json(raw_shipping_address)
                    address = parse_shipping_address(raw_shipping_address)
                    shipping_email = r.get("shipping_email") or address.get("email")
                    customer_country = (
                        r.get("customer_ip_country")
                        or r.get("billing_address_country")
                        or address.get("country")
                    )
                    order_day = self._order_day(r.get("create_time"))
                    if order_day:
                        affected_days.add((effective_user_group, order_day))

                    await cur.execute(
                        """INSERT INTO orders (id, amount, currency, create_time, product_host,
                           pay_status_text, card_number, customer_ip_country, shipping_email,
                           shipping_address, is_valid, admin_name, user_group, theme_name,
                           product_category, site_tag, product_info)
                           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                           ON DUPLICATE KEY UPDATE
                           amount=VALUES(amount), currency=VALUES(currency),
                           create_time=VALUES(create_time), product_host=VALUES(product_host),
                           pay_status_text=VALUES(pay_status_text),
                           card_number=VALUES(card_number),
                           customer_ip_country=VALUES(customer_ip_country),
                           shipping_email=VALUES(shipping_email),
                           shipping_address=CASE
                               WHEN JSON_LENGTH(VALUES(shipping_address)) > 0 THEN VALUES(shipping_address)
                               ELSE shipping_address
                           END,
                           is_valid=COALESCE(VALUES(is_valid), is_valid),
                           admin_name=VALUES(admin_name), user_group=VALUES(user_group),
                           theme_name=VALUES(theme_name), product_category=VALUES(product_category),
                           site_tag=VALUES(site_tag),
                           product_info=CASE
                               WHEN JSON_LENGTH(VALUES(product_info)) > 0 THEN VALUES(product_info)
                               ELSE product_info
                           END""",
                        (r.get("id"), r.get("amount"), r.get("currency"),
                         r.get("create_time"), product_host,
                         r.get("pay_status_text"),
                         r.get("cardNumber") or r.get("card_no") or r.get("card_number"),
                         customer_country, shipping_email, shipping_address,
                         self._optional_int(r.get("is_valid")), admin_name, effective_user_group,
                         theme_name, product_category, site_tag, product_info),
                    )
                    saved_count += 1
                for group, order_day in sorted(affected_days):
                    await self._refresh_dedupe_keys(cur, group, order_day)
        logger.info(f"💾 Order save complete: saved={saved_count}, site_matched={site_matched}")
        return saved_count

    async def _refresh_dedupe_keys(self, cur, user_group: str, order_day: str) -> None:
        """Rebuild transitive email-or-address identities for one business day."""
        await cur.execute(
            """SELECT id, user_group, create_time, product_host, shipping_email, shipping_address
               FROM orders
               WHERE user_group=%s AND create_time >= %s
                 AND create_time < DATE_ADD(%s, INTERVAL 1 DAY)""",
            (user_group, order_day, order_day),
        )
        columns = [column[0] for column in cur.description]
        rows = [dict(zip(columns, row)) for row in await cur.fetchall()]
        keys = build_dedupe_keys(rows)
        if not keys:
            return
        await cur.executemany(
            "UPDATE orders SET dedupe_key=%s WHERE user_group=%s AND id=%s",
            [(key, group, order_id) for (group, order_id), key in keys.items()],
        )
        logger.info(
            f"🔗 Refreshed order identities: group={user_group}, day={order_day}, "
            f"rows={len(rows)}, deduplicated={len(set(keys.values()))}"
        )

    @staticmethod
    def _normalize_domain(value) -> str:
        raw = str(value or "").strip()
        if not raw:
            return ""
        parsed = urlparse(raw if "://" in raw else f"//{raw}")
        domain = (parsed.netloc or parsed.path).split("/")[0].split(":")[0].lower()
        return domain[4:] if domain.startswith("www.") else domain

    @staticmethod
    def _product_info_json(value) -> str:
        """Keep productInfo as a JSON array while tolerating absent/malformed API fields."""
        if isinstance(value, str):
            try:
                value = json.loads(value)
            except json.JSONDecodeError:
                value = []
        if isinstance(value, dict):
            value = [value]
        if not isinstance(value, list):
            value = []
        return json.dumps(value, ensure_ascii=False)

    @staticmethod
    def _order_day(value) -> str:
        if isinstance(value, datetime):
            return value.date().isoformat()
        text = str(value or "").strip()
        return text[:10] if len(text) >= 10 else ""

    @staticmethod
    def _optional_int(value):
        if value is None or value == "":
            return None
        try:
            return int(value)
        except (TypeError, ValueError):
            return None

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
