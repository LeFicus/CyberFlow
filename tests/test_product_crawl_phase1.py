"""Stage-one regression tests; also runs an isolated Scrapy subprocess fixture."""

import contextlib
import io
import json
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "crawler-consumer"))
sys.path.insert(0, str(ROOT / "crawler-service/app/crawler/ecommerce_spider"))

from scrapy import Request
from scrapy.crawler import Crawler, CrawlerProcess
from scrapy.exceptions import DropItem
from scrapy.http import HtmlResponse, XmlResponse
from scrapy.settings import Settings

from consumers.product_consumer import ProductConsumer
from consumers.product_result import ProductResultProtocol, RESULT_PREFIX
from ecommerce_spider.crawl_result import CrawlMetrics, PersistenceError
from ecommerce_spider.crawl_options import CrawlOptions
from ecommerce_spider.pipelines import MySQLRedisPipeline
from ecommerce_spider.spiders.bigcommerce_crawl import BigCommerceCrawlSpider
from ecommerce_spider.spiders.platform_crawl import PlatformCrawlSpider
from ecommerce_spider.spiders.shopify_crawl import ShopifyCrawlFastSpider


def product(sku="A", domain="shop.test", category="Tools"):
    return {
        "SKU": sku, "Name": "Useful hammer", "Description": "A product description",
        "Regular price": 10, "Categories": category, "Images": "https://shop.test/hammer.jpg",
        "原站域名": domain, "语言": "en", "产品标签": "main", "货币": "USD",
    }


def index_rows(name="uk_product_domain_sku", columns=("source_domain", "sku")):
    return [("ecommerce_products", 0, name, position, column, "A", 0, None)
            for position, column in enumerate(columns, 1)]


class MemoryDatabase:
    def __init__(self):
        self.rows = {}
        self.commits = 0
        self.rollbacks = 0
        self.fail_sku = None
        self.fail_commit = False
        self.indexes = index_rows()

    def connect(self, **kwargs):
        return MemoryConnection(self)


class MemoryConnection:
    def __init__(self, database):
        self.database = database
        self.pending = dict(database.rows)
        self.rowcount = 0

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()

    def cursor(self):
        return self

    def begin(self):
        self.pending = dict(self.database.rows)

    def execute(self, sql, values=None):
        if sql.startswith("SHOW INDEX"):
            return
        if values[0] == self.database.fail_sku:
            raise RuntimeError("Injected statement failure")
        identity = (values[9], values[0])
        previous = self.pending.get(identity)
        self.rowcount = 1 if previous is None else (0 if previous == values else 2)
        self.pending[identity] = values

    def fetchall(self):
        return self.database.indexes

    def commit(self):
        if self.database.fail_commit:
            raise RuntimeError("Injected commit failure")
        self.database.rows = dict(self.pending)
        self.database.commits += 1

    def rollback(self):
        self.database.rollbacks += 1
        self.pending = dict(self.database.rows)

    def close(self):
        pass


def spider_instance(spider_type=PlatformCrawlSpider):
    crawler = Crawler(spider_type, Settings())
    options = {"domain": "https://shop.test"}
    if spider_type == PlatformCrawlSpider:
        options["platform"] = "woocommerce"
    return spider_type.from_crawler(crawler, **options)


