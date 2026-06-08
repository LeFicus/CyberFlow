import asyncio
import aiohttp
from loguru import logger


class AsyncOrderCrawler:
    """Async version of OrderCrawler — fetches orders from payment platform."""

    BASE_URL = "https://c4partypay.com"
    PAGE_SIZE = 100

    def __init__(self):
        self.client: aiohttp.ClientSession | None = None
        self.token: str | None = None

    async def login(self, username: str, password: str) -> str:
        async with self.client.post(
            f"{self.BASE_URL}/platformapi/login/account",
            json={"username": username, "password": password},
        ) as resp:
            data = await resp.json()
            self.token = data.get("data", {}).get("access_token") or data.get("access_token")
            if not self.token:
                raise Exception(f"Order login failed: {data}")
            logger.success("🔓 Order crawler logged in")
            return self.token

    def _headers(self):
        return {"Authorization": f"Bearer {self.token}"}

    async def fetch_orders(self, since_order_id: str = "0") -> tuple[list[dict], str]:
        """Fetch orders incrementally by order_id cursor."""
        results = []
        page = 1
        current_max_id = int(since_order_id)

        while True:
            url = (
                f"{self.BASE_URL}/platformapi/pay.pay_order/lists"
                f"?tenant_id=95&page_size={self.PAGE_SIZE}&page={page}"
            )
            async with self.client.get(url, headers=self._headers()) as resp:
                data = await resp.json()
                items = data.get("data", {}).get("items", []) or data.get("items", [])
                if not items:
                    break

                for item in items:
                    order_id = item.get("id", 0)
                    if order_id <= int(since_order_id):
                        continue
                    channel = str(item.get("channel", "")).lower()
                    if any(t in channel for t in ["测试", "ig-3", "test-mutiwp"]):
                        continue
                    card_no = str(item.get("card_no", ""))
                    if card_no in ["400000******0000", "411111******1111"]:
                        continue

                    current_max_id = max(current_max_id, order_id)
                    results.append(item)

                if len(items) < self.PAGE_SIZE:
                    break
                page += 1
                await asyncio.sleep(0.1)

        logger.info(f"📦 Fetched {len(results)} orders (since_id={since_order_id}, new_max={current_max_id})")
        return results, str(current_max_id)

    async def run(self, username: str, password: str, since_order_id: str = "0") -> tuple[list[dict], str]:
        """Execute order crawl. Returns (records, new_max_order_id)."""
        self.client = aiohttp.ClientSession(
            connector=aiohttp.TCPConnector(verify_ssl=False),
            timeout=aiohttp.ClientTimeout(total=30),
        )
        try:
            await self.login(username, password)
            records, new_cursor = await self.fetch_orders(since_order_id)
            return records, new_cursor
        finally:
            await self.client.close()
