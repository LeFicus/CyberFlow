"""Build stable order identity groups from recipient email or shipping address."""

from __future__ import annotations

import hashlib
import json
import re
from collections import defaultdict
from datetime import date, datetime
from typing import Any, Iterable


ADDRESS_FIELDS = ("country", "state", "city", "address", "zipCode")


def parse_shipping_address(value: Any) -> dict[str, Any]:
    """Return the platform shippingAddress value as a JSON object."""
    if isinstance(value, dict):
        return value
    if isinstance(value, str) and value.strip():
        try:
            parsed = json.loads(value)
        except (TypeError, ValueError, json.JSONDecodeError):
            return {}
        return parsed if isinstance(parsed, dict) else {}
    return {}


def shipping_address_json(value: Any) -> str:
    """Serialize a shipping address without double-encoding JSON strings."""
    return json.dumps(parse_shipping_address(value), ensure_ascii=False, separators=(",", ":"))


def normalize_email(value: Any) -> str:
    return str(value or "").strip().casefold()


def normalize_address(value: Any) -> str:
    """Normalize the delivery location while excluding recipient PII such as name/phone."""
    address = parse_shipping_address(value)
    parts = [_compact(address.get(field)) for field in ADDRESS_FIELDS]
    # Country alone is not an identity. A usable address needs a street plus
    # either a postal code or city to avoid merging unrelated incomplete rows.
    if not parts[3] or not (parts[2] or parts[4]):
        return ""
    return "\x1f".join(parts)


def build_dedupe_keys(rows: Iterable[dict[str, Any]]) -> dict[tuple[str, int], str]:
    """Group orders when email OR address matches within day/group/site.

    The relationship is transitive: an order sharing an email with one row and
    an address with another joins all three into one customer/order identity.
    """
    records = list(rows)
    parents = list(range(len(records)))
    identifiers: list[tuple[tuple[str, str, str], str, str]] = []
    seen: dict[tuple[tuple[str, str, str], str, str], int] = {}

    def find(index: int) -> int:
        while parents[index] != index:
            parents[index] = parents[parents[index]]
            index = parents[index]
        return index

    def union(left: int, right: int) -> None:
        left_root = find(left)
        right_root = find(right)
        if left_root != right_root:
            parents[right_root] = left_root

    for index, row in enumerate(records):
        scope = (_day(row.get("create_time")), _group(row.get("user_group")), _host(row.get("product_host")))
        email = normalize_email(row.get("shipping_email"))
        address = normalize_address(row.get("shipping_address"))
        identifiers.append((scope, email, address))
        for kind, value in (("email", email), ("address", address)):
            if not value:
                continue
            identity = (scope, kind, value)
            previous = seen.get(identity)
            if previous is None:
                seen[identity] = index
            else:
                union(index, previous)

    members: dict[int, list[int]] = defaultdict(list)
    for index in range(len(records)):
        members[find(index)].append(index)

    result: dict[tuple[str, int], str] = {}
    for indexes in members.values():
        scope = identifiers[indexes[0]][0]
        tokens: set[str] = set()
        for index in indexes:
            _, email, address = identifiers[index]
            if email:
                tokens.add(f"email:{email}")
            if address:
                tokens.add(f"address:{address}")
        if not tokens:
            row = records[indexes[0]]
            tokens.add(f"order:{_group(row.get('user_group'))}:{row.get('id')}")
        payload = "\x1e".join((*scope, *sorted(tokens)))
        key = hashlib.sha256(payload.encode("utf-8")).hexdigest()
        for index in indexes:
            row = records[index]
            result[(_group(row.get("user_group")), int(row["id"]))] = key
    return result


def _compact(value: Any) -> str:
    return re.sub(r"[\W_]+", "", str(value or "").casefold(), flags=re.UNICODE)


def _day(value: Any) -> str:
    if isinstance(value, (datetime, date)):
        return value.isoformat()[:10]
    return str(value or "")[:10]


def _group(value: Any) -> str:
    return str(value or "").strip().upper()


def _host(value: Any) -> str:
    host = str(value or "").strip().casefold()
    return host[4:] if host.startswith("www.") else host
