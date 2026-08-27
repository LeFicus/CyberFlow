"""Platform contracts, one-row-per-variant, Redis degradation and active-time control."""

import asyncio
import contextlib
import io
import json
from pathlib import Path
import sys
import time
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import test_product_crawl_phase1 as phase1
from test_product_crawl_phase2 import html, product_html
from scrapy import Request
from scrapy.crawler import Crawler, CrawlerProcess
from scrapy.http import XmlResponse
from scrapy.settings import Settings

from consumers.product_consumer import ProductConsumer
from consumers.product_control import ActiveDeadline
from db.repository import TaskCancelledError
from ecommerce_spider.crawl_result import CrawlMetrics, RESULT_PREFIX
from ecommerce_spider.pipelines import MySQLRedisPipeline
from ecommerce_spider.platforms import SPIDERS
from ecommerce_spider.request_policy import ProductRequestPolicyMiddleware
from ecommerce_spider.spiders.magento_crawl import MagentoCrawlSpider
from ecommerce_spider.spiders.wix_crawl import WixCrawlSpider
from ecommerce_spider.spiders.ecwid_crawl import EcwidCrawlSpider
from ecommerce_spider.spiders.shopline_crawl import ShoplineCrawlSpider
from ecommerce_spider.spiders.shopify_crawl import ShopifyCrawlFastSpider


ENGINES = {"magento": MagentoCrawlSpider, "wix": WixCrawlSpider,
           "ecwid": EcwidCrawlSpider, "shopline": ShoplineCrawlSpider}


def spider_for(cls, config=None):
    settings = Settings({"PRODUCT_PLATFORM_CONFIGS": json.dumps({"shop.test": config or {}})})
    return cls.from_crawler(Crawler(cls, settings), domain="https://shop.test")


def response_json(data, request):
    return html(json.dumps(data), request.url, request)


def magento_product(sku="A"):
    return {"sku": sku, "name": "Useful hammer", "description": {"html": "Durable"},
            "small_image": {"url": "https://shop.test/image.jpg"},
            "price_range": {"minimum_price": {"final_price": {"value": 150, "currency": "AUD"}}}}


def magento_page(items, total, page=1):
    return {"data": {"products": {"items": items, "total_count": total,
                                  "page_info": {"current_page": page, "total_pages": 2}}}}


def shopify_product():
    return {"id": 42, "title": "Useful hammer", "body_html": "Durable", "product_type": "Tools",
            "images": [{"src": "https://shop.test/default.jpg"}, {"src": "https://shop.test/blue.jpg", "variant_ids": [102]}],
            "options": [{"name": "Color"}],
            "variants": [{"id": 101, "sku": "REUSED", "price": "150", "option1": "Red"},
                         {"id": 102, "sku": "REUSED", "price": "200", "option1": "Blue"}]}


