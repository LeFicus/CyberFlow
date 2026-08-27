"""Wix Stores: public product sitemap and server-rendered Product JSON-LD."""

import re
from ecommerce_spider.spiders.structured_store import StructuredStoreSpider


class WixCrawlSpider(StructuredStoreSpider):
    name = "wix_crawl"
    engine = "wix"
    SITEMAP_PATHS = ("/sitemap.xml", "/store-products-sitemap.xml")
    PRODUCT_URL = re.compile(r"/product-page/[^/]+", re.I)
    DEFAULT_SELECTORS = {
        **StructuredStoreSpider.DEFAULT_SELECTORS,
        "title": "//*[@data-hook='product-title']//text() | //meta[@property='og:title']/@content",
        "price": "//*[@data-hook='formatted-primary-price']//text() | //*[@itemprop='price']/@content",
        "description": "//*[@data-hook='description']//text() | //meta[@property='og:description']/@content",
    }
