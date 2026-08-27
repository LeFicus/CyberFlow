"""Stage-two options, currency, request policy and real Scrapy lifecycle checks."""

import gzip
import json
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import test_product_crawl_phase1 as phase1
from scrapy import Request
from scrapy.crawler import Crawler, CrawlerProcess
from scrapy.exceptions import DropItem, IgnoreRequest
from scrapy.http import HtmlResponse, Response, XmlResponse
from scrapy.settings import Settings
from twisted.internet.error import TimeoutError as DownloadTimeout

from consumers.product_consumer import ProductConsumer
from ecommerce_spider.check_runtime import runtime_report
from ecommerce_spider.crawl_options import CrawlOptions, resolve_currency
from ecommerce_spider.normalization import UnknownCurrencyError, currency_to_usd
from ecommerce_spider.request_policy import (
    BigCommerceRequestsFallbackMiddleware, ProductRequestPolicyMiddleware,
    ProductTLSContextFactory, configured_proxy, is_verification_page, same_store,
)
from ecommerce_spider.spiders.bigcommerce_crawl import BigCommerceCrawlSpider


def html(body, url="https://shop.test/hammer/", request=None, status=200):
    return HtmlResponse(url, body=body.encode(), encoding="utf-8", status=status,
                        request=request or Request(url))


def product_html(currency="AUD", price=150, image=True):
    data = {"@type": "Product", "name": "Useful hammer", "sku": "A",
            "offers": {"price": price, "priceCurrency": currency}}
    if image:
        data["image"] = "/hammer.jpg"
    return '<script type="application/ld+json">' + json.dumps(data) + '</script>'


CHALLENGE = '<title>Just a moment...</title><script src="/cdn-cgi/challenge-platform/test"></script>'


class OptionsAndCurrencyTests(unittest.TestCase):
    def test_options_defaults_and_explicit_false_null(self):
        self.assertIsNone(CrawlOptions().max_product_price_usd)
        options = CrawlOptions.from_json('{"require_image":false,"max_product_price_usd":null}')
        self.assertFalse(options.require_image)
        self.assertIsNone(options.max_product_price_usd)

    def test_invalid_options_rejected(self):
        for options in ([], {"typo": 1}, {"require_image": "false"}, {"require_description": None},
                        {"max_product_price_usd": True}, {"max_product_price_usd": 0},
                        {"max_product_price_usd": float("nan")}, {"currency": "$"}, {"currency": False}):
            with self.subTest(options=options), self.assertRaises(ValueError):
                CrawlOptions.from_json(options)

    def test_currency_evidence_order_and_no_usd_assumption(self):
        spider = phase1.spider_instance(BigCommerceCrawlSpider)
        self.assertEqual(resolve_currency(spider), ("", "unknown"))
        spider.selectors["currency"] = "USD"
        response = html('<main data-currency-code="AUD"></main>')
        self.assertEqual(resolve_currency(spider, response), ("AUD", "page"))
        self.assertEqual(resolve_currency(spider, response, bcdata={"currency": "CAD"}), ("CAD", "bcdata"))
        self.assertEqual(resolve_currency(spider, response, preferred="NZD"), ("NZD", "price_payload"))
        spider.crawl_options = CrawlOptions(currency="GBP")
        self.assertEqual(resolve_currency(spider), ("GBP", "task_config"))

    def test_domain_config_and_active_page_currency(self):
        spider = phase1.spider_instance(BigCommerceCrawlSpider)
        spider.crawler.settings.set("PRODUCT_DOMAIN_CURRENCIES", '{"shop.test":"AUD"}')
        self.assertEqual(resolve_currency(spider), ("AUD", "domain_config"))
        for markup in ('<meta itemprop="priceCurrency" content="USD">',
                       '<script>var data={"active_currency_code":"USD"}</script>',
                       '<footer>All prices are in USD</footer>'):
            self.assertEqual(resolve_currency(spider, html(markup)), ("USD", "page"))

    def test_price_and_currency_are_from_same_offer_or_tax_branch(self):
        spider = phase1.spider_instance(BigCommerceCrawlSpider)
        product = {"offers": [{"priceCurrency": "USD"}, {"price": 150, "priceCurrency": "AUD"}]}
        attributes = {"price": {"without_tax": {"currency": "USD"},
                                "with_tax": {"value": 150, "currency": "AUD"}}}
        self.assertEqual((spider._jsonld_price(product), spider._jsonld_currency(product)), (150, "AUD"))
        self.assertEqual((spider._bcdata_price(attributes), spider._bcdata_currency(attributes)), (150, "AUD"))

    def test_jsonld_and_bcdata_prices_and_currencies(self):
        spider = phase1.spider_instance(BigCommerceCrawlSpider)
        response = html(product_html("USD") + '<script>var BCData={"product_attributes":'
                        '{"price":{"without_tax":{"value":200,"currency":"AUD"}}}};</script>')
        item = list(spider.parse_product_detail(response))[0]
        self.assertEqual((item["Regular price"], item["货币"]), ("200.00", "AUD"))

    def test_unknown_or_unpriced_page_is_not_filled_with_recommendation_price(self):
        spider = phase1.spider_instance(BigCommerceCrawlSpider)
        response = html('<h1 class="productView-title">Unpriced hammer</h1>'
                        '<div class="product-price-reviews">3 reviews</div>'
                        '<div class="price-section"><span data-product-price-with-tax>$99.00</span></div>')
        self.assertEqual(list(spider.parse_product_detail(response)), [])
        self.assertEqual(spider.metrics.failed_reasons["product_parse"], 1)

    def test_image_requirement_is_left_to_pipeline(self):
        spider = phase1.spider_instance(BigCommerceCrawlSpider)
        item = list(spider.parse_product_detail(html(product_html(image=False))))[0]
        self.assertEqual(item["Images"], "")

    def test_unknown_currency_does_not_convert_at_one_to_one(self):
        for currency in ("", None, "ZZZ"):
            with self.assertRaises(UnknownCurrencyError):
                currency_to_usd(150, currency)
        self.assertEqual(currency_to_usd(150, "AUD"), 100.5)


