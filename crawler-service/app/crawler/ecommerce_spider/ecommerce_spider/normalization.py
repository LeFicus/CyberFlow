"""Shared product normalization rules used by every storefront spider."""

import json
import html
import re
import math
from pathlib import Path
from urllib.parse import unquote, urlparse

from scrapy.exceptions import DropItem


_RATES = None
_FILE_NAME_RE = re.compile(r"\.[a-zA-Z0-9]{1,8}\)?$", re.IGNORECASE)
_SHOP_BY_RE = re.compile(r"^shop\s+by\b", re.IGNORECASE)
_CATEGORY_NOISE = {
    "all",
    "all products",
    "clearance",
    "featured",
    "latest",
    "new",
    "new arrivals",
    "new in",
    "bestseller",
    "best sellers",
    "bestsellers",
    "popular",
    "sale",
    "sales",
    "shop all",
    "trending",
}
class UnknownCurrencyError(ValueError):
    pass


def normalize_name(value: object) -> str:
    """Normalize and validate a product title before it reaches MySQL."""
    name = re.sub(r"\s+", " ", str(value or "")).strip()
    if len(name) < 5:
        raise DropItem(f"丢弃商品：标题少于 5 个字符 ({name!r})")
    if name.isdigit():
        raise DropItem(f"丢弃商品：标题为纯数字 ({name!r})")
    if _FILE_NAME_RE.search(name):
        raise DropItem(f"丢弃商品：标题疑似文件名 ({name!r})")
    return name


def has_content(value: object) -> bool:
    """Return whether a scraped text/URL field contains meaningful content."""
    if value is None or value == "":
        return False
    if isinstance(value, (list, tuple, set)):
        return any(has_content(item) for item in value)
    text = html.unescape(str(value))
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"\s+", "", text)
    return bool(text)


def product_dedupe_key(domain: object, name: object, image: object) -> str:
    """Build a non-unique lookup hint; domain/SKU remains the product identity."""
    raw_domain = str(domain or "").strip().lower()
    parsed_domain = urlparse(raw_domain if "://" in raw_domain else f"//{raw_domain}")
    domain_key = (parsed_domain.netloc or parsed_domain.path).split("/")[0].split(":")[0]
    if domain_key.startswith("www."):
        domain_key = domain_key[4:]
    normalized_name = re.sub(r"[^\w\u4e00-\u9fff]+", "", str(name or "").casefold())
    parsed_image = urlparse(str(image or "").strip())
    image_key = unquote(parsed_image.path or parsed_image.netloc).casefold()
    return "|".join((domain_key[:120], normalized_name[:300], image_key[:300]))


def normalize_category(value: object, fallback: str = "Others") -> str:
    """Keep at most one category child and remove navigation-like labels."""
    raw_segments = re.split(r"\|\|\||[,/]+", str(value or ""))
    cleaned = []
    for segment in raw_segments:
        category = re.sub(r"\s+", " ", segment).strip()
        normalized_label = category.casefold()
        if (
            not category
            or _SHOP_BY_RE.match(category)
            or normalized_label in _CATEGORY_NOISE
            or len(category) > 40
            or re.search(r"[\d_]", category)
        ):
            continue
        # Uppercase the first ASCII letter while preserving Chinese/local text.
        match = re.search(r"[A-Za-z]", category)
        if match:
            index = match.start()
            category = category[:index] + category[index].upper() + category[index + 1:]
        if category not in cleaned:
            cleaned.append(category)
        if len(cleaned) == 2:
            break
    result = "|||".join(cleaned)
    return result[:160] if result else fallback


def currency_to_usd(value: object, currency: object = "USD") -> float:
    """Convert a source price into USD using the bundled rates table."""
    try:
        number = float(re.sub(r"[^\d.,-]", "", str(value or "")).replace(",", ""))
    except (TypeError, ValueError):
        return 0.0
    if not math.isfinite(number):
        return 0.0
    code = str(currency or "").strip().upper()
    rates = load_exchange_rates()
    if code not in rates or not math.isfinite(float(rates[code])) or float(rates[code]) <= 0:
        raise UnknownCurrencyError(f"Unknown currency or missing USD rate: {code or 'missing'}")
    return round(number * float(rates[code]), 2)


def load_exchange_rates() -> dict:
    global _RATES
    if _RATES is not None:
        return _RATES
    path = Path(__file__).with_name("exchange_rates.json")
    if not path.exists():
        path = path.parent / "spiders" / "exchange_rates.json"
    try:
        _RATES = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        _RATES = {"USD": 1.0}
    return _RATES