class PlatformTests(unittest.TestCase):
    def test_independent_adapters_parse_schema_products(self):
        for engine, cls in ENGINES.items():
            with self.subTest(engine=engine):
                spider = spider_for(cls)
                item = list(spider.parse_product_detail(html(product_html())))[0]
                self.assertEqual(item["Regular price"], 150)
                self.assertEqual(item["货币"], "AUD")
                self.assertEqual(spider.name, SPIDERS[engine])

    def test_wix_product_page_and_custom_product_sitemap_slug(self):
        spider = spider_for(WixCrawlSpider)
        self.assertTrue(spider._is_product_detail_url("https://shop.test/product-page/hammer"))
        self.assertTrue(spider._is_product_detail_url("https://shop.test/custom/hammer", True))
        self.assertFalse(spider._is_product_detail_url("https://other.test/product-page/hammer", True))
        self.assertFalse(spider._is_product_detail_url("https://shop.test/about"))

    def test_magento_page_currency_and_pagination(self):
        spider = spider_for(MagentoCrawlSpider)
        outputs = list(spider.parse_catalog(response_json(magento_page([magento_product()], 2), spider.catalog_request(1))))
        self.assertEqual(outputs[0]["SKU"], "A")
        self.assertEqual(outputs[0]["货币"], "AUD")
        self.assertEqual(outputs[1].meta["catalog_page"], 2)
        last = list(spider.parse_catalog(response_json(magento_page([magento_product("B")], 2, 2), outputs[1])))
        self.assertEqual(len(last), 1)
        self.assertEqual(spider.metrics.counts["failed"], 0)

    def test_magento_api_error_falls_back_only_before_partial_catalog(self):
        spider = spider_for(MagentoCrawlSpider)
        output = list(spider.parse_catalog(response_json({"errors": [{"message": "disabled"}]}, spider.catalog_request(1))))
        self.assertEqual(output[0].callback, spider.parse_sitemap)
        spider.api_seen.add("A")
        self.assertEqual(list(spider.parse_catalog(response_json({"errors": []}, spider.catalog_request(2)))), [])
        self.assertEqual(spider.metrics.failed_reasons["api_schema"], 1)

    def test_magento_empty_catalog_and_repeated_sku(self):
        spider = spider_for(MagentoCrawlSpider)
        self.assertEqual(list(spider.parse_catalog(response_json(magento_page([], 0), spider.catalog_request(1)))), [])
        self.assertTrue(spider.metrics.confirmed_empty)
        spider = spider_for(MagentoCrawlSpider)
        list(spider.parse_catalog(response_json(magento_page([magento_product(), magento_product()], 2), spider.catalog_request(1))))
        self.assertGreater(spider.metrics.counts["failed"], 0)

    def test_ecwid_public_api_profile_and_catalog(self):
        spider = spider_for(EcwidCrawlSpider, {"store_id": "123", "public_token": "public_test"})
        profile = spider.api_request("profile", spider.parse_profile)
        catalog = list(spider.parse_profile(response_json({"formatsAndUnits": {"currency": "AUD"}}, profile)))[0]
        self.assertEqual(catalog.headers.get("Authorization"), b"Bearer public_test")
        output = list(spider.parse_catalog(response_json({"items": [{"id": 1, "name": "Useful hammer", "price": 150}],
                          "offset": 0, "count": 1, "total": 2}, catalog)))
        self.assertEqual(output[0]["货币"], "AUD")
        self.assertEqual(output[1].meta["offset"], 1)

    def test_ecwid_authorization_is_scoped_to_its_exact_store(self):
        spider = spider_for(EcwidCrawlSpider, {"store_id": "123", "public_token": "public_test"})
        self.assertTrue(spider.request_allowed("https://app.ecwid.com/api/v3/123/products?offset=0"))
        for url in ("https://app.ecwid.com/api/v3/999/products", "http://app.ecwid.com/api/v3/123/products",
                    "https://app.ecwid.com/api/v3/123/orders", "https://evil.test/api/v3/123/products"):
            self.assertFalse(spider.request_allowed(url))
        with self.assertRaises(ValueError):
            spider_for(EcwidCrawlSpider, {"store_id": "123", "public_token": "secret_not_allowed"})

    def test_shopline_ajax_minor_units_and_handle_check(self):
        spider = spider_for(ShoplineCrawlSpider)
        page = html('<main data-currency-code="AUD"><h1>Useful hammer</h1></main>', "https://shop.test/products/hammer")
        request = list(spider.parse_product_detail(page))[0]
        self.assertIn("/api/product/products.json?handle=hammer", request.url)
        product = {"id": "1", "handle": "hammer", "title": "Useful hammer", "price": 15000}
        item = list(spider.parse_ajax(response_json({"products": [product]}, request)))[0]
        self.assertEqual(item["Regular price"], 150)
        self.assertEqual(item["货币"], "AUD")
        request.meta["handle"] = "different"
        self.assertEqual(list(spider.parse_ajax(response_json({"products": [product]}, request))), [])
        self.assertGreater(spider.metrics.counts["failed"], 0)

    def test_shopline_unknown_currency_not_guessed_as_cents(self):
        spider = spider_for(ShoplineCrawlSpider)
        request = Request("https://shop.test/api/product/products.json", meta={"handle": "x", "currency": ""})
        output = list(spider.parse_ajax(response_json({"products": [{"handle": "x", "price": 999}]}, request)))
        self.assertEqual(output, [])
        self.assertEqual(spider.metrics.failed_reasons["shopline_ajax"], 1)