class Phase2PipelineTests(unittest.TestCase):
    setUp = phase1.PipelineTests.setUp

    def test_filter_applied_after_aud_conversion(self):
        self.pipeline.options = CrawlOptions(max_product_price_usd=130)
        item = phase1.product()
        item.update({"Regular price": 150, "货币": "AUD"})
        self.pipeline.process_item(item, self.spider)
        self.pipeline.close_spider(self.spider)
        self.assertEqual(self.database.rows[("shop.test", "A")][3], 100.5)

    def test_optional_image_description_and_unlimited_price(self):
        self.pipeline.options = CrawlOptions(require_image=False)
        item = phase1.product()
        item.pop("Images")
        item.pop("Description")
        item["Regular price"] = 999
        self.pipeline.process_item(item, self.spider)
        self.pipeline.close_spider(self.spider)
        self.assertEqual(self.pipeline.metrics.counts["persisted"], 1)

    def test_required_fields_have_distinct_filter_reasons(self):
        self.pipeline.options = CrawlOptions(require_description=True)
        for field, reason in (("Description", "missing_description"), ("Images", "missing_image")):
            item = phase1.product()
            item[field] = ""
            with self.assertRaises(DropItem):
                self.pipeline.process_item(item, self.spider)
            self.assertEqual(self.pipeline.metrics.filtered_reasons[reason], 1)

    def test_unknown_currency_is_a_task_failure_not_silent_filter(self):
        item = phase1.product()
        item["货币"] = ""
        with self.assertRaises(DropItem):
            self.pipeline.process_item(item, self.spider)
        self.pipeline.close_spider(self.spider)
        self.assertEqual(self.pipeline.metrics.finish("finished")["outcome"], "failed")
        self.assertEqual(self.pipeline.metrics.counts["persisted"], 0)


