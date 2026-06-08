import aiomysql
from loguru import logger
from config import DATABASE_URL, SCRAPED_DB_URL


def _parse_mysql_url(url: str):
    """Parse mysql+pymysql://user:pass@host:port/db to kwargs."""
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
    """Manage crawl_cursor read/write via aiomysql."""

    def __init__(self):
        self.pool: aiomysql.Pool | None = None

    async def connect(self):
        kwargs = _parse_mysql_url(DATABASE_URL)
        self.pool = await aiomysql.create_pool(
            host=kwargs["host"], port=kwargs["port"],
            user=kwargs["user"], password=kwargs["password"],
            db=kwargs["db"], charset="utf8mb4", autocommit=True,
        )

    async def get_cursor(self, cursor_key: str) -> str | None:
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(
                    "SELECT cursor_value FROM crawl_cursor WHERE cursor_key = %s",
                    (cursor_key,),
                )
                row = await cur.fetchone()
                return row[0] if row else None

    async def update_cursor(self, cursor_key: str, cursor_value: str):
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                await cur.execute(
                    """INSERT INTO crawl_cursor (cursor_key, cursor_value, last_sync_at)
                       VALUES (%s, %s, NOW())
                       ON DUPLICATE KEY UPDATE cursor_value=%s, last_sync_at=NOW()""",
                    (cursor_key, cursor_value, cursor_value),
                )

    async def update_task_status(self, task_id: str, status: str, **kwargs):
        async with self.pool.acquire() as conn:
            async with conn.cursor() as cur:
                if status == "RUNNING":
                    await cur.execute(
                        "UPDATE task_history SET status='RUNNING', started_at=NOW() WHERE task_id=%s",
                        (task_id,),
                    )
                elif status in ("SUCCESS", "FAILED"):
                    await cur.execute(
                        """UPDATE task_history SET status=%s, rows_affected=%s,
                           duration_ms=%s, error_msg=%s, finished_at=NOW() WHERE task_id=%s""",
                        (status,
                         kwargs.get("rows_affected", 0),
                         kwargs.get("duration_ms", 0),
                         kwargs.get("error_msg", ""),
                         task_id),
                    )

    async def get_site_config(self, site_config_id: int) -> dict | None:
        """Get crawl_site_config + merged selectors."""
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
                config["templates"] = templates
                return config

    async def close(self):
        if self.pool:
            self.pool.close()
            await self.pool.wait_closed()


class ProductRepository:
    """Manage scraped_data.ecommerce_products writes."""

    def __init__(self):
        self.pool: aiomysql.Pool | None = None

    async def connect(self):
        kwargs = _parse_mysql_url(SCRAPED_DB_URL)
        self.pool = await aiomysql.create_pool(
            host=kwargs["host"], port=kwargs["port"],
            user=kwargs["user"], password=kwargs["password"],
            db=kwargs["db"], charset="utf8mb4", autocommit=True,
        )

    async def close(self):
        if self.pool:
            self.pool.close()
            await self.pool.wait_closed()
