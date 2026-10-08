import asyncio
import unittest

from consumers.order_consumer import OrderConsumer
from consumers.site_consumer import SiteConsumer


class FakeCursor:
    def __init__(self, retired_site=None):
        self.retired_site = retired_site
        self.queries = []
        self.rowcount = 0

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args):
        return False

    async def execute(self, sql, params=None):
        self.queries.append((sql, params))

    async def executemany(self, sql, params):
        self.queries.append((sql, params))

    async def fetchone(self):
        sql = self.queries[-1][0]
        return self.retired_site if "FROM retired_site_info" in sql else None

    async def fetchall(self):
        if "SELECT DISTINCT LOWER(CASE" in self.queries[-1][0]:
            return [("old.example",)]
        return []


class FakeConnection:
    def __init__(self, cursor):
        self.fake_cursor = cursor

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args):
        return False

    def cursor(self):
        return self.fake_cursor


class FakePool:
    def __init__(self, cursor):
        self.connection = FakeConnection(cursor)

    def acquire(self):
        return self.connection


class RetiredSiteOrdersTest(unittest.TestCase):
    def test_order_uses_retired_site_owner_and_preserves_existing_owner_on_upsert(self):
        cursor = FakeCursor(("A-张三", "A", "旧主题", "旧分类", 2))
        consumer = OrderConsumer.__new__(OrderConsumer)
        consumer.repo = type("Repository", (), {"pool": FakePool(cursor)})()

        saved = asyncio.run(consumer._save_orders([{
            "id": 31, "product_host": "www.retired.example", "amount": 10,
        }], "B"))

        self.assertEqual(1, saved)
        insert, params = next((sql, params) for sql, params in cursor.queries
                              if "INSERT INTO orders" in sql)
        self.assertEqual("A-张三", params[11])
        self.assertEqual("A", params[12])
        self.assertEqual("retired.example", params[4])
        self.assertIn("admin_name=COALESCE(NULLIF(VALUES(admin_name), ''), admin_name)", insert)
        self.assertIn("site_tag=CASE WHEN NULLIF(VALUES(admin_name), '') IS NOT NULL", insert)

    def test_site_retirement_archives_metadata_before_deleting_current_site(self):
        cursor = FakeCursor()

        asyncio.run(SiteConsumer._delete_inactive_sites(cursor))

        statements = [sql for sql, _ in cursor.queries]
        self.assertIn("INSERT INTO retired_site_info", statements[0])
        self.assertIn("DELETE s FROM site_info", statements[-1])
        self.assertLess(statements.index(statements[0]), len(statements) - 1)

    def test_abandoned_upstream_domain_restores_orders_without_a_live_site(self):
        cursor = FakeCursor()
        consumer = SiteConsumer.__new__(SiteConsumer)
        consumer.repo = type("Repository", (), {"pool": FakePool(cursor)})()

        asyncio.run(consumer._upsert_site_info(
            [{"site_domain": "live.example", "username": "builder", "admin_name": "A-李四"}],
            {},
            [{"site_domain": "old.example", "admin_name": "A-张三",
              "domain_applied_at": "2026-06-01 00:00:00"}],
        ))

        archived = [(sql, params) for sql, params in cursor.queries
                    if "INSERT INTO retired_site_info" in sql and "VALUES (%s" in sql]
        self.assertEqual(1, len(archived))
        self.assertEqual("old.example", archived[0][1][0])
        self.assertEqual("A-张三", archived[0][1][1])
        self.assertEqual("A", archived[0][1][2])
        self.assertTrue(any("UPDATE orders o JOIN retired_site_info r" in sql
                            for sql, _ in cursor.queries))


if __name__ == "__main__":
    unittest.main()
