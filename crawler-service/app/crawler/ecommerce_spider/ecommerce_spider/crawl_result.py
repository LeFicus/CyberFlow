"""Authoritative crawl counters, published only after database commits."""

import json
from collections import Counter

from scrapy import signals


PROGRESS_PREFIX = "CYBERFLOW_CRAWL_PROGRESS="
RESULT_PREFIX = "CYBERFLOW_CRAWL_RESULT="
COUNTERS = (
    "discovered", "fetched", "generated", "filtered", "accepted",
    "inserted", "updated", "unchanged", "persisted", "failed", "requests_failed",
)


class PersistenceError(RuntimeError):
    pass


class CrawlMetrics:
    def __init__(self):
        self.counts = Counter({key: 0 for key in COUNTERS})
        self.filtered_reasons = Counter()
        self.failed_reasons = Counter()
        self.currency_sources = Counter()
        self.currencies = Counter()
        self.discovery_sources = Counter()
        self.errors = []
        self.pipeline_opened = False
        self.pipeline_closed = False
        self.confirmed_empty = False

    def filter(self, reason):
        self.counts["filtered"] += 1
        self.filtered_reasons[reason] += 1

    def error(self, reason, message, count=1):
        self.counts["failed"] += count
        self.failed_reasons[reason] += count
        if len(self.errors) < 10:
            self.errors.append({"reason": reason, "message": str(message)[:500]})

    def snapshot(self):
        return {
            "version": 1,
            **self.counts,
            "filtered_reasons": dict(self.filtered_reasons),
            "failed_reasons": dict(self.failed_reasons),
            "currency_sources": dict(self.currency_sources),
            "currencies": dict(self.currencies),
            "discovery_sources": dict(self.discovery_sources),
            "errors": list(self.errors),
        }

    def committed(self, inserted, updated, unchanged):
        for key, count in (("inserted", inserted), ("updated", updated), ("unchanged", unchanged)):
            self.counts[key] += count
        self.counts["persisted"] += inserted + updated + unchanged
        print(PROGRESS_PREFIX + json.dumps(self.snapshot(), ensure_ascii=False), flush=True)

    def finish(self, reason):
        if reason != "finished":
            self.error("abnormal_close", f"Spider closed: {reason}")
        if (not self.pipeline_opened or not self.pipeline_closed) and not self.counts["failed"]:
            self.error("pipeline_incomplete", "MySQL Pipeline did not open and finish successfully")
        if not self.counts["discovered"] and not self.confirmed_empty and not self.counts["failed"]:
            self.error("discovery_empty", "No confirmed product catalog was discovered")
        if self.counts["accepted"] != self.counts["persisted"] and not self.counts["failed"]:
            self.error("uncommitted_items", "Accepted products remain uncommitted")
        empty_reason = None
        if not self.counts["persisted"] and not self.counts["failed"]:
            if self.counts["filtered"]:
                empty_reason = "all_items_filtered"
            elif self.confirmed_empty:
                empty_reason = "confirmed_empty_catalog"
            else:
                self.error("unexplained_empty", "No products were persisted and no empty reason is known")
        return {
            **self.snapshot(),
            "outcome": "failed" if self.counts["failed"] else "success",
            "close_reason": reason,
            "empty_reason": empty_reason,
        }


def get_metrics(crawler):
    if not hasattr(crawler, "product_crawl_metrics"):
        crawler.product_crawl_metrics = CrawlMetrics()
    return crawler.product_crawl_metrics


class CrawlResultExtension:
    def __init__(self, crawler):
        self.crawler = crawler
        self.metrics = get_metrics(crawler)
        crawler.signals.connect(self.spider_opened, signal=signals.spider_opened)
        crawler.signals.connect(self.spider_error, signal=signals.spider_error)
        crawler.signals.connect(self.item_error, signal=signals.item_error)
        crawler.signals.connect(self.spider_closed, signal=signals.spider_closed)

    @classmethod
    def from_crawler(cls, crawler):
        return cls(crawler)

    def spider_opened(self, spider):
        from ecommerce_spider.check_runtime import runtime_report

        report = runtime_report(self.crawler.settings)
        spider.logger.info("Product crawler runtime: %s", json.dumps(report))
        self.crawler.stats.set_value("product/runtime_fingerprint", report["fingerprint"])

    def spider_error(self, failure, response, spider):
        self.metrics.error("spider_exception", failure.getErrorMessage())

    def item_error(self, item, response, spider, failure):
        if not isinstance(failure.value, PersistenceError):
            self.metrics.error("item_exception", failure.getErrorMessage())

    def spider_closed(self, spider, reason):
        result = self.metrics.finish(reason)
        for key in COUNTERS:
            self.crawler.stats.set_value(f"product/{key}", result[key])
        print(RESULT_PREFIX + json.dumps(result, ensure_ascii=False), flush=True)
