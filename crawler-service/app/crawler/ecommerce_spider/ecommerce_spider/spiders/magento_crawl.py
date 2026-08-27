"""Magento 2/Open Source: read-only catalog GraphQL, then Magento HTML fallback."""

import json
import re
from urllib.parse import urlencode
import scrapy

from ecommerce_spider.spiders.structured_store import StructuredStoreSpider


class MagentoCrawlSpider(StructuredStoreSpider):
    name = "magento_crawl"
    engine = "magento"
    PRODUCT_URL = re.compile(r"\.html$|/products?/[^/]+", re.I)
    DEFAULT_SELECTORS = {
        **StructuredStoreSpider.DEFAULT_SELECTORS,
        "title": "//h1[contains(@class,'page-title')]//text() | //meta[@property='og:title']/@content",
        "price": "//*[@data-price-type='finalPrice']/@data-price-amount | //*[@itemprop='price']/@content",
        "sku": "//*[contains(@class,'product') and contains(@class,'sku')]//*[@itemprop='sku']/text()",
    }
    PAGE_SIZE = 100

    async def start(self):
        yield self.catalog_request(1)

    def catalog_request(self, page):
        query = '''query { products(filter:{price:{from:"0"}}, pageSize:100, currentPage:PAGE) {
          total_count page_info {current_page total_pages} items {
            sku name description {html} small_image {url} categories {name}
            price_range { minimum_price { final_price {value currency} } }
          } } }'''.replace("PAGE", str(page))
        return scrapy.Request(self.domain + "/graphql?" + urlencode({"query": query}),
                              callback=self.parse_catalog, errback=self.api_failure, meta={"catalog_page": page})

    def parse_catalog(self, response):
        page = response.meta["catalog_page"]
        try:
            payload = json.loads(response.text)
            data = payload.get("data", {}).get("products")
            if payload.get("errors") or not isinstance(data, dict) or not isinstance(data.get("items"), list):
                raise ValueError("Magento GraphQL schema/errors response")
            items = data["items"]
            total = int(data["total_count"])
            info = data["page_info"]
            if total < 0 or int(info["current_page"]) != page:
                raise ValueError("Invalid Magento pagination")
            if total == 0 and not items and page == 1:
                self.metrics.confirmed_empty = True
                return
            if not items:
                raise ValueError("Magento catalog ended before total_count")
        except (ValueError, TypeError, KeyError, AttributeError) as exc:
            if page == 1 and not self.api_seen:
                self.logger.warning("Magento API unavailable; falling back to sitemap")
                yield self._sitemap_request(0)
            else:
                self.metrics.error("api_schema", str(exc))
            return
        self.metrics.discovery_sources["magento_graphql"] += 1
        before = len(self.api_seen)
        for product in items:
            try:
                sku = product["sku"]
                if not sku or sku in self.api_seen:
                    raise ValueError("Missing or repeated Magento SKU across catalog pages")
                self.api_seen.add(sku)
                self.metrics.counts["discovered"] += 1
                self.metrics.counts["fetched"] += 1
                price = product["price_range"]["minimum_price"]["final_price"]
                item = self.make_item(sku, product.get("name"), price.get("value"), price.get("currency"),
                                     description=(product.get("description") or {}).get("html", ""),
                                     image=(product.get("small_image") or {}).get("url", ""),
                                     category="|||".join(c.get("name", "") for c in product.get("categories", [])[:2]), sku=sku)
                if item:
                    yield item
            except (ValueError, TypeError, KeyError, AttributeError) as exc:
                self.metrics.error("product_parse", str(exc))
        if len(self.api_seen) < total:
            if len(self.api_seen) == before or page >= 200 or len(self.api_seen) >= self.max_items:
                self.limit_reached()
            else:
                yield self.catalog_request(page + 1)
        elif len(self.api_seen) != total:
            self.metrics.error("api_pagination", "Magento count differs from total_count")