class PipelineTests(unittest.TestCase):
    def setUp(self):
        self.database = MemoryDatabase()
        self.spider = spider_instance()
        self.addCleanup(patch.stopall)
        patch("ecommerce_spider.pipelines.pymysql.connect", self.database.connect).start()
        patch("ecommerce_spider.pipelines.redis.Redis", return_value=MagicMock()).start()
        self.output = io.StringIO()
        self.redirect = contextlib.redirect_stdout(self.output)
        self.redirect.__enter__()
        self.addCleanup(self.redirect.__exit__, None, None, None)
        self.pipeline = MySQLRedisPipeline({}, {}, batch_size=2)
        self.pipeline.open_spider(self.spider)

    def test_commits_before_close_and_flushes_tail(self):
        for sku in ("A", "B", "C"):
            self.pipeline.process_item(product(sku), self.spider)
        self.assertEqual(len(self.database.rows), 2)
        self.assertEqual(self.pipeline.metrics.counts["persisted"], 2)
        self.pipeline.close_spider(self.spider)
        self.assertEqual(len(self.database.rows), 3)

    def test_same_sku_different_sites_are_independent(self):
        self.pipeline.process_item(product("A", "one.test"), self.spider)
        self.pipeline.process_item(product("A", "two.test"), self.spider)
        self.assertEqual(len(self.database.rows), 2)

    def test_same_name_image_different_skus_are_not_merged(self):
        self.pipeline.process_item(product("A"), self.spider)
        self.pipeline.process_item(product("B"), self.spider)
        self.assertEqual(len(self.database.rows), 2)

    def test_insert_update_unchanged_are_counted_after_commit(self):
        self.pipeline.process_item(product("A"), self.spider)
        self.pipeline.close_spider(self.spider)
        next_spider = spider_instance()
        pipeline = MySQLRedisPipeline({}, {}, 3)
        pipeline.open_spider(next_spider)
        pipeline.process_item(product("A"), next_spider)
        pipeline.process_item(product("B"), next_spider)
        pipeline.close_spider(next_spider)
        self.assertEqual(pipeline.metrics.counts["inserted"], 1)
        self.assertEqual(pipeline.metrics.counts["unchanged"], 1)
        last_spider = spider_instance()
        pipeline = MySQLRedisPipeline({}, {}, 1)
        pipeline.open_spider(last_spider)
        changed = product("A")
        changed["Regular price"] = 11
        pipeline.process_item(changed, last_spider)
        self.assertEqual(pipeline.metrics.counts["updated"], 1)
        self.assertEqual(len(self.database.rows), 2)

    def test_statement_failure_rolls_back_batch_preserves_prior_commits(self):
        for sku in ("A", "B", "C"):
            self.pipeline.process_item(product(sku), self.spider)
        self.database.fail_sku = "D"
        with self.assertRaises(PersistenceError):
            self.pipeline.process_item(product("D"), self.spider)
        self.pipeline.close_spider(self.spider)
        self.assertEqual(set(self.database.rows), {("shop.test", "A"), ("shop.test", "B")})
        self.assertEqual(self.pipeline.metrics.counts["persisted"], 2)
        self.assertEqual(self.pipeline.metrics.counts["failed"], 2)

    def test_commit_failure_does_not_count_attempted_inserts(self):
        self.database.fail_commit = True
        self.pipeline.process_item(product("A"), self.spider)
        with self.assertRaises(PersistenceError):
            self.pipeline.process_item(product("B"), self.spider)
        self.assertEqual(self.pipeline.metrics.counts["persisted"], 0)
        self.assertEqual(self.database.rows, {})
        self.assertNotIn("CYBERFLOW_CRAWL_PROGRESS=", self.output.getvalue())

    def test_redis_post_commit_error_does_not_erase_committed_totals(self):
        self.pipeline.r.pipeline.side_effect = RuntimeError("Redis unavailable after commit")
        self.pipeline.r.scard.side_effect = RuntimeError("Redis unavailable on close")
        self.pipeline.process_item(product("A"), self.spider)
        self.pipeline.process_item(product("B"), self.spider)
        self.pipeline.close_spider(self.spider)
        self.assertEqual(self.pipeline.metrics.counts["persisted"], 2)
        self.assertEqual(self.pipeline.metrics.counts["failed"], 0)

    def test_filters_have_reasons_and_do_not_count_as_persisted(self):
        self.pipeline.options = CrawlOptions(max_product_price_usd=130)
        item = product()
        item["Regular price"] = 999
        with self.assertRaises(DropItem):
            self.pipeline.process_item(item, self.spider)
        self.assertEqual(self.pipeline.metrics.counts["generated"], 1)
        self.assertEqual(self.pipeline.metrics.filtered_reasons["price_limit"], 1)
        self.assertEqual(self.pipeline.metrics.counts["persisted"], 0)

    def test_subcategory_threshold_is_streamed(self):
        for index in range(47):
            self.pipeline.process_item(product(str(index), category="Tools|||Hammers"), self.spider)
        self.assertEqual(self.database.rows, {})
        self.pipeline.process_item(product("47", category="Tools|||Hammers"), self.spider)
        self.assertEqual(len(self.database.rows), 48)
        self.assertTrue(all(row[4] == "Tools|||Hammers" for row in self.database.rows.values()))

    def test_small_subcategory_falls_back_without_whole_crawl_staging(self):
        self.pipeline.process_item(product("A", category="Tools|||Hammers"), self.spider)
        self.pipeline.close_spider(self.spider)
        self.assertEqual(self.database.rows[("shop.test", "A")][4], "Tools")

    def test_old_sku_or_dedupe_index_is_rejected(self):
        for name, columns in (("sku", ("sku",)), ("uk_sku", ("sku",)), ("uk_product_dedupe", ("dedupe_key",))):
            with self.subTest(name=name):
                self.database.indexes = index_rows() + index_rows(name, columns)
                with self.assertRaises(PersistenceError):
                    MySQLRedisPipeline._check_identity(self.database.connect())


