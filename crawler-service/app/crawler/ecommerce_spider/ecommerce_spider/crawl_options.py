"""Validated per-run product filters and evidence-based currency resolution."""

from dataclasses import dataclass
import json
import math
import re
from urllib.parse import urlparse


@dataclass(frozen=True)
class CrawlOptions:
    max_product_price_usd: float | None = None
    require_description: bool = False
    require_image: bool = True
    currency: str = ""

    @classmethod
    def from_json(cls, value=None):
        values = json.loads(value) if isinstance(value, str) and value else ({} if value is None else value)
        if not isinstance(values, dict) or set(values) - set(cls.__dataclass_fields__):
            raise ValueError("Unsupported crawl_options keys")
        values = dict(values)
        for key in ("require_description", "require_image"):
            if key in values and type(values[key]) is not bool:
                raise ValueError(f"{key} must be boolean")
        maximum = values.get("max_product_price_usd")
        if maximum is not None:
            if isinstance(maximum, bool) or not isinstance(maximum, (int, float)):
                raise ValueError("max_product_price_usd must be numeric or null")
            if not math.isfinite(maximum) or maximum <= 0:
                raise ValueError("max_product_price_usd must be finite and positive")
        if "currency" in values:
            if values["currency"] is not None and not isinstance(values["currency"], str):
                raise ValueError("currency must be a string or null")
            currency = str(values["currency"] or "").strip().upper()
            if currency and not re.fullmatch(r"[A-Z]{3}", currency):
                raise ValueError("currency must be an ISO currency code or empty")
            values["currency"] = currency
        return cls(**values)


def currency_code(value):
    if isinstance(value, dict):
        value = value.get("code") or value.get("currency_code") or value.get("currencyCode")
    code = str(value or "").strip().upper()
    return code if re.fullmatch(r"[A-Z]{3}", code) else ""


def data_currency(data):
    if not isinstance(data, dict):
        return ""
    for key in ("active_currency_code", "currency_code", "currencyCode", "priceCurrency", "currency", "active_currency"):
        code = currency_code(data.get(key))
        if code:
            return code
    for key in ("currency_selector", "store", "settings"):
        code = data_currency(data.get(key))
        if code:
            return code
    return ""


def page_currency(response):
    values = response.xpath(
        "//meta[@itemprop='priceCurrency' or @property='product:price:currency' or @property='og:price:currency']/@content | "
        "//*[@itemprop='priceCurrency']/text() | "
        "//main/@data-currency-code | //body/@data-currency-code | "
        "//*[@data-currency-code and (@aria-current='true' or @aria-selected='true')]/@data-currency-code"
    ).getall()
    for value in values:
        code = currency_code(value)
        if code:
            return code
    for pattern in (
        r'''["']?active_currency_code["']?\s*:\s*["']([A-Za-z]{3})["']''',
        r'''\bAll prices (?:are )?in\s+(?:<[^>]+>\s*)*([A-Z]{3})\b''',
    ):
        match = re.search(pattern, response.text, re.IGNORECASE)
        if match:
            return match.group(1).upper()
    return ""


def resolve_currency(spider, response=None, preferred="", bcdata=None, secondary=""):
    options = getattr(spider, "crawl_options", CrawlOptions())
    candidates = [(preferred, "price_payload"), (data_currency(bcdata), "bcdata")]
    if response is not None:
        candidates.append((page_currency(response), "page"))
    candidates.extend(((secondary, "product_metadata"), (options.currency, "task_config")))
    settings = getattr(getattr(spider, "crawler", None), "settings", {})
    domain_map = settings.get("PRODUCT_DOMAIN_CURRENCIES", {})
    if isinstance(domain_map, str):
        domain_map = json.loads(domain_map)
    if not isinstance(domain_map, dict):
        raise ValueError("PRODUCT_DOMAIN_CURRENCIES must be a domain/currency object")
    hostname = (urlparse(spider.domain).hostname or "").lower()
    candidates.append((domain_map.get(hostname) or domain_map.get(hostname.removeprefix("www.")), "domain_config"))
    candidates.append((getattr(spider, "selectors", {}).get("currency"), "site_template"))
    for value, source in candidates:
        code = currency_code(value)
        if code:
            return code, source
    return "", "unknown"
