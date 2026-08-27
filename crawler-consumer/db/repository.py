"""
数据库仓库层 —— 封装所有 MySQL 数据库访问操作。

本模块提供两个核心仓库类：
1. CursorRepository —— 管理 cyberflow 主数据库的爬取游标与任务状态
2. ProductRepository —— 管理 scraped_data 数据库的连接池
"""

import aiomysql
import asyncio
from loguru import logger
from config import DATABASE_URL, SCRAPED_DB_URL


class TaskCancelledError(RuntimeError):
    """Raised when an operator has cancelled or removed a task."""


def _parse_mysql_url(url: str):
    """将 MySQL 连接 URL 解析为 aiomysql 所需的参数字典。

    支持的 URL 格式：
        mysql+pymysql://user:pass@host:port/db

    参数:
        url (str): MySQL 连接 URL 字符串

    返回:
        dict: 包含 host, port, user, password, db 五个键的字典

    示例:
        >>> _parse_mysql_url("mysql+pymysql://root:123456@localhost:3306/cyberflow")
        {'host': 'localhost', 'port': 3306, 'user': 'root', 'password': '123456', 'db': 'cyberflow'}
    """
    from urllib.parse import urlparse, unquote
    parsed = urlparse(url)
    return {
        "host": parsed.hostname or "localhost",
        "port": parsed.port or 3306,
        "user": unquote(parsed.username or "root"),
        "password": unquote(parsed.password or ""),
        "db": parsed.path.lstrip("/"),
    }


