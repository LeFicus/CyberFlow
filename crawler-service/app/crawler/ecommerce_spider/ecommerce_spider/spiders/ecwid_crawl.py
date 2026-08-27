"""Ecwid Instant Site SEO pages, or the documented API with an explicit public token."""

import json
import re
from urllib.parse import urlparse, urlencode
import scrapy

from ecommerce_spider.crawl_options import currency_code
from ecommerce_spider.request_policy import same_store
from ecommerce_spider.spiders.structured_store import StructuredStoreSpider


class EcwidCrawlSpider(StructuredStoreSpider):
    name = "ecwid_crawl"
    engine = "ecwid"
    PRODUCT_URL = re.compile(r"/products?/[^/]+|(?:-|/)p\d+(?:/|$)", re.I)
    DEFAULT_SELECTORS = {
        **StructuredStoreSpider.DEFAULT_SELECTORS,
        "title": "//*[contains(@class,'product-details__product-title')]//text() | //meta[@property='og:title']/@content",
        "price": "//*[@itemprop='price']/@content | //meta[@property='product:price:amount']/@content",
        "description": "//*[contains(@class,'product-details__product-description')]//text() | //meta[@property='og:description']/@content",
    }

    def configure_platform(self):
        self.store_id = str(self.platform_config.get("store_id") or "")
        self.public_token = str(self.platform_config.get("public_token") or "")
        self.shop_currency = ""
        if self.store_id or self.public_token:
            if not self.store_id.isdigit() or not re.fullmatch(r"public_[A-Za-z0-9]+", self.public_token):
                raise ValueError("Ecwid API requires a numeric store_id and public_ token; secret tokens are not accepted")
            self.allowed_domains.append("app.ecwid.com")

    def request_allowed(self, url):
        if same_store(url, self.domain):
            return True
        parsed = urlparse(url)
        return bool(self.public_token and parsed.scheme == "https" and parsed.netloc == "app.ecwid.com"
                    and parsed.path in {f"/api/v3/{self.store_id}/products", f"/api/v3/{self.store_id}/profile"})

    async def start(self):
        if self.public_token:
            yield self.api_request("profile", self.parse_profile)
        else:
            yield self._sitemap_request(0)

    def api_request(self, resource, callback, offset=0):
        query = urlencode({"offset": offset, "limit": 100, "enabled": "true"}) if resource == "products" else ""
        return scrapy.Request(f"https://app.ecwid.com/api/v3/{self.store_id}/{resource}" + ("?" + query if query else ""),
                              headers={"Authorization": "Bearer " + self.public_token},
                              callback=callback, errback=self.api_failed, meta={"offset": offset, "dont_redirect": True})

    def api_failed(self, failure):
        self.metrics.counts["requests_failed"] += 1
        self.metrics.error("ecwid_api_request", "Ecwid public API failed; check store_id, public token and network")

    def parse_profile(self, response):
        try:
            data = json.loads(response.text)
            self.shop_currency = currency_code(data.get("formatsAndUnits", {}).get("currency"))
        except (ValueError, AttributeError, TypeError):
            self.metrics.error("ecwid_profile", "Invalid Ecwid store profile")
            return
        yield self.api_request("products", self.parse_catalog)

    def parse_catalog(self, response):
        try:
            data = json.loads(response.text)
            items, total = data["items"], int(data["total"])
            offset = response.meta["offset"]
            if not isinstance(items, list) or total < 0 or int(data["offset"]) != offset or int(data["count"]) != len(items):
                raise ValueError("Invalid Ecwid catalog pagination")
            if not items:
                if total == 0 and offset == 0:
                    self.metrics.confirmed_empty = True
                    return
                raise ValueError("Ecwid catalog ended before total")
        except (ValueError, KeyError, TypeError) as exc:
            self.metrics.error("api_schema", str(exc))
            return
        self.metrics.discovery_sources["ecwid_public_api"] += 1
        for product in items:
            try:
                identity = product["id"]
                if not identity or identity in self.api_seen:
                    raise ValueError("Missing or repeated Ecwid product ID")
                self.api_seen.add(identity)
                self.metrics.counts["discovered"] += 1
                self.metrics.counts["fetched"] += 1
                item = self.make_item(identity, product.get("name"), product.get("price"), self.shop_currency,
                                     description=product.get("description", ""),
                                     image=product.get("originalImageUrl") or product.get("imageUrl") or "",
                                     sku=product.get("sku", ""))
                if item:
                    yield item
            except (ValueError, TypeError, KeyError) as exc:
                self.metrics.error("product_parse", str(exc))
        next_offset = offset + len(items)
        if next_offset < total:
            if next_offset >= self.max_items:
                self.limit_reached()
            else:
                yield self.api_request("products", self.parse_catalog, next_offset)
        elif len(self.api_seen) != total:
            self.metrics.error("api_pagination", "Ecwid distinct product count differs from total")
