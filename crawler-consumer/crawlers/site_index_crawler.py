"""Async crawler for daily site indexing statistics."""

import asyncio
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
                domain = normalize_domain(item.get("site_domain") or item.get("domain"))
                if domain:
                    records[domain] = {
                        "site_domain": domain,
                        "index_count": _as_int(item.get("google_count")),
                        "product_count": _as_int(item.get("total_product")),
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
