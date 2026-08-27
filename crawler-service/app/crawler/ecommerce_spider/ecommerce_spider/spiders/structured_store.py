"""Shared discovery/Schema.org primitives for independent storefront adapters.

This base has no WooCommerce selectors. Each adapter owns its URL patterns,
page selectors and optional API. Only positively identified products are emitted.
"""

import hashlib
import json
import math
import re
from urllib.parse import urlparse, urlunparse, parse_qs, urlencode

import scrapy

from ecommerce_spider.crawl_options import resolve_currency
from ecommerce_spider.request_policy import same_store
from ecommerce_spider.spiders.platform_crawl import PlatformCrawlSpider


class StructuredStoreSpider(PlatformCrawlSpider):
    name = None
    engine = None
    SUPPORTED = {"magento", "wix", "ecwid", "shopline"}
    SITEMAP_PATHS = ("/sitemap.xml", "/sitemap_index.xml")
    PRODUCT_URL = re.compile(r"/products?/[^/]+")
    NAVIGATION_XPATH = "//nav//a/@href | //a[@rel='next']/@href"
    DEFAULT_SELECTORS = {
        "title": "//*[@itemprop='name']/text() | //meta[@property='og:title']/@content",
        "price": "//*[@itemprop='price']/@content | //meta[@property='product:price:amount']/@content",
        "price_regex": r"[\d.,]+",
        "sku": "//*[@itemprop='sku']/@content | //*[@itemprop='sku']/text()",
        "description": "//*[@itemprop='description']//text() | //meta[@property='og:description']/@content",
        "images": "//meta[@property='og:image']/@content",
        "breadcrumb_links": "//*[@itemprop='breadcrumb']//a/text()",
        "breadcrumb_last": "//*[@itemprop='breadcrumb']/*[last()]/text()",
        "site_map": PlatformCrawlSpider.SITEMAP_INDEX_XPATH,
        "currency": "",
    }

    def __init__(self, *args, **kwargs):
        kwargs.pop("platform", None)
        super().__init__(*args, platform=self.engine, **kwargs)
        self.navigation_started = False
        self.navigation_seen = set()
        self.api_seen = set()
        self.discovery_limited = False
        self.platform_config = {}

    @classmethod
    def from_crawler(cls, crawler, *args, **kwargs):
        spider = super().from_crawler(crawler, *args, **kwargs)
        values = crawler.settings.get("PRODUCT_PLATFORM_CONFIGS") or {}
        values = json.loads(values) if isinstance(values, str) else values
        if not isinstance(values, dict):
            raise ValueError("PRODUCT_PLATFORM_CONFIGS must be a domain/config object")
        config = values.get(urlparse(spider.domain).hostname, {})
        if not isinstance(config, dict):
            raise ValueError("Platform config must be an object")
        spider.platform_config = {**config, **spider.selectors.get("platform_config", {})}
        spider.configure_platform()
        return spider

    def configure_platform(self):
        pass

    @staticmethod
    def json_product(response):
        def visit(value):
            if isinstance(value, list):
                for child in value:
                    found = visit(child)
                    if found:
                        return found
            if isinstance(value, dict):
                types = value.get("@type", [])
                types = [types] if isinstance(types, str) else types
                if "Product" in types or "https://schema.org/Product" in types:
                    return value
                return visit(value.get("@graph", []))
            return {}

        for raw in response.xpath("//script[@type='application/ld+json']/text()").getall():
            try:
                product = visit(json.loads(raw))
                if product:
                    return product
            except (ValueError, TypeError):
                continue
        return {}

    @staticmethod
    def amount(value):
        if isinstance(value, bool):
            return 0.0
        try:
            number = float(value)
            return number if math.isfinite(number) and number > 0 else 0.0
        except (ValueError, TypeError):
            return 0.0

    @classmethod
    def offer(cls, product):
        offers = product.get("offers", [])
        offers = [offers] if isinstance(offers, dict) else offers
        for value in offers if isinstance(offers, list) else []:
            if isinstance(value, dict) and cls.amount(value.get("price") or value.get("lowPrice")):
                return value
        return {}

    @staticmethod
    def image_url(value):
        if isinstance(value, list):
            value = value[0] if value else ""
        if isinstance(value, dict):
            value = value.get("url") or value.get("contentUrl") or ""
        return value if isinstance(value, str) else ""

    def make_item(self, identity, name, price, currency, response=None, description="", image="", category="Others", sku=""):
        if not name or not self.amount(price) or not identity:
            self.metrics.error("product_parse", f"{self.engine}: product lacks name, positive price or identity")
            return None
        code, source = resolve_currency(self, response, preferred=currency)
        identity_sku = sku or f"{self.engine.upper()}-{hashlib.sha256(str(identity).encode()).hexdigest()[:24].upper()}"
        return {
            "SKU": str(identity_sku), "Name": self._clean_text(name),
            "Regular price": self.amount(price), "货币": code, "币种来源": source,
            "Description": self._clean_description(description), "Images": image or "",
            "Categories": category or "Others", "cf_opingts": "",
            "自定义分类": self.custom_category, "产品标签": self.product_role,
            "原站域名": urlparse(self.domain).netloc, "语言": "en",
        }

    def parse_product_detail(self, response):
        self.metrics.counts["fetched"] += 1
        product = self.json_product(response)
        offer = self.offer(product)
        name = product.get("name") or self._first(response, "title")
        price = self.amount(offer.get("price") or offer.get("lowPrice")) or self._price(response)
        image = self.image_url(product.get("image")) or self._first_url(response, "images")
        description = product.get("description") or " ".join(response.xpath(self.selectors["description"]).getall())
        item = self.make_item(product.get("productID") or response.url, name, price, offer.get("priceCurrency"),
                              response, description, response.urljoin(image) if image else "",
                              product.get("category") if isinstance(product.get("category"), str) else "Others",
                              product.get("sku") or self._first(response, "sku"))
        if item:
            yield item

    def _is_product_detail_url(self, url, trusted_product_sitemap=False):
        return same_store(url, self.domain) and (
            bool(self.PRODUCT_URL.search(urlparse(url).path))
            or (trusted_product_sitemap and super()._is_product_detail_url(url, True))
        )

    def discovery_failed(self, reason, message):
        if self.navigation_started:
            return
        self.navigation_started = True
        self.metrics.discovery_sources["navigation"] += 1
        self.logger.warning("%s sitemap unavailable (%s); trying public store navigation", self.engine, reason)
        url = self.domain + "/"
        self.navigation_seen.add(url)
        yield scrapy.Request(url, callback=self.parse_navigation, errback=self.navigation_failed,
                             dont_filter=True, meta={"navigation_depth": 0})

    def navigation_failed(self, failure):
        self.metrics.counts["requests_failed"] += 1
        self.metrics.error("navigation_request", failure.getErrorMessage())

    def empty_sitemap(self, response):
        yield from self.discovery_failed("empty_sitemap", response.url)

    def limit_reached(self):
        if not self.discovery_limited:
            self.discovery_limited = True
            self.metrics.error("discovery_limit", "Catalog discovery limit reached; results may be incomplete")

    def parse_navigation(self, response):
        for raw in response.xpath("//a/@href").getall():
            url = self._canonical_product_url(response.urljoin(raw))
            if not self._is_product_detail_url(url) or url in self.seen_product_urls:
                continue
            if len(self.seen_product_urls) >= self.max_items:
                self.limit_reached()
                break
            self.seen_product_urls.add(url)
            self.metrics.counts["discovered"] += 1
            yield scrapy.Request(url, callback=self.parse_product_detail, errback=self.product_failed)
        depth = response.meta.get("navigation_depth", 0)
        for raw in response.xpath(self.NAVIGATION_XPATH).getall():
            parsed = urlparse(response.urljoin(raw))
            if not same_store(parsed.geturl(), self.domain) or self._is_product_detail_url(parsed.geturl()):
                continue
            if re.search(r"/(?:cart|checkout|login|account|search|blog|contact|privacy)(?:[./_-]|$)", parsed.path, re.I):
                continue
            query = parse_qs(parsed.query)
            if set(query) - {"page", "p"}:
                continue
            url = urlunparse((parsed.scheme, parsed.netloc, parsed.path or "/", "", urlencode(query, doseq=True), ""))
            if url in self.navigation_seen:
                continue
            if depth >= 5 or len(self.navigation_seen) >= self.crawler.settings.getint("PRODUCT_DISCOVERY_MAX_PAGES", 100):
                self.limit_reached()
                continue
            self.navigation_seen.add(url)
            yield scrapy.Request(url, callback=self.parse_navigation, errback=self.navigation_failed,
                                 meta={"navigation_depth": depth + 1})

    def api_failure(self, failure):
        self.metrics.counts["requests_failed"] += 1
        if not self.api_seen:
            yield self._sitemap_request(0)
        else:
            self.metrics.error("api_request", failure.getErrorMessage())