class SpiderTests(unittest.TestCase):
    def test_all_sitemap_candidates_fail(self):
        spider = spider_instance(BigCommerceCrawlSpider)
        for index in range(3):
            request = spider._sitemap_request(index)
            failure = SimpleNamespace(request=request, getErrorMessage=lambda: "HTTP 403")
            requests = list(spider.sitemap_failed(failure))
        self.assertTrue(spider.discovery_fallback_started)
        self.assertEqual(requests[0].callback, spider.parse_navigation)
        spider.navigation_failed(failure)
        self.assertEqual(spider.metrics.failed_reasons["navigation_request"], 1)

    def test_unreadable_product_shard_is_fatal(self):
        spider = spider_instance()
        failure = SimpleNamespace(request=Request("https://shop.test/product-sitemap.xml"),
                                  getErrorMessage=lambda: "HTTP 403")
        self.assertEqual(list(spider.sitemap_failed(failure)), [])
        self.assertEqual(spider.metrics.failed_reasons["sitemap_request"], 1)

    def test_product_request_failure_is_counted(self):
        spider = spider_instance()
        spider.product_failed(SimpleNamespace(getErrorMessage=lambda: "HTTP 403"))
        self.assertEqual(spider.metrics.counts["failed"], 1)

    def test_shopify_invalid_json_and_wrong_schema_are_failures(self):
        for body in (b"not json", b"{}", b"[]", b'{"products":{}}'):
            with self.subTest(body=body):
                spider = spider_instance(ShopifyCrawlFastSpider)
                response = HtmlResponse("https://shop.test/products.json", body=body, encoding="utf-8",
                                        request=Request("https://shop.test/products.json"))
                self.assertEqual(list(spider.parse_products(response)), [])
                self.assertEqual(spider.metrics.counts["failed"], 1)

    def test_shopify_empty_catalog_is_explicit(self):
        spider = spider_instance(ShopifyCrawlFastSpider)
        response = HtmlResponse("https://shop.test/products.json", body=b'{"products":[]}',
                                encoding="utf-8", request=Request("https://shop.test/products.json"))
        list(spider.parse_products(response))
        self.assertTrue(spider.metrics.confirmed_empty)