class ShopifyVariantTests(unittest.TestCase):
    def test_all_variants_have_stable_ids_independent_prices_images_and_attributes(self):
        spider = spider_for(ShopifyCrawlFastSpider)
        spider.shop_currency = "AUD"
        item_a, item_b = list(spider.parse_products(response_json({"products": [shopify_product()]}, Request("https://shop.test/products.json"))))
        self.assertEqual(item_a["SKU"], "SHOPIFY-42-101")
        self.assertEqual(item_b["SKU"], "SHOPIFY-42-102")
        self.assertEqual((item_a["Regular price"], item_b["Regular price"]), (150, 200))
        self.assertTrue(item_b["Images"].endswith("blue.jpg"))
        self.assertIn("Color^Blue", item_b["cf_opingts"])
        self.assertNotIn("Red", item_b["cf_opingts"])
        self.assertEqual(spider.metrics.counts["fetched"], 2)

    def test_reordering_or_category_changes_do_not_change_identity(self):
        product = shopify_product()
        left = list(spider_for(ShopifyCrawlFastSpider).emit_variants(product, product["variants"]))
        other = spider_for(ShopifyCrawlFastSpider)
        other.custom_category = "Different"
        right = list(other.emit_variants(product, list(reversed(product["variants"]))))
        self.assertEqual({i["SKU"] for i in left}, {i["SKU"] for i in right})

    def test_bad_variant_does_not_prevent_other_variant_from_being_saved(self):
        spider = spider_for(ShopifyCrawlFastSpider)
        product = shopify_product()
        product["variants"][0]["price"] = "broken"
        items = list(spider.emit_variants(product, product["variants"]))
        self.assertEqual(len(items), 1)
        self.assertEqual(spider.metrics.failed_reasons["variant_parse"], 1)

    def test_public_variant_limit_is_reported_not_silently_accepted(self):
        spider = spider_for(ShopifyCrawlFastSpider)
        product = shopify_product()
        product["variants_count"] = 300
        list(spider.parse_products(response_json({"products": [product]}, Request("https://shop.test/products.json"))))
        self.assertEqual(spider.metrics.failed_reasons["variant_limit"], 1)

    def test_storefront_cursor_collects_more_variants_and_checks_repeated_cursor(self):
        spider = spider_for(ShopifyCrawlFastSpider, {"storefront_token": "public_storefront_token"})
        product = shopify_product()
        request = list(spider.parse_products(response_json({"products": [product]}, Request("https://shop.test/products.json"))))[0]
        self.assertEqual(request.method, "POST")
        node = {"id": "gid://shopify/ProductVariant/101", "sku": "X", "selectedOptions": [],
                "price": {"amount": "150", "currencyCode": "AUD"}}
        payload = {"data": {"product": {"variants": {"nodes": [node], "pageInfo": {"hasNextPage": True, "endCursor": "next"}}}}}
        output = list(spider.parse_variant_page(response_json(payload, request)))
        self.assertEqual(output[0]["货币"], "AUD")
        self.assertEqual(json.loads(output[1].body)["variables"]["after"], "next")
        node["id"] = "gid://shopify/ProductVariant/102"
        list(spider.parse_variant_page(response_json(payload, output[1])))
        self.assertEqual(spider.metrics.failed_reasons["variant_api"], 1)


