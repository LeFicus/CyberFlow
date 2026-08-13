"""Dedicated BigCommerce product crawler.

The crawler accepts only a site domain, discovers product detail URLs from the
site's sitemap, and parses Schema.org Product JSON-LD first. Configurable page
selectors inherited from the generic spider are used only for missing fields.
"""

import hashlib
import json
import re
from html import unescape
from urllib.parse import urlparse

from ecommerce_spider.spiders.platform_crawl import PlatformCrawlSpider


class BigCommerceCrawlSpider(PlatformCrawlSpider):
    """BigCommerce sitemap and JSON-LD product crawler."""

    name = "bigcommerce_crawl"
    SUPPORTED = {"bigcommerce"}
    SITEMAP_PATHS = ("/xmlsitemap.php", "/sitemap.xml", "/sitemap_index.xml")
    JSONLD_SCRIPT_RE = re.compile(
        r"<script\b(?=[^>]*\btype\s*=\s*(?:[\"']application/ld\+json[\"']|application/ld\+json))"
        r"[^>]*>(.*?)</script\s*>",
        re.IGNORECASE | re.DOTALL,
    )

    def __init__(self, domain=None, category="未知分类", config_json=None,
                 mode="prod", *args, **kwargs):
        super().__init__(
            domain=domain,
            category=category,
            platform="bigcommerce",
            selector_profile="woocommerce",
            config_json=config_json,
            mode=mode,
            *args,
            **kwargs,
        )

    def parse_product_detail(self, response):
        product = self._jsonld_product(response)
        name = self._jsonld_text(product.get("name")) or self._first(response, "title")
        price = self._jsonld_price(product) or self._price(response)
        image = self._jsonld_image(product.get("image")) or self._first_url(response, "images")
        if not name or price <= 0 or not image:
            return

        original_sku = self._jsonld_text(
            product.get("sku") or product.get("mpn") or product.get("productID")
        ) or self._first(response, "sku")
        identity = f"{urlparse(response.url).netloc}|{response.url}|{original_sku}"
        sku = original_sku or f"BIGC-{hashlib.md5(identity.encode()).hexdigest()[:12].upper()}"

        description = self._clean_description(self._jsonld_text(product.get("description")))
        if not description:
            parts = response.xpath(self.selectors["description"]).getall()
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
            "原站域名": urlparse(response.url).netloc,
            "分布网站识别": 0,
            "语言": "en",
        }
        self.logger.info("成功生成商品 → %s | %s", sku, name[:60])

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