class ResultTests(unittest.TestCase):
    def ready_metrics(self):
        metrics = CrawlMetrics()
        metrics.pipeline_opened = metrics.pipeline_closed = True
        return metrics

    def test_old_log_counts_never_substitute_for_a_result(self):
        parser = ProductResultProtocol()
        parser.consume("成功生成商品 → A")
        parser.consume("批量入库成功：100 条记录")
        with self.assertRaises(RuntimeError):
            parser.finish(0)
        self.assertEqual(parser.latest["persisted"], 0)

    def test_all_filtered_is_success_with_zero_commits(self):
        metrics = self.ready_metrics()
        metrics.counts.update(discovered=1, fetched=1, generated=1)
        metrics.filter("price_limit")
        parser = ProductResultProtocol()
        parser.consume(RESULT_PREFIX + json.dumps(metrics.finish("finished")))
        self.assertEqual(parser.finish(0)["persisted"], 0)

    def test_zero_without_confirmed_empty_is_failed(self):
        self.assertEqual(self.ready_metrics().finish("finished")["outcome"], "failed")

    def test_duplicate_malformed_and_inconsistent_results_are_rejected(self):
        metrics = self.ready_metrics()
        metrics.confirmed_empty = True
        result = metrics.finish("finished")
        scenarios = [["{"], [json.dumps(result)] * 2, [json.dumps({**result, "persisted": 5})]]
        for lines in scenarios:
            parser = ProductResultProtocol()
            for line in lines:
                parser.consume(RESULT_PREFIX + line)
            with self.assertRaises(RuntimeError):
                parser.finish(0)


class ConsumerTests(unittest.IsolatedAsyncioTestCase):
    async def test_success_uses_committed_count_and_summary(self):
        consumer = ProductConsumer.__new__(ProductConsumer)
        consumer.repo = AsyncMock()
        consumer.repo.get_site_config.return_value = {}
        consumer._publish_result = MagicMock()
        consumer._run_platform = AsyncMock(return_value={
            "persisted": 2, "inserted": 1, "updated": 1, "fetched": 8, "filtered": 6,
        })
        await consumer.process({"task_id": "t1", "payload": {
            "site_config_id": 1, "domain": "shop.test", "type": "bigcommerce",
        }})
        call = consumer.repo.update_task_status.call_args
        self.assertEqual(call.args[1], "SUCCESS")
        self.assertEqual(call.kwargs["rows_affected"], 2)
        self.assertIn("过滤 6", call.kwargs["progress_message"])

    async def test_abrupt_exit_keeps_last_commit_report(self):
        consumer = ProductConsumer.__new__(ProductConsumer)
        consumer.repo = AsyncMock()
        consumer.scrapy_project = ROOT
        metrics = CrawlMetrics()
        metrics.counts.update(discovered=2, fetched=2, generated=2, accepted=2, inserted=2, persisted=2)
        payload = "CYBERFLOW_CRAWL_PROGRESS=" + json.dumps(metrics.snapshot())
        command = [sys.executable, "-c", f"import os; print({payload!r}, flush=True); os._exit(17)"]
        with self.assertRaises(RuntimeError):
            await consumer._exec_scrapy(command, "fixture")
        self.assertEqual(consumer.crawl_metrics["persisted"], 2)

    async def test_failed_result_retains_partial_commits_in_task_and_message(self):
        consumer = ProductConsumer.__new__(ProductConsumer)
        consumer.repo = AsyncMock()
        consumer.repo.get_site_config.return_value = {}
        consumer._publish_result = MagicMock()

        async def fail(*args):
            consumer.crawl_metrics = {"persisted": 2, "inserted": 2, "failed": 1}
            raise RuntimeError("Partial request failure")

        consumer._run_platform = fail
        with self.assertRaises(RuntimeError):
            await consumer.process({"task_id": "t1", "payload": {
                "site_config_id": 1, "domain": "shop.test", "type": "bigcommerce",
            }})
        call = consumer.repo.update_task_status.call_args
        self.assertEqual(call.args[1], "FAILED")
        self.assertEqual(call.kwargs["rows_affected"], 2)
        self.assertEqual(consumer._publish_result.call_args.args[2], 2)

    async def test_real_scrapy_lifecycle_and_consumer_protocol(self):
        for scenario, expected, persisted in (
            ("success", "success", 2), ("blocked", "failed", 0),
            ("partial", "failed", 1), ("filtered", "success", 0),
            ("batch-failure", "failed", 1), ("empty", "success", 0),
            ("close-failure", "failed", 0), ("startup-failure", "failed", 0),
            ("bigcommerce-blocked", "failed", 0), ("shopify-blocked", "failed", 0),
            ("shopify-invalid", "failed", 0),
        ):
            with self.subTest(scenario=scenario):
                consumer = ProductConsumer.__new__(ProductConsumer)
                consumer.repo = AsyncMock()
                consumer.repo.get_task_status.return_value = "RUNNING"
                consumer.scrapy_project = ROOT
                consumer.crawl_metrics = {}
                command = [sys.executable, "-u", str(Path(__file__).resolve()), "crawl-fixture", scenario]
                if expected == "failed":
                    with self.assertRaises(RuntimeError):
                        await consumer._exec_scrapy(command, "fixture")
                else:
                    result = await consumer._exec_scrapy(command, "fixture")
                    self.assertEqual(result["outcome"], "success")
                self.assertEqual(consumer.crawl_metrics["persisted"], persisted)