class CursorRepository:
    """爬取游标仓库类 —— 管理 crawl_cursor 表的读写与任务状态更新。

    职责:
        - 读取/更新爬取游标 (crawl_cursor)，记录增量爬取的断点位置
        - 更新任务状态 (task_history)，标记 RUNNING / SUCCESS / FAILED
        - 查询站点配置 (crawl_site_config) 及其关联的选择器模板

    用法:
        repo = CursorRepository()
        await repo.connect()
        cursor = await repo.get_cursor("site_crawler")
        await repo.update_cursor("site_crawler", "2024-01-01T00:00:00Z")
        await repo.close()
    """

    def __init__(self):
        """初始化 CursorRepository 实例，连接池初始为空。"""
        self.pool: aiomysql.Pool | None = None

    async def connect(self):
        """建立 MySQL 连接池。

        从 DATABASE_URL 解析连接参数并创建 aiomysql 连接池，
        使用 utf8mb4 编码和 autocommit 模式。
        """
        kwargs = _parse_mysql_url(DATABASE_URL)
        self.pool = await aiomysql.create_pool(
            host=kwargs["host"], port=kwargs["port"],
            user=kwargs["user"], password=kwargs["password"],
            db=kwargs["db"], charset="utf8mb4", autocommit=True,
        )

    async def get_cursor(self, cursor_key: str) -> str | None:
        """根据游标键读取当前游标值。

        参数:
            cursor_key (str): 游标的唯一标识键，如 "site_crawler"、"order_crawler"

        返回:
            str | None: 游标值字符串，如果记录不存在则返回 None
        """
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(
                    "SELECT cursor_value FROM crawl_cursor WHERE cursor_key = %s",
                    (cursor_key,),
                )
                row = await cur.fetchone()
                return row[0] if row else None

    async def update_cursor(self, cursor_key: str, cursor_value: str):
        """更新或插入游标记录（UPSERT 语义）。

        使用 INSERT ... ON DUPLICATE KEY UPDATE 实现原子性的游标更新，
        同时自动记录 last_sync_at 时间戳。

        参数:
            cursor_key (str): 游标的唯一标识键
            cursor_value (str): 新的游标值
        """
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(
                    """INSERT INTO crawl_cursor (cursor_key, cursor_value, last_sync_at)
                       VALUES (%s, %s, NOW())
                       ON DUPLICATE KEY UPDATE cursor_value=%s, last_sync_at=NOW()""",
                    (cursor_key, cursor_value, cursor_value),
                )

    async def update_task_status(self, task_id: str, status: str, **kwargs):
        """更新任务执行状态。

        根据状态类型执行不同的 SQL 更新逻辑：
        - RUNNING: 仅设置状态和开始时间
        - SUCCESS / FAILED: 设置状态、影响行数、耗时、错误信息和完成时间

        参数:
            task_id (str): 任务的唯一标识 ID
            status (str): 任务状态，可选值: "RUNNING", "SUCCESS", "FAILED"
            **kwargs: 额外字段，包括:
                rows_affected (int): 影响的行数（默认 0）
                duration_ms (int): 任务耗时毫秒数（默认 0）
                error_msg (str): 错误信息（默认空字符串）
        """
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                if status == "RUNNING":
                    await cur.execute(
                        """UPDATE task_history SET status='RUNNING', started_at=COALESCE(started_at, NOW()),
                           progress=%s, progress_message=%s
                           WHERE task_id=%s AND status NOT IN ('PAUSED', 'CANCELLED')""",
                        (kwargs.get("progress", 5), kwargs.get("progress_message", "任务已开始"), task_id),
                    )
                elif status in ("SUCCESS", "FAILED"):
                    await cur.execute(
                        """UPDATE task_history SET status=%s, rows_affected=%s,
                           duration_ms=%s, error_msg=%s, finished_at=NOW(), progress=%s,
                           progress_message=%s WHERE task_id=%s""",
                        (status,
                         kwargs.get("rows_affected", 0),
                         kwargs.get("duration_ms", 0),
                         kwargs.get("error_msg", ""),
                         100 if status == "SUCCESS" else kwargs.get("progress", 0),
                         kwargs.get("progress_message", "采集完成" if status == "SUCCESS" else "采集失败"),
                         task_id),
                    )

    async def update_task_progress(self, task_id: str, progress: int, message: str):
        """Persist a visible crawl phase without changing the task lifecycle state."""
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(
                    "UPDATE task_history SET progress=%s, progress_message=%s WHERE task_id=%s",
                    (max(0, min(99, progress)), message[:255], task_id),
                )

    async def get_task_status(self, task_id: str) -> str | None:
        """Read the operator-controlled lifecycle state for a task."""
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute("SELECT status FROM task_history WHERE task_id=%s", (task_id,))
                row = await cur.fetchone()
                return str(row[0]).upper() if row and row[0] is not None else None

    async def wait_for_task_control(self, task_id: str) -> str:
        """Wait while a task is paused and stop promptly when its record is deleted."""
        while True:
            status = await self.get_task_status(task_id)
            if status is None or status in {"CANCELLED", "DELETED"}:
                raise TaskCancelledError("任务已被删除或取消")
            if status != "PAUSED":
                return status
            await asyncio.sleep(2)

    async def reset_task_log(self, task_id: str):
        """Clear a task log before starting its crawler subprocess."""
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(
                    "UPDATE task_history SET crawl_log='' WHERE task_id=%s",
                    (task_id,),
                )

    async def append_task_log(self, task_id: str, content: str):
        """Append crawler output without truncating the existing task log."""
        if not content:
            return
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(
                    """UPDATE task_history
                       SET crawl_log=CONCAT(COALESCE(crawl_log, ''), %s)
                       WHERE task_id=%s""",
                    (content, task_id),
                )

    async def get_site_config(self, site_config_id: int) -> dict | None:
        """获取站点爬取配置及其关联的选择器模板。

        查询 crawl_site_config 表获取站点基本配置，同时通过
        site_template_mapping 和 selector_template 两张关联表
        获取该站点关联的所有选择器模板信息。

        参数:
            site_config_id (int): 站点配置的唯一 ID

        返回:
            dict | None: 包含站点配置及 templates 列表的字典，不存在时返回 None
        """
        async with self.pool.acquire() as conn:
            async with conn.cursor(aiomysql.DictCursor) as cur:
                await cur.execute(
                    "SELECT * FROM crawl_site_config WHERE id=%s", (site_config_id,)
                )
                config = await cur.fetchone()
                if not config:
                    return None
                await cur.execute(
                    """SELECT st.*, stm.extra_selectors
                       FROM site_template_mapping stm
                       JOIN selector_template st ON st.id = stm.template_id
                       WHERE stm.site_config_id = %s""",
                    (site_config_id,),
                )
                templates = await cur.fetchall()
                config["uses_default_template"] = not bool(templates)
                # All non-Shopify engines currently share the WooCommerce
                # selector profile. A site-specific mapping wins; otherwise
                # use the system/default WooCommerce template when available.
                if not templates and str(config.get("type", "")).lower() != "shopify":
                    await cur.execute(
                        """SELECT st.*, NULL AS extra_selectors
                           FROM selector_template st
                           WHERE st.platform = 'woocommerce'
                           ORDER BY st.is_system DESC, st.id ASC
                           LIMIT 1"""
                    )
                    templates = await cur.fetchall()
                config["templates"] = templates
                return config

    async def close(self):
        """关闭连接池，释放所有数据库连接资源。"""
        if self.pool:
            self.pool.close()
            await self.pool.wait_closed()


class ProductRepository:
    """产品数据仓库类 —— 管理 scraped_data 数据库的连接池。

    用于爬取结果数据库（scraped_data）的连接管理，
    该数据库存储 ecommerce_products 等爬取结果表。

    用法:
        repo = ProductRepository()
        await repo.connect()
        # 执行数据写入操作...
        await repo.close()
    """

    def __init__(self):
        """初始化 ProductRepository 实例，连接池初始为空。"""
        self.pool: aiomysql.Pool | None = None

    async def connect(self):
        """建立到 scraped_data 数据库的 MySQL 连接池。

        从 SCRAPED_DB_URL 解析连接参数，使用 utf8mb4 编码和 autocommit 模式。
        """
        kwargs = _parse_mysql_url(SCRAPED_DB_URL)
        self.pool = await aiomysql.create_pool(
            host=kwargs["host"], port=kwargs["port"],
            user=kwargs["user"], password=kwargs["password"],
            db=kwargs["db"], charset="utf8mb4", autocommit=True,
        )

    async def close(self):
        """关闭连接池，释放所有数据库连接资源。"""
        if self.pool:
            self.pool.close()
            await self.pool.wait_closed()
