"""Print an auditable crawler fingerprint; usable during image build and deployment."""

import hashlib
import json
from pathlib import Path

from scrapy.settings import Settings
from scrapy.utils.misc import load_object


REVISION = "product-crawl-phase3-v1"
REQUIRED_MIDDLEWARES = (
    "ecommerce_spider.middlewares.ProductRequestPolicyMiddleware",
    "ecommerce_spider.middlewares.BigCommerceRequestsFallbackMiddleware",
)


def runtime_report(settings=None):
    from ecommerce_spider.spiders.bigcommerce_crawl import BigCommerceCrawlSpider
    from ecommerce_spider.platforms import SPIDERS

    if settings is None:
        settings = Settings()
        settings.setmodule("ecommerce_spider.settings")
        BigCommerceCrawlSpider.update_settings(settings)
    configured = settings.getdict("DOWNLOADER_MIDDLEWARES")
    enabled = [name for name in REQUIRED_MIDDLEWARES if configured.get(name) is not None]
    for name in enabled:
        load_object(name)
    digest = hashlib.sha256()
    root = Path(__file__).parent
    for relative in (
        "check_runtime.py", "crawl_options.py", "crawl_result.py", "pipelines.py",
        "normalization.py", "middlewares.py", "request_policy.py", "settings.py", "spiders/exchange_rates.json",
        "platforms.py", "shopify_variants.py",
        "spiders/bigcommerce_crawl.py", "spiders/platform_crawl.py", "spiders/shopify_crawl.py",
        "spiders/structured_store.py", "spiders/magento_crawl.py", "spiders/wix_crawl.py",
        "spiders/ecwid_crawl.py", "spiders/shopline_crawl.py",
    ):
        digest.update(relative.encode())
        digest.update((root / relative).read_bytes().replace(b"\r\n", b"\n"))
    return {"revision": REVISION, "fingerprint": digest.hexdigest()[:16], "engines": SPIDERS,
            "middlewares": enabled, "protection_ready": len(enabled) == len(REQUIRED_MIDDLEWARES)}


def main():
    report = runtime_report()
    print(json.dumps(report, ensure_ascii=False))
    if not report["protection_ready"]:
        raise SystemExit("Required product request middlewares are not enabled")


if __name__ == "__main__":
    main()
