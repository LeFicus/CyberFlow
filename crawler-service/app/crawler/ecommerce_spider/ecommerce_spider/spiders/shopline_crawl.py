"""SHOPLINE: sitemap/product pages and per-handle public Ajax fallback (minor units)."""

import json
import re
from urllib.parse import urlparse, urlencode, unquote
import scrapy

from ecommerce_spider.crawl_options import resolve_currency
from ecommerce_spider.spiders.structured_store import StructuredStoreSpider


class ShoplineCrawlSpider(StructuredStoreSpider):
    name = "shopline_crawl"
    engine = "shopline"
    PRODUCT_URL = re.compile(r"/products/[^/]+", re.I)
    DEFAULT_SELECTORS = {
        **StructuredStoreSpider.DEFAULT_SELECTORS,
        "title": "//h1[contains(@class,'product-title')]//text() | //meta[@property='og:title']/@content",
        "price": "//*[@itemprop='price']/@content | //meta[@property='product:price:amount']/@content",
    }
    # Restrict conversion to known minor-unit currencies instead of guessing.
    MINOR_UNITS = {**dict.fromkeys(("USD", "AUD", "CAD", "GBP", "EUR", "NZD", "CNY", "HKD", "SGD", "TWD"), 100),
                   **dict.fromkeys(("JPY", "KRW", "VND"), 1), **dict.fromkeys(("KWD", "BHD", "OMR"), 1000)}

    def parse_product_detail(self, response):
        if self.offer(self.json_product(response)) or self._price(response):
            yield from super().parse_product_detail(response)
            return
        handle = unquote(urlparse(response.url).path.rstrip("/").rsplit("/", 1)[-1])
        currency, source = resolve_currency(self, response)
        yield scrapy.Request(self.domain + "/api/product/products.json?" + urlencode({"handle": handle}),
                              callback=self.parse_ajax, errback=self.product_failed,
                              meta={"product_url": response.url, "handle": handle, "currency": currency,
                                    "currency_source": source})

    def parse_ajax(self, response):
        self.metrics.counts["fetched"] += 1
        try:
            data = json.loads(response.text)
            products = data.get("products")
            if not isinstance(products, list):
                raise ValueError("SHOPLINE Ajax response has no products array")
            matched = [p for p in products if p.get("handle") == response.meta["handle"]]
            if len(matched) != 1:
                raise ValueError("SHOPLINE response does not uniquely match requested handle")
            product = matched[0]
            currency = response.meta["currency"]
            if currency not in self.MINOR_UNITS:
                raise ValueError("Unknown SHOPLINE Ajax currency/minor unit; supply store currency or use Product JSON-LD")
            price = self.amount(product.get("price")) / self.MINOR_UNITS[currency]
            item = self.make_item(product.get("id"), product.get("title"), price, currency,
                                 description=product.get("description", ""),
                                 image=self.image_url(product.get("featured_image") or product.get("images")))
            if item:
                item["币种来源"] = response.meta["currency_source"]
                yield item
        except (ValueError, KeyError, TypeError, AttributeError) as exc:
            self.metrics.error("shopline_ajax", str(exc))