class DiscoveryTests(unittest.TestCase):
    def test_empty_bigcommerce_sitemap_also_checks_storefront(self):
        spider = phase1.spider_instance(BigCommerceCrawlSpider)
        request = spider._sitemap_request(2)
        response = XmlResponse(request.url, body=b'<urlset/>', request=request)
        requests = list(spider.parse_sitemap(response))
        self.assertEqual(requests[0].callback, spider.parse_navigation)
        self.assertFalse(spider.metrics.confirmed_empty)

    def test_failed_sitemap_starts_navigation_once_and_discovers_categories(self):
        spider = phase1.spider_instance(BigCommerceCrawlSpider)
        requests = list(spider.discovery_failed("test", "blocked"))
        self.assertEqual(list(spider.discovery_failed("test", "blocked")), [])
        links = list(spider.parse_navigation(html('<nav><a href="/tools/">Tools</a>'
                     '<a href="https://other.test/tools/">Offsite</a><a href="/cart.php">Cart</a></nav>',
                     url="https://shop.test/", request=requests[0])))
        self.assertEqual([request.url for request in links], ["https://shop.test/tools/"])
        products = list(spider.parse_navigation(html('<h3 class="card-title"><a href="/hammer/">Hammer</a></h3>',
                        request=links[0])))
        self.assertEqual(products[0].callback, spider.parse_product_detail)

    def test_navigation_limit_is_explicit_incomplete_failure(self):
        spider = phase1.spider_instance(BigCommerceCrawlSpider)
        spider.crawler.settings.set("PRODUCT_DISCOVERY_MAX_PAGES", 1)
        request = list(spider.discovery_failed("test", "blocked"))[0]
        self.assertEqual(list(spider.parse_navigation(html('<nav><a href="/tools/">Tools</a></nav>', request=request))), [])
        self.assertEqual(spider.metrics.failed_reasons["discovery_limit"], 1)

    def test_gzip_sitemap_and_www_alias(self):
        spider = phase1.spider_instance(BigCommerceCrawlSpider)
        request = Request("https://shop.test/product-sitemap.xml", meta={"product_sitemap": True})
        response = Response(request.url, request=request, body=gzip.compress(
            b'<urlset><url><loc>https://www.shop.test/hammer/</loc></url></urlset>'))
        self.assertEqual(list(spider.parse_sitemap(response))[0].url, "https://www.shop.test/hammer/")


