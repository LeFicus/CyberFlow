import asyncio
import aiohttp
from loguru import logger


class AsyncSiteCrawler:
    """Async version of SiteCrawler — fetches site info from management platform."""

    BASE_URL = "http://104.233.194.18"
    PAGE_SIZE = 100

    def __init__(self, username: str, password: str):
        self.username = username
        self.password = password
        self.client: aiohttp.ClientSession | None = None
        self.token: str | None = None

    async def login(self) -> str:
        async with self.client.post(
            f"{self.BASE_URL}/adminapi/login",
            json={"username": self.username, "password": self.password},
        ) as resp:
            data = await resp.json()
            self.token = data.get("data", {}).get("access_token") or data.get("access_token")
            if not self.token:
                raise Exception(f"Login failed: {data}")
            logger.success("🔓 Site crawler logged in")
            return self.token

    def _headers(self):
        return {"Authorization": f"Bearer {self.token}"}

    async def fetch_site_map(self) -> dict:
        """Build domain → {theme_name, product_category} mapping."""
        site_map = {}
        page = 1
        while True:
            url = f"{self.BASE_URL}/adminapi/site/site/list?page={page}&page_size={self.PAGE_SIZE}"
            async with self.client.get(url, headers=self._headers()) as resp:
                data = await resp.json()
                items = data.get("data", {}).get("items", []) or data.get("items", [])
                if not items:
                    break
                for item in items:
                    domain = item.get("site_domain", "").strip()
                    if domain:
                        site_map[domain] = {
                            "theme_name": item.get("theme_name", ""),
                            "product_category": item.get("product_category", ""),
                            "admin_name": item.get("admin_name", ""),
                        }
                if len(items) < self.PAGE_SIZE:
                    break
                page += 1
        logger.info(f"📋 Site map built: {len(site_map)} domains")
        return site_map

    async def fetch_domains(self, site_map: dict, since: str | None = None) -> list[dict]:
        """Fetch domain list, optionally incremental (since = last_updated_at)."""
        results = []
        page = 1
        while True:
            url = f"{self.BASE_URL}/adminapi/domain/domain/list?page={page}&page_size={self.PAGE_SIZE}"
            if since:
                url += f"&updated_since={since}"
            async with self.client.get(url, headers=self._headers()) as resp:
                data = await resp.json()
                items = data.get("data", {}).get("items", []) or data.get("items", [])
                if not items:
                    break
                for item in items:
                    domain = item.get("domain", "").strip()
                    admin_name = item.get("admin_name", "")
                    status = item.get("status", 0)
                    if admin_name == "super" or status == 2:
                        continue
                    record = {
                        "site_domain": domain,
                        "admin_name": admin_name,
                        "username": self.username,
                    }
                    if domain in site_map:
                        record["theme_name"] = site_map[domain]["theme_name"]
                        record["product_category"] = site_map[domain]["product_category"]
                    results.append(record)
                if len(items) < self.PAGE_SIZE:
                    break
                page += 1
                await asyncio.sleep(0.1)
        logger.info(f"📦 Fetched {len(results)} domains (since={since})")
        return results

    async def run(self, since: str | None = None) -> tuple[list[dict], str | None]:
        """Execute site crawl. Returns (records, new_cursor_value)."""
        self.client = aiohttp.ClientSession(
            connector=aiohttp.TCPConnector(verify_ssl=False),
            timeout=aiohttp.ClientTimeout(total=30),
        )
        try:
            await self.login()
            site_map = await self.fetch_site_map()
            records = await self.fetch_domains(site_map, since)
            return records, None
        finally:
            await self.client.close()
