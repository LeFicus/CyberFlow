"""Parse the crawler's committed totals independently of human-readable logs."""

import json


PROGRESS_PREFIX = "CYBERFLOW_CRAWL_PROGRESS="
RESULT_PREFIX = "CYBERFLOW_CRAWL_RESULT="
COUNTERS = (
    "discovered", "fetched", "generated", "filtered", "accepted",
    "inserted", "updated", "unchanged", "persisted", "failed", "requests_failed",
)


def metrics_summary(metrics):
    return (
        f"抓取 {metrics.get('fetched', 0)}，生成 {metrics.get('generated', 0)}，"
        f"过滤 {metrics.get('filtered', 0)}，新增 {metrics.get('inserted', 0)}，"
        f"更新 {metrics.get('updated', 0)}，未变化 {metrics.get('unchanged', 0)}，"
        f"已提交 {metrics.get('persisted', 0)}，失败 {metrics.get('failed', 0)}"
    )


class ProductResultProtocol:
    def __init__(self):
        self.latest = {key: 0 for key in COUNTERS}
        self.result = None
        self.result_count = 0
        self.errors = []

    def consume(self, line):
        line = line.rstrip("\r\n")
        is_result = line.startswith(RESULT_PREFIX)
        if not is_result and not line.startswith(PROGRESS_PREFIX):
            return
        prefix = RESULT_PREFIX if is_result else PROGRESS_PREFIX
        if is_result:
            self.result_count += 1
        try:
            payload = json.loads(line[len(prefix):])
            if not isinstance(payload, dict) or type(payload.get("version")) is not int or payload["version"] != 1:
                raise ValueError("Unsupported crawl result version")
            if any(type(payload.get(key)) is not int or payload[key] < 0 for key in COUNTERS):
                raise ValueError("Invalid or missing crawl counters")
            if payload["persisted"] != sum(payload[key] for key in ("inserted", "updated", "unchanged")):
                raise ValueError("Committed totals do not match inserted/updated/unchanged")
            if payload["persisted"] > payload["accepted"] or payload["accepted"] > payload["generated"]:
                raise ValueError("Committed totals exceed accepted/generated products")
            if any(payload[key] < self.latest[key] for key in COUNTERS):
                raise ValueError("Crawl counters moved backwards")
            if self.result is not None:
                raise ValueError("Unexpected result/progress after final result")
            self.latest = payload
            if is_result:
                self.result = payload
        except (ValueError, TypeError, KeyError) as exc:
            self.errors.append(str(exc))

    def finish(self, returncode):
        if returncode != 0:
            raise RuntimeError(f"Scrapy exited {returncode}")
        if self.errors or self.result_count != 1 or self.result is None:
            detail = "; ".join(self.errors[:3]) or "缺少唯一有效的结构化爬取结果"
            raise RuntimeError(detail)
        result = self.result
        if result.get("outcome") != "success" or result["failed"] or result.get("close_reason") != "finished":
            details = result.get("errors") or []
            message = "; ".join(str(error.get("message", error)) for error in details if isinstance(error, dict))
            raise RuntimeError(message or "爬取未完整成功")
        if result["accepted"] != result["persisted"]:
            raise RuntimeError("存在未提交到 MySQL 的商品")
        if not result["persisted"] and result.get("empty_reason") not in {
            "confirmed_empty_catalog", "all_items_filtered",
        }:
            raise RuntimeError("没有商品入库且缺少明确的空结果原因")
        return result