class RequestPolicyTests(unittest.TestCase):
    def setUp(self):
        self.crawler = SimpleNamespace(settings=Settings(), signals=MagicMock(), stats=MagicMock())
        self.spider = SimpleNamespace(name="bigcommerce_crawl", domain="https://shop.test", logger=MagicMock())

    def test_same_origin_rejects_offsite_and_downgrade_but_allows_www(self):
        self.assertTrue(same_store("https://www.shop.test/a", self.spider.domain))
        for url in ("http://shop.test/a", "https://shop.test.evil.test/", "https://shop.test:9000/", "https://user@shop.test/"):
            self.assertFalse(same_store(url, self.spider.domain))
        policy = ProductRequestPolicyMiddleware(self.crawler)
        with self.assertRaises(IgnoreRequest):
            policy.process_request(Request("https://other.test/"), self.spider)

    def test_http_200_challenge_is_error_but_normal_captcha_widget_is_not(self):
        policy = ProductRequestPolicyMiddleware(self.crawler)
        request = Request("https://shop.test/")
        response = policy.process_response(request, html(CHALLENGE, request=request), self.spider)
        self.assertEqual(response.status, 403)
        self.assertIn("verification-required", response.flags)
        self.assertTrue(request.meta["dont_retry"])
        self.assertFalse(is_verification_page(html('<title>Shop</title><div class="g-recaptcha"></div>')))

    def test_proxy_setting_and_invalid_config(self):
        self.crawler.settings.set("PRODUCT_CRAWL_PROXY_URL", "http://user:secret@proxy.test:8080")
        policy = ProductRequestPolicyMiddleware(self.crawler)
        request = Request("https://shop.test/")
        policy.process_request(request, self.spider)
        self.assertEqual(request.meta["proxy"], "http://user:secret@proxy.test:8080")
        self.assertNotIn(b"Proxy-Authorization", request.headers)
        with self.assertRaises(ValueError):
            configured_proxy({"PRODUCT_CRAWL_PROXY_URL": "socks5://proxy.test:9"})

    def test_fallback_status_challenge_and_network_exception_are_bounded(self):
        middleware = BigCommerceRequestsFallbackMiddleware(self.crawler)
        with patch("ecommerce_spider.request_policy.deferToThread", return_value=MagicMock()) as worker:
            for status in (403, 429, 503, 200):
                request = Request("https://shop.test/")
                response = html(CHALLENGE if status == 200 else "", status=status, request=request)
                middleware.process_response(request, response, self.spider)
                count = worker.call_count
                self.assertTrue(request.meta["requests_fallback_attempted"])
                self.assertIs(middleware.process_response(request, response, self.spider), response)
                self.assertEqual(worker.call_count, count)
            request = Request("https://shop.test/")
            middleware.process_exception(request, DownloadTimeout(), self.spider)
            self.assertEqual(worker.call_count, 5)

    def test_transport_uses_tls_proxy_and_removes_decoded_content_headers(self):
        self.crawler.settings.set("PRODUCT_CRAWL_PROXY_URL", "http://user:secret@proxy.test:8080")
        middleware = BigCommerceRequestsFallbackMiddleware(self.crawler)
        result = MagicMock()
        result.__enter__.return_value = result
        result.status_code = 200
        result.headers = {"Content-Encoding": "gzip", "Content-Length": "5", "Content-Type": "text/html"}
        result.iter_content.return_value = [b"<html>hello</html>"]
        session = MagicMock()
        session.get.return_value = result
        with patch.object(middleware, "_session", return_value=session):
            response = middleware._download(Request("https://shop.test/", headers={"Proxy-Authorization": "secret"}), self.spider)
        self.assertEqual(response.status, 200)
        self.assertNotIn("Content-Encoding", response.headers)
        kwargs = session.get.call_args.kwargs
        self.assertIs(kwargs["verify"], True)
        self.assertFalse(kwargs["allow_redirects"])
        self.assertNotIn("Proxy-Authorization", kwargs["headers"])
        self.assertEqual(kwargs["proxies"]["https"], "http://user:secret@proxy.test:8080")

    def test_transport_does_not_follow_offsite_redirect(self):
        middleware = BigCommerceRequestsFallbackMiddleware(self.crawler)
        result = MagicMock()
        result.__enter__.return_value = result
        result.status_code = 302
        result.headers = {"Location": "https://other.test/"}
        session = MagicMock()
        session.get.return_value = result
        with patch.object(middleware, "_session", return_value=session):
            response = middleware._download(Request("https://shop.test/"), self.spider)
        self.assertEqual(response.status, 503)
        self.assertEqual(session.get.call_count, 1)

    def test_runtime_requires_both_middlewares(self):
        self.assertTrue(runtime_report()["protection_ready"])
        self.assertFalse(runtime_report(Settings())["protection_ready"])

    def test_tls_verifies_hostname_and_accepts_pem_bundle(self):
        import certifi

        default = ProductTLSContextFactory.from_crawler(self.crawler)
        self.assertIsNotNone(default.creatorForNetloc(b"shop.test", 443))
        self.crawler.settings.set("PRODUCT_CRAWL_CA_BUNDLE", certifi.where())
        custom = ProductTLSContextFactory.from_crawler(self.crawler)
        self.assertIsNotNone(custom.trust_root)
        self.assertIsNotNone(custom.creatorForNetloc(b"shop.test", 443))


