"""
站点爬虫执行器 —— 通过管理平台 API 异步获取站点域名列表与站点映射信息。

本模块使用 aiohttp 实现异步 HTTP 客户端，步骤为：
1. 登录管理平台获取 JWT Bearer Token
2. 分页拉取站点映射（domain → theme_name, product_category）
3. 分页拉取域名列表，合并站点映射信息，过滤超级管理员和已禁用域名
4. 返回域名记录列表供 SiteConsumer 写入数据库

支持增量爬取（通过 since 参数过滤更新日期）。
"""

import asyncio
import aiohttp
from loguru import logger


class AsyncSiteCrawler:
    """异步站点爬虫 —— 从管理平台 API 获取站点域名和详情。

    通过 HTTP API 登录管理平台，分页获取站点映射和域名列表，
        过滤超级管理员域名，并可按策略只保留已建站域名，返回结构化的站点记录。

    参数:
        username (str): 管理平台用户名
        password (str): 管理平台密码
    """

    def __init__(
        self,
        base_url: str,
        username: str,
        password: str,
        verify_ssl: bool = True,
        page_size: int = 100,
        skip_site_check: bool = True,
        fetch_admin_login_url: bool = False,
        filter_built_only: bool = False,
    ):
        """初始化 AsyncSiteCrawler。

        参数:
            username (str): 管理平台登录用户名
            password (str): 管理平台登录密码
        """
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.password = password
        self.verify_ssl = verify_ssl
        self.page_size = page_size
        self.skip_site_check = skip_site_check
        self.fetch_admin_login_url = fetch_admin_login_url
        self.filter_built_only = filter_built_only
        self.client: aiohttp.ClientSession | None = None
        self.token: str | None = None

    async def login(self) -> str:
        """登录管理平台，获取 JWT Bearer Token。

        向 /adminapi/login 发送 POST 请求，使用用户名/密码认证，
        从响应中提取 access_token。

        返回:
            str: JWT 访问令牌

        异常:
            Exception: 登录失败时抛出，包含响应体内容
        """
        async with self.client.post(
            f"{self.base_url}/adminapi/login",
            json={"username": self.username, "password": self.password},
        ) as resp:
            data = await resp.json()
            self.token = data.get("data", {}).get("access_token") or data.get("access_token")
            if not self.token:
                raise Exception(f"Login failed: {data}")
            logger.success("🔓 Site crawler logged in")
            return self.token

    def _headers(self):
        """构建带 Bearer Token 的请求头字典。

        返回:
            dict: 包含 Authorization 头的字典
        """
        return {"Authorization": f"Bearer {self.token}"}

    def _list_items(self, data: dict) -> list[dict]:
        """Return list payloads from the admin API's supported page shapes."""
        payload = data.get("data", {})
        if isinstance(payload, dict):
            for key in ("items", "data", "list", "rows", "records"):
                value = payload.get(key)
                if isinstance(value, list):
                    return value
        for key in ("items", "data", "list", "rows", "records"):
            value = data.get(key)
            if isinstance(value, list):
                return value
        return []

    def _total_count(self, data: dict) -> int | None:
        payload = data.get("data", {})
        if isinstance(payload, dict) and isinstance(payload.get("total"), int):
            return payload["total"]
        return data.get("total") if isinstance(data.get("total"), int) else None

    def _has_next_page(self, data: dict, item_count: int, page: int) -> bool:
        payload = data.get("data", {})
        if isinstance(payload, dict):
            if isinstance(payload.get("has_more"), bool):
                return payload["has_more"]
            current = payload.get("current_page", page)
            last = payload.get("last_page")
            if isinstance(current, int) and isinstance(last, int):
                return current < last
        return item_count >= self.page_size

    async def fetch_site_map(self) -> dict:
        """获取站点映射：domain → {theme_name, product_category, admin_name}。

        分页拉取 /adminapi/site/site/list 接口，构建域名到站点详情的映射字典，
        供后续域名列表查询时关联使用。

        返回:
            dict: 以 domain 为键，站点详情为值的映射字典
        """
        site_map = {}
        page = 1
        while True:
            url = f"{self.base_url}/adminapi/site/site/list?page={page}&pageSize={self.page_size}"
            async with self.client.get(url, headers=self._headers()) as resp:
                data = await resp.json()
                items = self._list_items(data)
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
                # 若当前页记录数少于页大小，说明已到最后一页
                if not self._has_next_page(data, len(items), page):
                    break
                page += 1
        logger.info(f"📋 Site map built: {len(site_map)} domains")
        return site_map

    async def fetch_domains(self, site_map: dict, since: str | None = None) -> list[dict]:
        """分页获取域名列表，支持增量查询。

        拉取 /adminapi/domain/domain/list 接口，过滤以下域名：
        - admin_name 为 "super" 的超级管理员域名
        - filter_built_only 开启时，只保留 status=2 的已建站域名

        若 site_map 中存在对应域名的信息，则合并 theme_name 和 product_category。

        参数:
            site_map (dict): 域名到站点详情的映射字典
            since (str | None): 增量查询起点（ISO 8601 格式），None 表示全量

        返回:
            list[dict]: 结构化站点记录列表，每条含 site_domain、admin_name、
                       username、theme_name、product_category
        """
        results = []
        page = 1
        while True:
            url = f"{self.base_url}/adminapi/domain/domain/list?page={page}&pageSize={self.page_size}"
            if since:
                url += f"&updated_since={since}"
            async with self.client.get(url, headers=self._headers()) as resp:
                data = await resp.json()
                items = self._list_items(data)
                if page == 1:
                    logger.info(f"📦 Remote domain total: {self._total_count(data)}")
                if not items:
                    break
                for item in items:
                    domain = item.get("domain", "").strip()
                    admin_name = item.get("admin_name", "")
                    status = item.get("status", 0)
                    # 跳过超级管理员；filter_built_only 开启时只保留已建站(status=2)
                    if admin_name == "super":
                        continue
                    if self.filter_built_only and status != 2:
                        continue
                    record = {
                        "site_domain": domain,
                        "admin_name": admin_name,
                        "username": self.username,
                    }
                    # 从站点映射中合并主题和分类信息
                    if domain in site_map:
                        record["theme_name"] = site_map[domain]["theme_name"]
                        record["product_category"] = site_map[domain]["product_category"]
                    results.append(record)
                if not self._has_next_page(data, len(items), page):
                    break
                page += 1
                await asyncio.sleep(0.1)  # 温和的请求间隔，避免触发限流
        logger.info(f"📦 Fetched {len(results)} domains (since={since})")
        return results

    async def run(self, since: str | None = None) -> tuple[list[dict], str | None]:
        """执行完整的站点爬取流程。

        创建 aiohttp Session（禁用 SSL 验证，30 秒超时），
        依次执行登录、获取站点映射、获取域名列表。

        参数:
            since (str | None): 增量查询时间戳，None 表示全量爬取

        返回:
            tuple[list[dict], str | None]: (站点记录列表, 新游标值)
                - 站点记录列表在 SiteConsumer 中用于写入数据库
                - 新游标值当前返回 None（由 Consumer 自行生成）
        """
        self.client = aiohttp.ClientSession(
            connector=aiohttp.TCPConnector(ssl=self.verify_ssl),
            timeout=aiohttp.ClientTimeout(total=30),
        )
        try:
            await self.login()
            site_map = await self.fetch_site_map()
            records = await self.fetch_domains(site_map, since)
            return records, None
        finally:
            await self.client.close()
