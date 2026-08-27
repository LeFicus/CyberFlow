"""Dedicated BigCommerce product crawler.

The crawler accepts only a site domain, discovers product detail URLs from the
site's sitemap, and parses Schema.org Product JSON-LD first. Configurable page
selectors inherited from the generic spider are used only for missing fields.
"""

import hashlib
import json
import re
from collections import Counter
from html import unescape
from urllib.parse import urlparse

from ecommerce_spider.spiders.platform_crawl import PlatformCrawlSpider


class BigCommerceCrawlSpider(PlatformCrawlSpider):
    """BigCommerce sitemap and storefront-metadata product crawler."""

    name = "bigcommerce_crawl"
    SUPPORTED = {"bigcommerce"}
    SITEMAP_PATHS = ("/xmlsitemap.php", "/sitemap.xml", "/sitemap_index.xml")
    custom_settings = {
        **PlatformCrawlSpider.custom_settings,
        # BigCommerce product pages are public storefront content. Some stores
        # publish overly broad robots rules that also hide their product sitemap.
        "ROBOTSTXT_OBEY": False,
        # Match the working standalone collector instead of Scrapy's default UA.
        "USER_AGENT": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/120.0.0.0 Safari/537.36"
        ),
        "DEFAULT_REQUEST_HEADERS": {
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
        },
        "DOWNLOADER_MIDDLEWARES": {
            # Run just before Scrapy's RetryMiddleware on the response path so
            # a successful requests response is parsed without redundant retries.
            "ecommerce_spider.middlewares.BigCommerceRequestsFallbackMiddleware": 560,
        },
        # The generic spider is intentionally slow, but that setting makes a
        # normal BigCommerce catalog exceed the consumer's task timeout.
        "CONCURRENT_REQUESTS": 16,
        "CONCURRENT_REQUESTS_PER_DOMAIN": 8,
        "DOWNLOAD_DELAY": 0.25,
        "AUTOTHROTTLE_START_DELAY": 0.5,
        "AUTOTHROTTLE_TARGET_CONCURRENCY": 4.0,
    }
    JSONLD_SCRIPT_RE = re.compile(
        r"<script\b(?=[^>]*\btype\s*=\s*(?:[\"']application/ld\+json[\"']|application/ld\+json))"
        r"[^>]*>(.*?)</script\s*>",
        re.IGNORECASE | re.DOTALL,
    )
    BCDATA_ASSIGNMENT_RE = re.compile(
        r"(?:\bvar\s+|\bwindow\.)BCData\s*=\s*",
        re.IGNORECASE,
    )

    def __init__(self, domain=None, category="未知分类", product_role="main", config_json=None,
                 mode="prod", *args, **kwargs):
        super().__init__(
            domain=domain,
            category=category,
            product_role=product_role,
            platform="bigcommerce",
            selector_profile="woocommerce",
            config_json=config_json,
            mode=mode,
            *args,
            **kwargs,
        )
        self.rejected_product_reasons = Counter()

    def parse_product_detail(self, response):
        bcdata = self._bcdata(response)
        attributes = bcdata.get("product_attributes", {}) if isinstance(bcdata, dict) else {}
        if not isinstance(attributes, dict):
            attributes = {}
        product = self._jsonld_product(response)
        name = (
            self._jsonld_text(product.get("name"))
            or self._first(response, "title")
            or self._default_first(response, "title")
        )
        price = (
            self._bcdata_price(attributes)
            or self._jsonld_price(product)
            or self._price(response)
            or self._default_price(response)
        )
        image = (
            self._jsonld_image(product.get("image"))
            or self._first_url(response, "images")
            or self._default_first_url(response, "images")
        )
        missing = [
            field
            for field, is_missing in (
                ("name", not name),
                ("price", price <= 0),
                ("image", not image),
            )
            if is_missing
        ]
        if missing:
            reason = ",".join(missing)
            self.rejected_product_reasons[reason] += 1
            if sum(self.rejected_product_reasons.values()) <= 20:
                self.logger.warning(
                    "丢弃 BigCommerce 商品页 → 缺少 %s | %s",
                    reason,
                    response.url,
                )
            return

        original_sku = (
            self._jsonld_text(attributes.get("sku"))
            or self._jsonld_text(
                product.get("sku") or product.get("mpn") or product.get("productID")
            )
            or self._first(response, "sku")
            or self._default_first(response, "sku")
        )
        identity = f"{urlparse(response.url).netloc}|{response.url}|{original_sku}"
        sku = original_sku or f"BIGC-{hashlib.md5(identity.encode()).hexdigest()[:12].upper()}"

        description = self._clean_description(self._jsonld_text(product.get("description")))
        if not description:
            parts = response.xpath(self.selectors["description"]).getall()
            description = self._clean_description(" ".join(part.strip() for part in parts if part.strip()))
        if not description:
            parts = response.xpath(PlatformCrawlSpider.DEFAULT_SELECTORS["description"]).getall()
            description = self._clean_description(" ".join(part.strip() for part in parts if part.strip()))

        categories = self._jsonld_category(product.get("category"))
        if not categories:
            breadcrumbs = [
                value.strip()
                for value in response.xpath(self.selectors["breadcrumb_links"]).getall()
                if value.strip()
            ]
            breadcrumbs = [value for value in breadcrumbs if value.lower() not in {"home", name.lower()}]
            categories = "|||".join(breadcrumbs[:2])

        yield {
            "SKU": sku,
            "Name": name,
            "Description": description,
            "Regular price": f"{price:.2f}",
            "Categories": categories or "Others",
            "Images": response.urljoin(image),
            "cf_opingts": "",
            "自定义分类": self.custom_category,
            "产品标签": self.product_role,
            "原站域名": urlparse(response.url).netloc,
            "分布网站识别": 0,
            "语言": "en",
            "货币": (
                self._bcdata_currency(attributes)
                or self._jsonld_currency(product)
                or self._currency(response)
            ),
        }
        self.logger.info("成功生成商品 → %s | %s", sku, name[:60])

    def closed(self, reason):
        if self.rejected_product_reasons:
            summary = ", ".join(
                f"{missing}={count}"
                for missing, count in self.rejected_product_reasons.most_common()
            )
            self.logger.warning("BigCommerce 商品页字段缺失汇总 → %s", summary)

    def _bcdata(self, response):
        """Decode BigCommerce Stencil's BCData assignment from the raw page."""
        decoder = json.JSONDecoder()
        for match in self.BCDATA_ASSIGNMENT_RE.finditer(response.text):
            raw = response.text[match.end():].lstrip()
            for candidate in (raw, unescape(raw)):
                try:
                    payload, _ = decoder.raw_decode(candidate)
                except (TypeError, json.JSONDecodeError):
                    continue
                if isinstance(payload, dict):
                    return payload
        return {}

    @classmethod
    def _bcdata_price(cls, attributes):
        price_data = attributes.get("price") if isinstance(attributes, dict) else None
        if not isinstance(price_data, dict):
            return 0.0
        for key in ("without_tax", "with_tax", "rrp_without_tax", "rrp_with_tax"):
            value = price_data.get(key)
            if isinstance(value, dict):
                value = value.get("value") or value.get("formatted")
            price = cls._parse_jsonld_price(value)
            if price > 0:
                return price
        return cls._parse_jsonld_price(price_data.get("value"))

    @staticmethod
    def _bcdata_currency(attributes):
        price_data = attributes.get("price") if isinstance(attributes, dict) else None
        if not isinstance(price_data, dict):
            return ""
        for key in ("without_tax", "with_tax", "rrp_without_tax", "rrp_with_tax"):
            value = price_data.get(key)
            if not isinstance(value, dict):
                continue
            currency = value.get("currency") or value.get("currency_code")
            if currency:
                return str(currency).strip().upper()
        currency = price_data.get("currency") or price_data.get("currency_code")
        return str(currency).strip().upper() if currency else ""

    def _default_first(self, response, key):
        values = response.xpath(PlatformCrawlSpider.DEFAULT_SELECTORS[key]).getall()
        return next((self._clean_text(value) for value in values if value.strip()), "")

    @staticmethod
    def _default_first_url(response, key):
        values = response.xpath(PlatformCrawlSpider.DEFAULT_SELECTORS[key]).getall()
        return next((unescape(value).strip() for value in values if value.strip()), "")

    def _default_price(self, response):
        for value in response.xpath(PlatformCrawlSpider.DEFAULT_SELECTORS["price"]).getall():
            price = self._parse_jsonld_price(value)
            if price > 0:
                return price
        return 0.0

    def _jsonld_product(self, response):
        # BigCommerce JSON-LD is read straight from the response body. This
        # deliberately avoids XPath/BeautifulSoup parsing of large detail pages.
        for match in self.JSONLD_SCRIPT_RE.finditer(response.text):
            raw = match.group(1).strip().removeprefix("<!--").removesuffix("-->").strip()
            for candidate in (raw, unescape(raw)):
                try:
                    payload = json.loads(candidate)
                except (TypeError, json.JSONDecodeError):
                    continue
                product = self._find_jsonld_product(payload)
                if product:
                    return product
        return {}

    def _find_jsonld_product(self, value):
        if isinstance(value, dict):
            types = value.get("@type", [])
            if isinstance(types, str):
                types = [types]
            if any(str(item).rstrip("/").rsplit("/", 1)[-1].lower() == "product" for item in types):
                return value
            for nested in value.get("@graph", []):
                product = self._find_jsonld_product(nested)
                if product:
                    return product
        elif isinstance(value, list):
            for nested in value:
                product = self._find_jsonld_product(nested)
                if product:
                    return product
        return {}

    @staticmethod
    def _jsonld_text(value):
        if isinstance(value, str):
            return BigCommerceCrawlSpider._clean_text(value)
        if isinstance(value, (int, float)):
            return str(value)
        if isinstance(value, dict):
            return BigCommerceCrawlSpider._jsonld_text(
                value.get("name") or value.get("url") or value.get("value")
            )
        if isinstance(value, list):
            return next(
                (text for item in value if (text := BigCommerceCrawlSpider._jsonld_text(item))),
                "",
            )
        return ""

    def _jsonld_price(self, product):
        offers = product.get("offers") if isinstance(product, dict) else None
        offers = offers if isinstance(offers, list) else [offers]
        for offer in offers:
            if not isinstance(offer, dict):
                continue
            price = self._parse_jsonld_price(
                offer.get("price") or offer.get("lowPrice") or offer.get("highPrice")
            )
            if price > 0:
                return price
        return 0.0

    @staticmethod
    def _jsonld_currency(product):
        offers = product.get("offers") if isinstance(product, dict) else None
        offers = offers if isinstance(offers, list) else [offers]
        for offer in offers:
            if isinstance(offer, dict) and offer.get("priceCurrency"):
                return str(offer["priceCurrency"]).strip().upper()
        return ""

    @staticmethod
    def _parse_jsonld_price(value):
        if value is None:
            return 0.0
        match = re.search(r"[\d.,]+", str(value).replace(" ", ""))
        if not match:
            return 0.0
        value = match.group(0)
        if value.count(",") == 1 and "." not in value and len(value.rsplit(",", 1)[1]) == 2:
            value = value.replace(",", ".")
        else:
            value = value.replace(",", "")
        try:
            return float(value)
        except ValueError:
            return 0.0

    @classmethod
    def _jsonld_image(cls, value):
        if isinstance(value, str):
            return value.strip()
        if isinstance(value, dict):
            return cls._jsonld_image(value.get("url") or value.get("contentUrl"))
        if isinstance(value, list):
            return next((image for item in value if (image := cls._jsonld_image(item))), "")
        return ""

    @classmethod
    def _jsonld_category(cls, value):
        if isinstance(value, str):
            return value.strip()
        if isinstance(value, dict):
            return cls._jsonld_text(value.get("name"))
        if isinstance(value, list):
            values = [cls._jsonld_category(item) for item in value]
            return "|||".join(item for item in values if item)
        return ""
