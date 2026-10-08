import asyncio
import unittest

from crawlers.site_crawler import AsyncSiteCrawler


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return False

    async def json(self):
        return self.payload


class FakeClient:
    def __init__(self, payload):
        self.payload = payload

    def get(self, *_args, **_kwargs):
        return FakeResponse(self.payload)


class SiteCrawlerTest(unittest.TestCase):
    def test_site_list_login_fields_are_preserved(self):
        crawler = AsyncSiteCrawler("https://admin.example", "admin", "secret")
        crawler.client = FakeClient({
            "data": {
                "data": [{
                    "site_domain": "shop.example",
                    "login_url": "https://shop.example/wp-admin",
                    "wp_admin_user": "shop-admin",
                    "wp_admin_user_pwd": "site-secret",
                }]
            }
        })

        site_map = asyncio.run(crawler.fetch_site_map())

        self.assertEqual("https://shop.example/wp-admin", site_map["shop.example"]["login_url"])
        self.assertEqual("shop-admin", site_map["shop.example"]["wp_admin_user"])
        self.assertEqual("site-secret", site_map["shop.example"]["wp_admin_user_pwd"])

    def test_site_login_fields_are_merged_into_persisted_records(self):
        crawler = AsyncSiteCrawler("https://admin.example", "admin", "secret")
        crawler.client = FakeClient({
            "data": {
                "data": [{
                    "domain": "shop.example",
                    "admin_name": "负责人",
                    "status": 2,
                }]
            }
        })
        site_map = {
            "shop.example": {
                "theme_name": "主题",
                "product_category": "分类",
                "login_url": "https://shop.example/wp-admin",
                "wp_admin_user": "shop-admin",
                "wp_admin_user_pwd": "site-secret",
            }
        }

        records = asyncio.run(crawler.fetch_domains(site_map))

        self.assertEqual("https://shop.example/wp-admin", records[0]["login_url"])
        self.assertEqual("shop-admin", records[0]["wp_admin_user"])
        self.assertEqual("site-secret", records[0]["wp_admin_user_pwd"])

    def test_abandoned_domain_is_kept_for_order_history_only(self):
        crawler = AsyncSiteCrawler("https://admin.example", "admin", "secret")
        crawler.client = FakeClient({
            "data": {"data": [{
                "domain": "retired.example", "admin_name": "A-张三",
                "status": 3, "add_date": "2026-05-01 00:00:00",
            }]}
        })

        records = asyncio.run(crawler.fetch_domains({}))

        self.assertEqual([], records)
        self.assertEqual("retired.example", crawler.retired_domains[0]["site_domain"])
        self.assertEqual("A-张三", crawler.retired_domains[0]["admin_name"])


if __name__ == "__main__":
    unittest.main()
