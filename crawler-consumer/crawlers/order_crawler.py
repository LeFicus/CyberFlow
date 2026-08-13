"""
订单爬虫执行器 —— 通过支付平台 API 异步获取订单数据。

本模块使用 aiohttp 实现异步 HTTP 客户端，步骤为：
1. 登录支付平台获取 JWT Bearer Token
2. 分页拉取订单列表，按 order_id 增量跳过已爬取的订单
3. 过滤测试订单（测试渠道、测试卡号）
4. 返回订单列表和新的最大 order_id 游标

支持增量爬取，通过 since_order_id 参数只获取新增订单。
"""

import asyncio
import aiohttp
from loguru import logger


class AsyncOrderCrawler:
    """异步订单爬虫 —— 从支付平台 API 获取订单数据。

    通过 HTTP API 登录支付平台，按 order_id 增量分页拉取订单，
    自动过滤测试数据，返回结构化订单列表和最新游标值。
    """

    def __init__(
        self,
        base_url: str,
        verify_ssl: bool = True,
        page_size: int = 100,
        filter_card_number_exclude: list[str] | None = None,
    ):
        """初始化 AsyncOrderCrawler，客户端和令牌初始为空。"""
        self.base_url = base_url.rstrip("/")
        self.verify_ssl = verify_ssl
        self.page_size = page_size
        self.filter_card_number_exclude = filter_card_number_exclude or [
            "400000******0000",
            "411111******1111",
            "411111111111",
        ]
        self.client: aiohttp.ClientSession | None = None
        self.token: str | None = None
        self.api_prefix = "tenantapi"

    async def login(self, username: str, password: str) -> str:
        """登录支付平台，获取 JWT Bearer Token。

        向支付平台登录接口发送 POST 请求，
        使用用户名/密码认证，从响应中提取 access_token。

        参数:
            username (str): 支付平台用户名
            password (str): 支付平台密码

        返回:
            str: JWT 访问令牌

        异常:
            Exception: 登录失败时抛出，包含响应体内容
        """
        payload = {"account": username, "password": password, "googleCode": "", "terminal": 1}
        last_response = None

        for api_prefix in ("tenantapi", "platformapi"):
            async with self.client.post(f"{self.base_url}/{api_prefix}/login/account", json=payload) as resp:
                data = await resp.json()
                last_response = data
                token = self._extract_token(data)
                if token:
                    self.api_prefix = api_prefix
                    self.token = token
                    logger.success(f"🔓 Order crawler logged in via {api_prefix}")
                    return self.token

            if not self._should_try_next_login(last_response):
                break

        raise Exception(f"Order login failed: {last_response}")

    def _extract_token(self, data) -> str | None:
        if not isinstance(data, dict):
            return None
        payload = data.get("data", {})
        if isinstance(payload, dict):
            return (
                payload.get("token")
                or payload.get("access_token")
                or data.get("token")
                or data.get("access_token")
            )
        return data.get("token") or data.get("access_token")

    def _should_try_next_login(self, data) -> bool:
        if not isinstance(data, dict):
            return False
        msg = str(data.get("msg", "")).lower()
        return "account is not exists" in msg or "account is not exist" in msg

    def _headers(self):
        """构建带 Bearer Token 的请求头字典。

        返回:
            dict: 包含 Authorization 头的字典
        """
        return {"Authorization": f"Bearer {self.token}", "token": self.token}

    def _list_items(self, data) -> list[dict]:
        """兼容支付 API 的不同分页返回结构。"""
        if isinstance(data, list):
            return [item for item in data if isinstance(item, dict)]
        if not isinstance(data, dict):
            return []

        payload = data.get("data", {})
        if isinstance(payload, list):
            return [item for item in payload if isinstance(item, dict)]
        if isinstance(payload, dict):
            for key in ("lists", "items", "data", "list", "rows", "records"):
                value = payload.get(key)
                if isinstance(value, list):
                    return [item for item in value if isinstance(item, dict)]

        for key in ("lists", "items", "data", "list", "rows", "records"):
            value = data.get(key)
            if isinstance(value, list):
                return [item for item in value if isinstance(item, dict)]

        return []

    def _has_next_page(self, data, item_count: int, page: int) -> bool:
        if isinstance(data, dict):
            payload = data.get("data", {})
            if isinstance(payload, dict):
                if isinstance(payload.get("has_more"), bool):
                    return payload["has_more"]
                current = payload.get("current_page", page)
                last = payload.get("last_page")
                if isinstance(last, int):
                    return int(current) < last
        return item_count >= self.page_size

    def _order_id(self, item: dict) -> int:
        value = item.get("id") or item.get("order_id") or 0
        try:
            return int(value)
        except (TypeError, ValueError):
            return 0

    async def fetch_orders(self, since_order_id: str = "0") -> tuple[list[dict], str]:
        """增量分页获取订单列表。

        按 order_id 递增顺序拉取，跳过 order_id <= since_order_id 的已处理订单。
        过滤规则：
        - 渠道名包含 "测试"、"ig-3"、"test-mutiwp" 的测试订单
        - 卡号为 "400000******0000" 或 "411111******1111" 的测试卡

        参数:
            since_order_id (str): 上次爬取的最大订单 ID（默认 "0"）

        返回:
            tuple[list[dict], str]:
                - list[dict]: 过滤后的新订单记录列表
                - str: 新的最大订单 ID（作为下次增量爬取的起点）
        """
        results = []
        page = 1
        current_max_id = int(since_order_id)
        since_id = int(since_order_id)
        skipped_by_cursor = 0
        skipped_by_card = 0
        skipped_by_channel = 0

        logger.info(
            f"🧭 Order crawl request: base={self.base_url}, api={self.api_prefix}, "
            f"page_size={self.page_size}, since_id={since_order_id}"
        )

        while True:
            url = f"{self.base_url}/{self.api_prefix}/pay.pay_order/lists"
            params = {
                "page_no": page,
                "page_size": self.page_size,
            }
            async with self.client.get(url, headers=self._headers(), params=params) as resp:
                data = await resp.json()
                items = self._list_items(data)
                code = data.get("code") if isinstance(data, dict) else None
                msg = data.get("msg") if isinstance(data, dict) else None
                payload = data.get("data") if isinstance(data, dict) else None
                payload_keys = list(payload.keys()) if isinstance(payload, dict) else type(payload).__name__
                logger.info(
                    f"📥 Order page {page} response: http={resp.status}, code={code}, "
                    f"msg={msg!r}, data={payload_keys}, items={len(items)}"
                )
                if not items and msg:
                    logger.warning(f"⚠️ Order page {page} returned no items: {msg}")
                if not items:
                    break

                new_count = 0
                for item in items:
                    order_id = self._order_id(item)
                    # 跳过已处理的订单（order_id <= 上次爬取的最大 ID）
                    if order_id <= since_id:
                        skipped_by_cursor += 1
                        continue

                    # 跳过测试渠道的订单
                    channel = str(item.get("pay_channel_name") or item.get("channel", "")).lower()
                    if any(t in channel for t in ["测试", "ig-3", "test-mutiwp"]):
                        skipped_by_channel += 1
                        continue

                    # 跳过测试卡号
                    card_no = str(item.get("cardNumber") or item.get("card_no", ""))
                    if card_no in self.filter_card_number_exclude:
                        skipped_by_card += 1
                        continue

                    current_max_id = max(current_max_id, order_id)
                    new_count += 1
                    results.append(item)

                if since_id > 0 and new_count == 0:
                    break
                if page == 1 or page % 10 == 0:
                    logger.info(f"📄 Order page {page}: {len(items)} items, accumulated={len(results)}")
                if not self._has_next_page(data, len(items), page):
                    break
                page += 1
                await asyncio.sleep(0.1)  # 温和的请求间隔，避免触发限流

        logger.info(
            f"📦 Fetched {len(results)} orders (since_id={since_order_id}, new_max={current_max_id}, "
            f"skipped_cursor={skipped_by_cursor}, skipped_channel={skipped_by_channel}, "
            f"skipped_card={skipped_by_card})"
        )
        return results, str(current_max_id)

    async def run(self, username: str, password: str, since_order_id: str = "0") -> tuple[list[dict], str]:
        """执行完整的订单爬取流程。

        创建 aiohttp Session（禁用 SSL 验证，30 秒超时），
        依次执行登录、增量拉取订单。

        参数:
            username (str): 支付平台用户名
            password (str): 支付平台密码
            since_order_id (str): 增量爬取起点订单 ID（默认 "0"）

        返回:
            tuple[list[dict], str]: (订单记录列表, 新的最大订单 ID 游标)
        """
        self.client = aiohttp.ClientSession(
            connector=aiohttp.TCPConnector(ssl=self.verify_ssl),
            timeout=aiohttp.ClientTimeout(total=30),
        )
        try:
            await self.login(username, password)
            records, new_cursor = await self.fetch_orders(since_order_id)
            return records, new_cursor
        finally:
            await self.client.close()