class Phase2ConsumerTests(unittest.IsolatedAsyncioTestCase):
    async def test_task_options_override_template_including_null_and_false(self):
        consumer = ProductConsumer.__new__(ProductConsumer)
        consumer.repo = AsyncMock()
        consumer.repo.get_site_config.return_value = {"uses_default_template": True, "templates": [{
            "currency": "USD", "extra_selectors": '{"crawl_options":{"max_product_price_usd":130,"require_image":true}}',
        }]}
        consumer._publish_result = MagicMock()
        consumer._run_platform = AsyncMock(return_value={"persisted": 0})
        await consumer.process({"task_id": "t", "payload": {"site_config_id": 1, "domain": "shop.test", "type": "bigcommerce",
                               "crawl_options": {"max_product_price_usd": None, "require_image": False}}})
        args = consumer._run_platform.call_args.args
        self.assertNotIn("currency", args[4])
        self.assertEqual(args[6], {"max_product_price_usd": None, "require_image": False})

    async def test_all_engines_receive_options(self):
        consumer = ProductConsumer.__new__(ProductConsumer)
        consumer._exec_scrapy = AsyncMock(return_value={})
        options = {"max_product_price_usd": None, "require_image": False}
        for engine in ("bigcommerce", "shopify", "woocommerce"):
            await consumer._run_platform("shop.test", "Tools", engine, crawl_options=options)
            command = consumer._exec_scrapy.call_args.args[0]
            value = next(arg.split("=", 1)[1] for arg in command if arg.startswith("crawl_options_json="))
            self.assertEqual(json.loads(value), options)

    async def test_scrapy_navigation_and_failure_paths(self):
        for scenario, outcome, persisted in (
            ("navigation", "success", 1), ("navigation-blocked", "failed", 0),
            ("verification", "failed", 0), ("unknown-currency", "failed", 0),
            ("optional-image", "success", 1), ("required-image", "success", 0),
            ("price-filter", "success", 0), ("aud-conversion", "success", 1),
        ):
            with self.subTest(scenario=scenario):
                consumer = ProductConsumer.__new__(ProductConsumer)
                consumer.repo = AsyncMock()
                consumer.repo.get_task_status.return_value = "RUNNING"
                consumer.scrapy_project = phase1.ROOT
                consumer.crawl_metrics = {}
                command = [sys.executable, "-u", str(Path(__file__).resolve()), "crawl-fixture", scenario]
                if outcome == "failed":
                    with self.assertRaises(RuntimeError):
                        await consumer._exec_scrapy(command, "fixture")
                else:
                    result = await consumer._exec_scrapy(command, "fixture")
                    self.assertEqual(result["outcome"], "success")
                    self.assertEqual(result["discovery_sources"]["navigation"], 1)
                self.assertEqual(consumer.crawl_metrics["persisted"], persisted)


class NavigationFixture:
    scenario = "navigation"

    def process_request(self, request, spider):
        if "sitemap" in request.url or self.scenario == "navigation-blocked":
            return html("", request.url, request, 403)
        if request.url.endswith("shop.test/"):
            return html('<nav><a href="/tools/">Tools</a></nav>', request.url, request)
        if request.url.endswith("/tools/"):
            return html('<h3 class="card-title"><a href="/hammer/">Hammer</a></h3>', request.url, request)
        if self.scenario == "verification":
            return html(CHALLENGE, request.url, request)
        return html(product_html(currency="" if self.scenario == "unknown-currency" else "AUD",
                                 image=self.scenario not in {"optional-image", "required-image"}), request.url, request)


def run_fixture(scenario):
    NavigationFixture.scenario = scenario
    settings = Settings()
    settings.setmodule("ecommerce_spider.settings")
    middleware = settings.getdict("DOWNLOADER_MIDDLEWARES")
    middleware[NavigationFixture] = 800
    settings.setdict({"LOG_ENABLED": False, "ROBOTSTXT_OBEY": False, "COOKIES_ENABLED": False,
                      "DOWNLOAD_DELAY": 0, "AUTOTHROTTLE_ENABLED": False, "RETRY_ENABLED": False,
                      "CONCURRENT_REQUESTS": 1, "DB_BATCH_SIZE": 1,
                      "DOWNLOADER_MIDDLEWARES": middleware}, priority="cmdline")
    options = {"require_image": scenario != "optional-image"}
    if scenario in {"price-filter", "aud-conversion"}:
        options["max_product_price_usd"] = 90 if scenario == "price-filter" else 130
    database = phase1.MemoryDatabase()
    with patch("ecommerce_spider.pipelines.pymysql.connect", database.connect), \
            patch("ecommerce_spider.pipelines.redis.Redis", return_value=MagicMock()), \
            patch.object(BigCommerceRequestsFallbackMiddleware, "_download",
                         lambda self, request, spider: NavigationFixture().process_request(request, spider)):
        process = CrawlerProcess(settings)
        process.crawl(BigCommerceCrawlSpider, domain="https://shop.test", crawl_options_json=json.dumps(options))
        process.start()


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "crawl-fixture":
        run_fixture(sys.argv[2])
    else:
        unittest.main()
