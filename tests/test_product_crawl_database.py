"""Optional real database checks against an explicitly selected local test server."""

import contextlib
import io
import os
import re
import unittest
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import MagicMock, patch
from uuid import uuid4

import pymysql

from test_product_crawl_phase1 import ROOT, product, spider_instance
from ecommerce_spider.crawl_result import PersistenceError
from ecommerce_spider.pipelines import MySQLRedisPipeline
from scripts import backfill_product_dedupe


@unittest.skipUnless(os.getenv("CYBERFLOW_TEST_MYSQL_PORT"), "Set CYBERFLOW_TEST_MYSQL_PORT for an isolated local test server")
class ProductDatabaseTests(unittest.TestCase):
    def setUp(self):
        self.name = "cyberflow_phase1_test_" + uuid4().hex[:12]
        self.config = {
            "host": "127.0.0.1", "port": int(os.environ["CYBERFLOW_TEST_MYSQL_PORT"]),
            "user": os.getenv("CYBERFLOW_TEST_MYSQL_USER", "root"),
            "password": os.getenv("CYBERFLOW_TEST_MYSQL_PASSWORD", ""),
            "charset": "utf8mb4", "autocommit": True,
        }
        self.connection = pymysql.connect(**self.config)
        self.addCleanup(self.connection.close)
        with self.connection.cursor() as cursor:
            cursor.execute(f"CREATE DATABASE `{self.name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        self.addCleanup(self.drop_database)
        self.connection.select_db(self.name)
        self.config["database"] = self.name
        self.addCleanup(patch.stopall)
        patch("ecommerce_spider.pipelines.redis.Redis", return_value=MagicMock()).start()
        self.output = contextlib.redirect_stdout(io.StringIO())
        self.output.__enter__()
        self.addCleanup(self.output.__exit__, None, None, None)

    def drop_database(self):
        if not re.fullmatch(r"cyberflow_phase1_test_[a-f0-9]{12}", self.name):
            raise ValueError("Unsafe test database name")
        with self.connection.cursor() as cursor:
            cursor.execute(f"DROP DATABASE `{self.name}`")

    def create_legacy_table(self, index_name="uk_sku"):
        schema = (ROOT / "backend-admin/src/main/resources/schema.sql").read_text(encoding="utf-8")
        statement = re.search(r"CREATE TABLE IF NOT EXISTS scraped_data\.ecommerce_products.*?;", schema, re.S).group()
        statement = statement.replace("scraped_data.ecommerce_products", "ecommerce_products")
        statement = statement.replace("source_domain   VARCHAR(255) NOT NULL", "source_domain   VARCHAR(255)")
        statement = statement.replace("UNIQUE KEY uk_product_domain_sku (source_domain, sku)", f"UNIQUE KEY `{index_name}` (sku)")
        statement = statement.replace("INDEX idx_product_dedupe (dedupe_key)", "UNIQUE KEY uk_product_dedupe (dedupe_key)")
        with self.connection.cursor() as cursor:
            cursor.execute(statement)

    def migrate(self):
        script = (ROOT / "script/migrations/20260827_fix_product_site_identity.sql").read_text(encoding="utf-8")
        script = script.replace("USE scraped_data;", f"USE `{self.name}`;")
        delimiter = ";"
        pending = []
        with self.connection.cursor() as cursor:
            for line in script.splitlines():
                if line.startswith("DELIMITER "):
                    delimiter = line.split()[1]
                    continue
                pending.append(line)
                if line.rstrip().endswith(delimiter):
                    sql = "\n".join(pending).strip()[:-len(delimiter)]
                    cursor.execute(sql)
                    pending = []

    def run_pipeline(self, items, batch_size=2):
        spider = spider_instance()
        pipeline = MySQLRedisPipeline(self.config, {}, batch_size)
        pipeline.open_spider(spider)
        for item in items:
            pipeline.process_item(item, spider)
        pipeline.close_spider(spider)
        return pipeline.metrics.counts

    def test_migration_supports_implicit_sku_index_and_is_repeatable(self):
        self.create_legacy_table("sku")
        with self.connection.cursor() as cursor:
            cursor.execute("INSERT INTO ecommerce_products (sku, source_domain) VALUES (' old ', NULL)")
        self.migrate()
        self.migrate()
        with self.connection.cursor() as cursor:
            MySQLRedisPipeline._check_identity(cursor)
            cursor.execute("SELECT sku, source_domain FROM ecommerce_products")
            self.assertEqual(cursor.fetchone(), ("old", "legacy-unknown"))

    def test_real_upsert_counts_and_cross_site_identity(self):
        self.create_legacy_table()
        self.migrate()
        counters = self.run_pipeline([product("A", "one.test"), product("A", "two.test")])
        self.assertEqual(counters["inserted"], 2)
        unchanged = self.run_pipeline([product("A", "one.test")])
        self.assertEqual(unchanged["unchanged"], 1)
        changed = product("A", "two.test")
        changed["Regular price"] = 12
        updated = self.run_pipeline([changed])
        self.assertEqual(updated["updated"], 1)
        self.assertEqual(updated["persisted"], 1)
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT source_domain, regular_price FROM ecommerce_products ORDER BY source_domain")
            self.assertEqual([(domain, float(price)) for domain, price in cursor.fetchall()], [("one.test", 10), ("two.test", 12)])

    def test_normalization_allows_previously_distinct_skus_on_different_sites(self):
        self.create_legacy_table()
        with self.connection.cursor() as cursor:
            cursor.execute("INSERT INTO ecommerce_products (sku, source_domain) VALUES (' A', 'one.test'), ('A', 'two.test')")
        self.migrate()
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT sku FROM ecommerce_products ORDER BY source_domain")
            self.assertEqual(cursor.fetchall(), (("A",), ("A",)))

    def test_shopify_variants_commit_independently_without_redis(self):
        from test_product_crawl_phase3 import shopify_product, spider_for
        from ecommerce_spider.spiders.shopify_crawl import ShopifyCrawlFastSpider

        self.create_legacy_table()
        self.migrate()
        payload = shopify_product()

        def save():
            spider = spider_for(ShopifyCrawlFastSpider)
            spider.shop_currency = "AUD"
            pipeline = MySQLRedisPipeline(self.config, {}, 1, redis_enabled=False)
            pipeline.open_spider(spider)
            for item in spider.emit_variants(payload, payload["variants"]):
                pipeline.process_item(item, spider)
            pipeline.close_spider(spider)
            self.assertIsNone(pipeline.r)
            return pipeline.metrics.counts

        self.assertEqual(save()["inserted"], 2)
        self.assertEqual(save()["unchanged"], 2)
        payload["variants"][1]["price"] = "210"
        counts = save()
        self.assertEqual((counts["updated"], counts["unchanged"], counts["persisted"]), (1, 1, 2))
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT sku, regular_price FROM ecommerce_products ORDER BY sku")
            self.assertEqual([(sku, float(price)) for sku, price in cursor.fetchall()],
                             [("SHOPIFY-42-101", 100.5), ("SHOPIFY-42-102", 140.7)])

    def test_real_batch_rollback_keeps_previous_commit(self):
        self.create_legacy_table()
        self.migrate()
        with self.connection.cursor() as cursor:
            cursor.execute("""CREATE TRIGGER reject_test_sku BEFORE INSERT ON ecommerce_products
                FOR EACH ROW BEGIN
                    IF NEW.sku = 'FAIL' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Injected failure'; END IF;
                END""")
        spider = spider_instance()
        pipeline = MySQLRedisPipeline(self.config, {}, 2)
        pipeline.open_spider(spider)
        for sku in ("A", "B", "C"):
            pipeline.process_item(product(sku), spider)
        with self.assertRaises(PersistenceError):
            pipeline.process_item(product("FAIL"), spider)
        pipeline.close_spider(spider)
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT sku FROM ecommerce_products ORDER BY sku")
            self.assertEqual(cursor.fetchall(), (("A",), ("B",)))
        self.assertEqual(pipeline.metrics.counts["persisted"], 2)

    def test_concurrent_upsert_has_one_insert_and_one_unchanged(self):
        self.create_legacy_table()
        self.migrate()
        with ThreadPoolExecutor(max_workers=2) as executor:
            counts = list(executor.map(lambda _: self.run_pipeline([product("A")], 1), range(2)))
        self.assertEqual(sum(result["inserted"] for result in counts), 1)
        self.assertEqual(sum(result["unchanged"] for result in counts), 1)

    def test_migration_conflict_aborts_without_deleting_records(self):
        self.create_legacy_table()
        with self.connection.cursor() as cursor:
            cursor.execute("ALTER TABLE ecommerce_products DROP INDEX uk_sku")
            cursor.execute("INSERT INTO ecommerce_products (sku, source_domain) VALUES ('A', 'shop.test'), ('A', ' shop.test ')")
        with self.assertRaises(pymysql.Error):
            self.migrate()
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) FROM ecommerce_products")
            self.assertEqual(cursor.fetchone()[0], 2)

    def test_backfill_never_merges_distinct_skus_or_restores_unique_hint(self):
        self.create_legacy_table()
        self.migrate()
        self.run_pipeline([product("A"), product("B")])
        with patch.object(backfill_product_dedupe, "config", return_value={**self.config, "autocommit": False}):
            backfill_product_dedupe.main()
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*), COUNT(DISTINCT dedupe_key) FROM ecommerce_products")
            self.assertEqual(cursor.fetchone(), (2, 1))
            MySQLRedisPipeline._check_identity(cursor)


if __name__ == "__main__":
    unittest.main()
