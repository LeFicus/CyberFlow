"""Validate products and commit bounded batches under a per-site SKU identity."""

import logging
from collections import defaultdict
from urllib.parse import urlparse

import pymysql
import redis
from redis.backoff import NoBackoff
from redis.retry import Retry
from pymysql.constants import CLIENT
from scrapy.exceptions import DropItem

from ecommerce_spider.crawl_result import PersistenceError, get_metrics
from ecommerce_spider.crawl_options import CrawlOptions
from ecommerce_spider.normalization import (
    UnknownCurrencyError,
    currency_to_usd,
    has_content,
    normalize_category,
    normalize_name,
    product_dedupe_key,
)


MIN_SUBCATEGORY_PRODUCTS = 48


class MySQLRedisPipeline:
    SQL = """
        INSERT INTO ecommerce_products
        (sku, name, description, regular_price, categories, images, cf_opingts,
         custom_category, product_role, source_domain, language, dedupe_key)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            name=VALUES(name), regular_price=VALUES(regular_price),
            categories=VALUES(categories), images=VALUES(images),
            description=VALUES(description), cf_opingts=VALUES(cf_opingts),
            custom_category=VALUES(custom_category), product_role=VALUES(product_role),
            language=VALUES(language), dedupe_key=VALUES(dedupe_key)
    """

    def __init__(self, mysql_config, redis_config, batch_size=50, redis_enabled=True):
        self.mysql_config = dict(mysql_config)
        self.mysql_config["autocommit"] = False
        self.mysql_config["client_flag"] = self.mysql_config.get("client_flag", 0) & ~CLIENT.FOUND_ROWS
        self.redis_config = redis_config
        self.redis_enabled = redis_enabled
        self.batch_size = max(1, int(batch_size))
        self.items_buffer = []
        self.pending_categories = defaultdict(list)
        self.ready_categories = set()
        self.logger = logging.getLogger(__name__)
        self.r = None
        self.spider_name = ""
        self.seen_skus = set()
        self.failed = False
        self.failed_items = 0

    @classmethod
    def from_crawler(cls, crawler):
        pipeline = cls(
            mysql_config=crawler.settings.get("MYSQL_CONFIG"),
            redis_config=crawler.settings.get("REDIS_CONFIG"),
            batch_size=crawler.settings.getint("DB_BATCH_SIZE", 50),
            redis_enabled=crawler.settings.getbool("PRODUCT_REDIS_ENABLED", True),
        )
        pipeline.metrics = get_metrics(crawler)
        return pipeline

    @staticmethod
    def _check_identity(cursor):
        cursor.execute("SHOW INDEX FROM ecommerce_products")
        unique_indexes = defaultdict(list)
        for row in cursor.fetchall():
            if row[1] == 0 and row[2] != "PRIMARY":
                unique_indexes[row[2]].append((row[3], row[4], row[7]))
        definitions = [
            [(column, prefix) for _, column, prefix in sorted(columns)]
            for columns in unique_indexes.values()
        ]
        if definitions != [[("source_domain", None), ("sku", None)]]:
            raise PersistenceError(
                "商品唯一索引未迁移：请先运行 db-migrate，使用 (source_domain, sku) 唯一索引"
            )

    def open_spider(self, spider):
        self.metrics = get_metrics(spider.crawler)
        self.options = getattr(spider, "crawl_options", CrawlOptions())
        self.spider_name = spider.name
        try:
            with pymysql.connect(**self.mysql_config) as connection:
                with connection.cursor() as cursor:
                    self._check_identity(cursor)
        except Exception as exc:
            self.failed = True
            self.metrics.error("pipeline_startup", str(exc))
            raise PersistenceError(str(exc)) from exc
        if self.redis_enabled:
            try:
                config = {**self.redis_config, "socket_connect_timeout": 2, "socket_timeout": 2,
                          "retry": Retry(NoBackoff(), 0)}
                self.r = redis.Redis(**config)
                self.r.ping()
            except Exception:
                self.r = None
                self.logger.warning("Redis 不可用，已降级为任务内去重和 MySQL 唯一索引；继续入库")
        self.metrics.pipeline_opened = True
        self.logger.info("Pipeline 已就绪：商品将按 %s 条一批持续提交 MySQL", self.batch_size)

    def _drop(self, reason, message):
        error = DropItem(message)
        error.filter_reason = reason
        raise error

    def process_item(self, item, spider):
        self.metrics.counts["generated"] += 1
        try:
            if self.failed:
                self.metrics.error("persistence_unavailable", "An earlier database batch failed")
                raise PersistenceError("数据库写入已经失败，停止接受新商品")
            sku = str(item.get("SKU") or "").strip()
            domain = str(item.get("原站域名") or "").strip().lower()
            if not sku or not domain:
                self._drop("missing_identity", "商品缺少 SKU 或原站域名")
            item["SKU"] = sku
            item["原站域名"] = domain
            item["Name"] = normalize_name(item.get("Name"))
            item["Categories"] = normalize_category(item.get("Categories"))
            try:
                item["Regular price"] = currency_to_usd(item.get("Regular price"), item.get("货币"))
            except UnknownCurrencyError as exc:
                self.metrics.error("unknown_currency", str(exc))
                self._drop("unknown_currency", str(exc))
            self.metrics.currency_sources[item.get("币种来源") or "item"] += 1
            self.metrics.currencies[str(item.get("货币")).upper()] += 1
            if item["Regular price"] <= 0:
                self._drop("invalid_price", "商品美元价格必须大于 0")
            if self.options.max_product_price_usd is not None and item["Regular price"] > self.options.max_product_price_usd:
                self._drop("price_limit", f"商品价格超过 ${self.options.max_product_price_usd:g}")
            if self.options.require_description and not has_content(item.get("Description")):
                self._drop("missing_description", "商品描述为空")
            if self.options.require_image and not has_content(item.get("Images")):
                self._drop("missing_image", "商品图片为空")
            item["语言"] = item.get("语言") or "en"
            item["产品标签"] = (
                "supplement" if str(item.get("产品标签", "")).strip().lower() == "supplement" else "main"
            )
            item["_dedupe_key"] = product_dedupe_key(domain, item["Name"], item.get("Images"))
            fingerprint = (domain, sku)
            if fingerprint in self.seen_skus:
                self._drop("duplicate_in_task", f"当前任务中 SKU 重复: {sku}")
            self.seen_skus.add(fingerprint)
            self.metrics.counts["accepted"] += 1
            category = item["Categories"]
            category_key = (domain, category)
            if "|||" in category and category_key not in self.ready_categories:
                self.pending_categories[category_key].append(dict(item))
                if len(self.pending_categories[category_key]) >= MIN_SUBCATEGORY_PRODUCTS:
                    self.ready_categories.add(category_key)
                    for pending in self.pending_categories.pop(category_key):
                        self._enqueue(pending)
            else:
                self._enqueue(dict(item))
            return item
        except DropItem as exc:
            self.metrics.filter(getattr(exc, "filter_reason", "invalid_title"))
            raise
        except PersistenceError:
            raise
        except Exception as exc:
            self.metrics.error("item_conversion", str(exc))
            raise PersistenceError(str(exc)) from exc

    def _enqueue(self, item):
        self.items_buffer.append(item)
        if len(self.items_buffer) >= self.batch_size:
            self._flush_to_mysql()

    @staticmethod
    def _values(item):
        return (
            item["SKU"], item["Name"], item.get("Description"), item["Regular price"],
            item.get("Categories"), item.get("Images"), item.get("cf_opingts"),
            item.get("自定义分类"), item["产品标签"], item["原站域名"],
            item["语言"], item["_dedupe_key"],
        )

    def _flush_to_mysql(self):
        """Use per-statement affected rows inside one transaction for exact counts."""
        if not self.items_buffer or self.failed:
            return
        connection = None
        inserted = updated = unchanged = 0
        try:
            connection = pymysql.connect(**self.mysql_config)
            connection.begin()
            with connection.cursor() as cursor:
                for item in self.items_buffer:
                    cursor.execute(self.SQL, self._values(item))
                    if cursor.rowcount == 1:
                        inserted += 1
                    elif cursor.rowcount == 2:
                        updated += 1
                    elif cursor.rowcount == 0:
                        unchanged += 1
                    else:
                        raise PersistenceError(f"Unexpected MySQL affected rows: {cursor.rowcount}")
            connection.commit()
        except Exception as exc:
            self.failed = True
            self.failed_items += len(self.items_buffer)
            self.metrics.error("mysql_batch", str(exc), count=len(self.items_buffer))
            if connection:
                connection.rollback()
            raise PersistenceError(f"MySQL 批次写入失败: {exc}") from exc
        finally:
            if connection:
                connection.close()
        persisted_items = self.items_buffer
        self.items_buffer = []
        self.metrics.committed(inserted, updated, unchanged)
        if self.r is None:
            return
        try:
            redis_batch = self.r.pipeline(transaction=False)
            for item in persisted_items:
                redis_batch.sadd(f"scraped_skus:{self.spider_name}:{item['原站域名']}", item["SKU"])
            redis_batch.execute()
        except Exception as exc:
            self.logger.warning("Redis 指纹更新失败，MySQL 已成功提交: %s", exc)
            self.r = None

    def close_spider(self, spider):
        try:
            if not self.failed:
                for (_, category), items in list(self.pending_categories.items()):
                    parent = category.split("|||", 1)[0]
                    for item in items:
                        item["Categories"] = parent
                        self._enqueue(item)
                self.pending_categories.clear()
                self._flush_to_mysql()
        finally:
            if self.failed:
                pending = self.metrics.counts["accepted"] - self.metrics.counts["persisted"] - self.failed_items
                if pending > 0:
                    self.metrics.error("uncommitted_items", "Products not committed after a batch failure", pending)
            self.metrics.pipeline_closed = self.metrics.pipeline_opened and not self.failed
        domain = str(getattr(spider, "domain", ""))
        if "://" in domain:
            domain = urlparse(domain).netloc
        redis_key = f"scraped_skus:{spider.name}:{domain.strip().lower()}"
        if self.r is None:
            return
        try:
            if getattr(spider, "mode", "prod") == "dev":
                self.r.delete(redis_key)
            else:
                self.logger.info("站点 Redis 指纹总数: %s", self.r.scard(redis_key))
        except Exception as exc:
            self.logger.warning("Redis 收尾失败，不影响已提交的 MySQL 数据: %s", exc)
