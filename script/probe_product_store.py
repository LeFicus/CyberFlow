"""Read a store homepage and one product without running pipelines or writing data."""

import argparse
import json
from pathlib import Path
import sys

import requests

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "crawler-service/app/crawler/ecommerce_spider"))

from scrapy.crawler import Crawler, CrawlerProcess
from scrapy import Request, signals
from scrapy.http import HtmlResponse
from scrapy.settings import Settings

from ecommerce_spider.request_policy import configured_proxy, is_verification_page, same_store
from ecommerce_spider.spiders.bigcommerce_crawl import BigCommerceCrawlSpider


def scrapy_probe(domain, url):
    """Exercise the actual Scrapy downloader/middlewares for one known product."""
    if not same_store(url, domain):
        raise SystemExit("Sample URL must belong to the configured store")

    class SampleSpider(BigCommerceCrawlSpider):
        async def start(self):
            yield Request(url, callback=self.parse_product_detail, errback=self.product_failed)

    settings = Settings()
    settings.setmodule("ecommerce_spider.settings")
    settings.setdict({"ITEM_PIPELINES": {}, "EXTENSIONS": {}, "LOG_LEVEL": "WARNING",
                      "ROBOTSTXT_OBEY": False, "TELNETCONSOLE_ENABLED": False,
                      "CLOSESPIDER_TIMEOUT": 90}, priority="cmdline")
    process = CrawlerProcess(settings)
    crawler = process.create_crawler(SampleSpider)
    items = []

    def collect(item, response, spider):
        items.append(item)

    crawler.signals.connect(collect, signal=signals.item_scraped, weak=False)
    process.crawl(crawler, domain=domain)
    process.start()
    stats = crawler.stats.get_stats()
    print(json.dumps({key: value for key, value in stats.items() if key.startswith("product/")}))
    for item in items:
        print(json.dumps({key: item.get(key) for key in ("Name", "Regular price", "货币", "币种来源")}, ensure_ascii=False))
    if len(items) != 1 or not items[0].get("货币"):
        raise SystemExit("Scrapy did not produce one product with confirmed currency")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("domain")
    parser.add_argument("--scrapy-product-url", help="Read one known product via the actual Scrapy middlewares, without pipelines")
    args = parser.parse_args()
    if args.scrapy_product_url:
        return scrapy_probe(args.domain, args.scrapy_product_url)
    settings = Settings()
    settings.setmodule("ecommerce_spider.settings")
    spider = BigCommerceCrawlSpider.from_crawler(Crawler(BigCommerceCrawlSpider, settings), domain=args.domain)
    session = requests.Session()
    session.trust_env = False
    session.headers.update({"User-Agent": spider.custom_settings["USER_AGENT"]})
    proxy = configured_proxy(settings)
    if proxy:
        session.proxies.update({"http": proxy, "https": proxy})
    session.verify = settings.get("PRODUCT_CRAWL_CA_BUNDLE") or True
    pending = [(spider.domain + "/", spider.parse_navigation)]
    visited = set()
    try:
        for _ in range(5):
            if not pending:
                break
            url, callback = pending.pop(0)
            if url in visited or not same_store(url, spider.domain):
                continue
            visited.add(url)
            result = session.get(url, timeout=(10, 20), allow_redirects=False)
            print(json.dumps({"url": url, "status": result.status_code, "bytes": len(result.content)}))
            if result.status_code != 200:
                continue
            response = HtmlResponse(url, body=result.content, encoding=result.encoding or "utf-8",
                                    request=Request(url))
            if is_verification_page(response):
                print("verification_required")
                break
            for item in callback(response):
                if isinstance(item, dict):
                    print(json.dumps({key: item.get(key) for key in ("Name", "Regular price", "货币", "币种来源")}, ensure_ascii=False))
                    if not item.get("货币"):
                        raise SystemExit("Product sample has no confirmed currency")
                    return
                pending.append((item.url, item.callback))
        raise SystemExit("No product sample found within five read-only requests")
    finally:
        session.close()


if __name__ == "__main__":
    main()