class RedisAndDeadlineTests(unittest.TestCase):
    def test_redis_startup_failure_and_explicit_disable_keep_mysql_working(self):
        for enabled in (True, False):
            database = phase1.MemoryDatabase()
            spider = phase1.spider_instance()
            with patch("ecommerce_spider.pipelines.pymysql.connect", database.connect), \
                    patch("ecommerce_spider.pipelines.redis.Redis", side_effect=ConnectionError("offline")) as redis_client, \
                    contextlib.redirect_stdout(io.StringIO()):
                pipeline = MySQLRedisPipeline({}, {}, 1, redis_enabled=enabled)
                pipeline.open_spider(spider)
                pipeline.process_item(phase1.product(), spider)
                pipeline.close_spider(spider)
                self.assertEqual(len(database.rows), 1)
                self.assertEqual(pipeline.metrics.counts["failed"], 0)
                self.assertEqual(redis_client.call_count, int(enabled))

    def test_pause_freezes_remaining_time_and_handles_repeated_transitions(self):
        now = [10.0]
        budget = ActiveDeadline(5, clock=lambda: now[0])
        now[0] = 12
        budget.pause()
        budget.pause()
        now[0] = 1000
        self.assertEqual(budget.remaining(), 3)
        self.assertFalse(budget.expired())
        budget.resume()
        budget.resume()
        now[0] += 2
        self.assertFalse(budget.expired())
        now[0] += 1
        self.assertTrue(budget.expired())


class Phase3ConsumerTests(unittest.IsolatedAsyncioTestCase):
    async def test_explicit_platform_dispatch_rejects_unsupported_engines(self):
        consumer = ProductConsumer.__new__(ProductConsumer)
        consumer._exec_scrapy = AsyncMock(return_value={})
        for engine, name in SPIDERS.items():
            await consumer._run_platform("shop.test", "Tools", engine)
            self.assertEqual(consumer._exec_scrapy.call_args.args[0][2], name)
        with self.assertRaises(ValueError):
            await consumer._run_platform("shop.test", "Tools", "wix_typo")
        with self.assertRaises(ValueError):
            await consumer._run_platform("shop.test", "Tools", "opencart")

    def consumer(self):
        consumer = ProductConsumer.__new__(ProductConsumer)
        consumer.repo = AsyncMock()
        consumer.repo.get_task_status.return_value = "RUNNING"
        consumer.scrapy_project = phase1.ROOT
        return consumer

    async def test_real_pause_longer_than_budget_resumes_without_timeout(self):
        consumer = self.consumer()
        start = time.monotonic()

        async def status(task_id):
            return "PAUSED" if time.monotonic() - start < 3.4 else "RUNNING"

        consumer.repo.get_task_status.side_effect = status
        metrics = CrawlMetrics()
        metrics.pipeline_opened = metrics.pipeline_closed = metrics.confirmed_empty = True
        line = RESULT_PREFIX + json.dumps(metrics.finish("finished"))
        command = [sys.executable, "-u", "-c", f"import time;time.sleep(1.4);print({line!r},flush=True)"]
        with patch("consumers.product_consumer.PRODUCT_CRAWL_TIMEOUT_SECONDS", 2.5):
            result = await consumer._exec_scrapy(command, "pause-test")
        self.assertEqual(result["outcome"], "success")
        self.assertGreater(time.monotonic() - start, 3.4)

    async def test_timeout_is_bounded_even_after_stdout_closed(self):
        consumer = self.consumer()
        command = [sys.executable, "-u", "-c", "import os,time;os.close(1);os.close(2);time.sleep(30)"]
        with patch("consumers.product_consumer.PRODUCT_CRAWL_TIMEOUT_SECONDS", 0.2):
            with self.assertRaises(TimeoutError):
                await asyncio.wait_for(consumer._exec_scrapy(command, "timeout-test"), 8)

    async def test_cancel_while_paused_resumes_and_stops_child(self):
        consumer = self.consumer()
        consumer.repo.get_task_status.side_effect = ["PAUSED", "CANCELLED"]
        command = [sys.executable, "-u", "-c", "import time;time.sleep(30)"]
        with self.assertRaises(TaskCancelledError):
            await asyncio.wait_for(consumer._exec_scrapy(command, "cancel-test"), 8)

    async def test_real_scrapy_platform_lifecycles(self):
        for engine in ENGINES:
            for scenario in ("success", "empty", "blocked"):
                with self.subTest(engine=engine, scenario=scenario):
                    consumer = self.consumer()
                    command = [sys.executable, "-u", str(Path(__file__).resolve()), "crawl-fixture", engine, scenario]
                    # An empty sitemap alone cannot confirm that a Wix/SHOPLINE catalog is empty.
                    success = scenario == "success" or (scenario == "empty" and engine in {"magento", "ecwid"})
                    if success:
                        result = await consumer._exec_scrapy(command, "fixture")
                        self.assertEqual(result["persisted"], 1 if scenario == "success" else 0)
                    else:
                        with self.assertRaises(RuntimeError):
                            await consumer._exec_scrapy(command, "fixture")
                    self.assertEqual(consumer.crawl_metrics["persisted"], 1 if scenario == "success" else 0)


