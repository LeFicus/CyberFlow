"""Async crawler for daily site indexing statistics."""

import asyncio
from datetime import datetime, timezone
from urllib.parse import urlsplit

import aiohttp
from loguru import logger


def normalize_domain(value: object) -> str:
    """Return a lowercase hostname without scheme, path, port, or www."""
    raw = str(value or "").strip().lower()
    if not raw:
        return ""
    parsed = urlsplit(raw if "://" in raw else f"//{raw}")
    host = (parsed.hostname or raw.split("/", 1)[0]).strip(".")
    return host[4:] if host.startswith("www.") else host


def _as_int(value: object) -> int:
    try:
        return max(0, int(float(value or 0)))
    except (TypeError, ValueError):
        return 0


def normalize_datetime(value: object) -> str | None:
    """Convert remote timestamp values to a MySQL DATETIME string.

    The platform uses human-readable placeholders such as ``未提交`` when a
    site has never been submitted.  Treat those and malformed values as
    missing data so one dirty value cannot roll back the full snapshot.
    """
    if value is None:
        return None
    if isinstance(value, datetime):
        parsed = value
    else:
        raw = str(value).strip()
        if not raw or raw.lower() in {
            "none", "null", "n/a", "na", "-", "--", "未提交", "暂无", "无",
        }:
            return None
        if raw.isdigit() and len(raw) >= 10:
            try:
                timestamp = int(raw)
                if len(raw) >= 13:
                    timestamp /= 1000
                parsed = datetime.fromtimestamp(timestamp, tz=timezone.utc)
            except (OverflowError, OSError, ValueError):
                return None
        else:
            candidate = raw.replace("/", "-").replace("T", " ").removesuffix("Z")
            try:
                parsed = datetime.fromisoformat(candidate)
            except ValueError:
                return None
    if parsed.tzinfo is not None:
        parsed = parsed.astimezone(timezone.utc).replace(tzinfo=None)
    return parsed.strftime("%Y-%m-%d %H:%M:%S")


class AsyncSiteIndexCrawler:
    """Fetch Google index and product counts from the configured admin API."""

    def __init__(self, base_url: str, username: str, password: str,
                 verify_ssl: bool = True, page_size: int = 100):
        self.base_url = str(base_url or "").rstrip("/")
        self.username = str(username or "")
        self.password = str(password or "")
        self.verify_ssl = verify_ssl
        self.page_size = max(1, page_size)
        self.client: aiohttp.ClientSession | None = None
        self.token: str | None = None

    async def login(self) -> None:
        async with self.client.post(
            f"{self.base_url}/adminapi/login",
            json={"username": self.username, "password": self.password},
        ) as response:
            response.raise_for_status()
            data = await response.json()
            self.token = data.get("data", {}).get("access_token") or data.get("access_token")
            if not self.token:
                raise RuntimeError("Admin API login response did not contain access_token")

    def _items(self, data: dict) -> list[dict]:
        payload = data.get("data", {})
        if isinstance(payload, dict):
            for key in ("items", "data", "list", "rows", "records"):
                if isinstance(payload.get(key), list):
                    return payload[key]
        for key in ("items", "data", "list", "rows", "records"):
            if isinstance(data.get(key), list):
                return data[key]
        return []

    async def fetch(self) -> list[dict]:
        records: dict[str, dict] = {}
        page = 1
        while True:
            async with self.client.get(
                f"{self.base_url}/adminapi/statistics/theme/list",
                params={"page": page, "pageSize": self.page_size, "theme_id": "0"},
                headers={"Authorization": f"Bearer {self.token}"},
            ) as response:
                response.raise_for_status()
                items = self._items(await response.json())
            if not items:
                break
            for item in items:
                # The statistics endpoint exposes built and historical site
                # rows through the same shape.  Only status=2 is a currently
                # built site and may enter the local current-site snapshot.
                if str(item.get("site_status") or "").strip() != "2":
                    continue
                domain = normalize_domain(item.get("site_domain") or item.get("domain"))
                if domain:
                    server_info = item.get("server_info") if isinstance(item.get("server_info"), dict) else {}
                    admin_info = item.get("admin_info") if isinstance(item.get("admin_info"), dict) else {}
                    records[domain] = {
                        "site_domain": domain,
                        "index_count": _as_int(item.get("google_count")),
                        "product_count": _as_int(item.get("total_product")),
                        "server_name": server_info.get("server_name") or item.get("site_fwq_name") or item.get("fwq_name") or "",
                        "server_ip": server_info.get("server_ip") or "",
                        "builder_username": admin_info.get("username") or "",
                        "admin_name": admin_info.get("realname") or "",
                        "user_group": str(admin_info.get("realname") or "").strip()[:1].upper(),
                        "theme_name": item.get("theme_name") or "",
                        "last_submitted_at": normalize_datetime(item.get("submit_time")),
                    }
            if len(items) < self.page_size:
                break
            page += 1
            await asyncio.sleep(0.1)
        logger.info(f"Fetched indexing statistics for {len(records)} domains")
        return list(records.values())

    async def run(self) -> list[dict]:
        if not self.base_url or not self.username or not self.password:
            raise ValueError("Admin API baseUrl, username, and password are required")
        self.client = aiohttp.ClientSession(
            connector=aiohttp.TCPConnector(ssl=self.verify_ssl),
            timeout=aiohttp.ClientTimeout(total=60),
        )
        try:
            await self.login()
            return await self.fetch()
        finally:
            await self.client.close()
