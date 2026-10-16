"""Run opt-in MySQL regressions in a disposable schema with synthetic site data."""

from contextlib import asynccontextmanager
import json
import os
import re
from types import SimpleNamespace
import unittest
import uuid

import aiomysql

from config import DATABASE_URL
from consumers.site_consumer import SiteConsumer
from db.repository import _parse_mysql_url


class FixedConnectionPool:
    def __init__(self, connection):
        self.connection = connection

    @asynccontextmanager
    async def acquire(self):
        yield self.connection


@unittest.skipUnless(os.environ.get("CYBERFLOW_SITE_DB_TEST") == "1", "opt-in MySQL test")
class RetiredSiteJsonDatabaseTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        settings = _parse_mysql_url(DATABASE_URL)
        source_schema = settings.pop("db")
        if not re.fullmatch(r"[A-Za-z0-9_]+", source_schema):
            raise ValueError("Unsupported source schema name")
        # Never connect with a business database selected for fixture writes.
        self.connection = await aiomysql.connect(**settings, charset="utf8mb4", autocommit=True)
        self.schema = "cyberflow_site_json_test_" + uuid.uuid4().hex
        self.addAsyncCleanup(self.clean_schema)
        async with self.connection.cursor() as cursor:
            await cursor.execute(f"CREATE DATABASE `{self.schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        await self.connection.select_db(self.schema)
        async with self.connection.cursor() as cursor:
            await cursor.execute("SELECT DATABASE()")
            self.assertEqual(self.schema, (await cursor.fetchone())[0])
            for table in ("site_info", "retired_site_info", "orders", "site_indexing_history"):
                await cursor.execute(f"CREATE TABLE `{table}` LIKE `{source_schema}`.`{table}`")
            await cursor.execute(
                """INSERT INTO retired_site_info
                   (site_domain,admin_name,user_group,cat_names,site_tag,domain_applied_at)
                   VALUES (%s,%s,%s,%s,%s,%s)""",
                ("retired.example", "A-original", "A", '["旧分类","园艺"]', 2, "2026-09-01"),
            )
            await cursor.execute(
                """INSERT INTO orders (id,user_group,product_host,admin_name)
                   VALUES (1,'A','retired.example','A-original')"""
            )
        self.consumer = SiteConsumer.__new__(SiteConsumer)
        self.consumer.repo = SimpleNamespace(pool=FixedConnectionPool(self.connection))

    async def clean_schema(self):
        try:
            async with self.connection.cursor() as cursor:
                await cursor.execute(f"DROP DATABASE IF EXISTS `{self.schema}`")
        finally:
            self.connection.close()

    async def synchronize_fixture(self, categories, site_tag):
        await self.consumer._upsert_site_info(
            [{"site_domain": "live.example", "username": "fixture", "admin_name": "A-fixture",
              "cat_names": ["家居"], "site_tag": 0}],
            {},
            [{"site_domain": "retired.example", "admin_name": "A-updated",
              "cat_names": categories, "site_tag": site_tag}],
        )
        async with self.connection.cursor() as cursor:
            await cursor.execute(
                "SELECT cat_names,site_tag,admin_name,DATE(domain_applied_at) "
                "FROM retired_site_info WHERE site_domain='retired.example'"
            )
            return await cursor.fetchone()

    async def test_empty_categories_preserve_old_json_and_build_type_on_repeated_sync(self):
        for categories in ([], "[]", None):
            with self.subTest(categories=categories):
                row = await self.synchronize_fixture(categories, 0)
                self.assertEqual(["旧分类", "园艺"], json.loads(row[0]))
                self.assertEqual(2, row[1])
                self.assertEqual("A-updated", row[2])
                self.assertEqual("2026-09-01", str(row[3]))

    async def test_nonempty_categories_update_json_and_build_type(self):
        categories = ['工具"配件', "户外\\园艺", "中文😀"]
        row = await self.synchronize_fixture(json.dumps(categories, ensure_ascii=False), 1)
        self.assertEqual(categories, json.loads(row[0]))
        self.assertEqual(1, row[1])
        preserved = await self.synchronize_fixture([], 0)
        self.assertEqual(categories, json.loads(preserved[0]))
        self.assertEqual(1, preserved[1])

    async def test_retiring_live_sites_preserves_their_json_categories(self):
        async with self.connection.cursor() as cursor:
            await cursor.execute(
                """INSERT INTO site_info (username,site_domain,admin_name,user_group,cat_names,site_tag)
                   VALUES ('fixture','archive.example','A-fixture','A',%s,1)""",
                ('["归档分类","园艺"]',),
            )
        await self.synchronize_fixture([], 0)
        async with self.connection.cursor() as cursor:
            await cursor.execute("SELECT cat_names,site_tag FROM retired_site_info WHERE site_domain='archive.example'")
            row = await cursor.fetchone()
            self.assertEqual(["归档分类", "园艺"], json.loads(row[0]))
            self.assertEqual(1, row[1])
            await cursor.execute("SELECT COUNT(*) FROM site_info WHERE site_domain='archive.example'")
            self.assertEqual(0, (await cursor.fetchone())[0])


if __name__ == "__main__":
    unittest.main()