class PlatformFixture:
    scenario = "success"

    def process_request(self, request, spider):
        if self.scenario == "blocked":
            return html("", request.url, request, 403)
        if "/graphql?" in request.url:
            items = [] if self.scenario == "empty" else [magento_product()]
            return response_json(magento_page(items, len(items)), request)
        if request.url.endswith("/profile"):
            return response_json({"formatsAndUnits": {"currency": "AUD"}}, request)
        if "app.ecwid.com" in request.url:
            items = [] if self.scenario == "empty" else [{"id": 1, "name": "Useful hammer", "price": 150, "imageUrl": "https://shop.test/a.jpg"}]
            return response_json({"items": items, "offset": 0, "count": len(items), "total": len(items)}, request)
        if "sitemap" in request.url:
            path = "/product-page/hammer" if spider.engine == "wix" else "/products/hammer"
            body = b"<urlset/>" if self.scenario == "empty" else f"<urlset><url><loc>https://shop.test{path}</loc></url></urlset>".encode()
            return XmlResponse(request.url, body=body, request=request)
        if request.url.endswith("shop.test/"):
            return html("<html><title>Empty storefront</title></html>", request.url, request)
        return html(product_html(), request.url, request)


def run_fixture(engine, scenario):
    PlatformFixture.scenario = scenario
    settings = Settings()
    settings.setmodule("ecommerce_spider.settings")
    middleware = settings.getdict("DOWNLOADER_MIDDLEWARES")
    middleware[PlatformFixture] = 800
    settings.setdict({"LOG_ENABLED": False, "ROBOTSTXT_OBEY": False, "COOKIES_ENABLED": False,
                      "DOWNLOAD_DELAY": 0, "AUTOTHROTTLE_ENABLED": False, "RETRY_ENABLED": False,
                      "CONCURRENT_REQUESTS": 1, "DB_BATCH_SIZE": 1, "PRODUCT_REDIS_ENABLED": False,
                      "DOWNLOADER_MIDDLEWARES": middleware,
                      "PRODUCT_PLATFORM_CONFIGS": {"shop.test": {"store_id": "123", "public_token": "public_test"}}}, priority="cmdline")
    database = phase1.MemoryDatabase()
    with patch("ecommerce_spider.pipelines.pymysql.connect", database.connect):
        process = CrawlerProcess(settings)
        process.crawl(ENGINES[engine], domain="https://shop.test")
        process.start()


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "crawl-fixture":
        run_fixture(sys.argv[2], sys.argv[3])
    else:
        unittest.main()