class FixtureDownloadMiddleware:
    scenario = "success"

    def process_request(self, request, spider):
        path = request.url.split("shop.test", 1)[-1]
        if self.scenario.endswith("blocked") or (self.scenario == "partial" and path == "/product/b"):
            return HtmlResponse(request.url, status=403, request=request)
        if self.scenario == "shopify-invalid":
            return HtmlResponse(request.url, body=b"not-json", encoding="utf-8", request=request)
        if path == "/sitemap_index.xml":
            if self.scenario == "empty":
                body = "<urlset/>"
            else:
                body = "<urlset><url><loc>https://shop.test/product/a</loc></url><url><loc>https://shop.test/product/b</loc></url></urlset>"
            return XmlResponse(request.url, body=body.encode(), encoding="utf-8", request=request)
        if "sitemap" in path:
            return XmlResponse(request.url, body=b"<urlset/>", encoding="utf-8", request=request)
        price = "999" if self.scenario == "filtered" else "10"
        body = (
            '<html><h1 class="product_title">Useful hammer</h1>'
            f'<span class="sku">{path[-1]}</span><meta itemprop="price" content="{price}">'
            '<meta property="og:image" content="/hammer.jpg">'
            '<meta itemprop="priceCurrency" content="USD">'
            '<div class="product-description">Durable hammer</div></html>'
        )
        return HtmlResponse(request.url, body=body.encode(), encoding="utf-8", request=request)


def run_crawl_fixture(scenario):
    FixtureDownloadMiddleware.scenario = scenario
    database = MemoryDatabase()
    if scenario in {"batch-failure", "close-failure"}:
        database.fail_sku = "b"
    if scenario == "startup-failure":
        database.indexes = index_rows("sku", ("sku",))
    settings = Settings()
    settings.setmodule("ecommerce_spider.settings")
    settings.setdict({
        "LOG_ENABLED": False, "ROBOTSTXT_OBEY": False, "COOKIES_ENABLED": False,
        "DOWNLOAD_DELAY": 0, "AUTOTHROTTLE_ENABLED": False, "RETRY_ENABLED": False,
        "CONCURRENT_REQUESTS": 1, "CONCURRENT_REQUESTS_PER_DOMAIN": 1, "DB_BATCH_SIZE": 1,
        "SCHEDULER_MEMORY_QUEUE": "scrapy.squeues.FifoMemoryQueue",
        "DOWNLOADER_MIDDLEWARES": {FixtureDownloadMiddleware: 10},
    }, priority="cmdline")
    if scenario == "close-failure":
        settings.set("DB_BATCH_SIZE", 10, priority="cmdline")
    with patch("ecommerce_spider.pipelines.pymysql.connect", database.connect), \
            patch("ecommerce_spider.pipelines.redis.Redis", return_value=MagicMock()):
        process = CrawlerProcess(settings)
        if scenario.startswith("shopify"):
            process.crawl(ShopifyCrawlFastSpider, domain="https://shop.test")
        elif scenario.startswith("bigcommerce"):
            process.crawl(BigCommerceCrawlSpider, domain="https://shop.test")
        else:
            process.crawl(PlatformCrawlSpider, domain="https://shop.test", platform="woocommerce",
                          crawl_options_json=json.dumps({"max_product_price_usd": 130}) if scenario == "filtered" else None)
        process.start()


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "crawl-fixture":
        run_crawl_fixture(sys.argv[2])
    else:
        unittest.main()
