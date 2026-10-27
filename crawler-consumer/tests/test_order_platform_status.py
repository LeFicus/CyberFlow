import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock, patch
from consumers.order_consumer import OrderConsumer
from crawlers.order_crawler import AsyncOrderCrawler
from test_retired_site_orders import FakeCursor, FakePool


class Response:
    status = 200
    def __init__(self, data): self.data = data
    async def __aenter__(self): return self
    async def __aexit__(self, *args): return False
    async def json(self): return self.data


class Client:
    def __init__(self, pages): self.pages, self.calls = pages, []
    def get(self, url, headers=None, params=None):
        self.calls.append(params['page_no'])
        return Response(self.pages[params['page_no'] - 1])


def page(rows, last=3):
    return {'code': 1, 'data': {'lists': rows, 'current_page': 1, 'last_page': last}}


class OrderPlatformStatusTest(unittest.IsolatedAsyncioTestCase):
    async def test_incremental_fetch_skips_old_orders_even_when_platform_flags_changed(self):
        crawler = AsyncOrderCrawler('https://fixture.test', page_size=2)
        crawler.client = Client([
            page([{'id': 202, 'is_fraud': 0, 'is_valid': 0}, {'id': 201, 'is_fraud': 1, 'is_valid': 1}]),
            page([{'id': 105, 'is_fraud': 1, 'is_valid': 0}, {'id': 104, 'is_fraud': 0, 'is_valid': 1}]),
        ])
        rows, cursor = await crawler.fetch_orders('150')
        self.assertEqual([202, 201], [r['id'] for r in rows])
        self.assertEqual((0, 0), (rows[0]['is_fraud'], rows[0]['is_valid']))
        self.assertEqual('202', cursor)
        self.assertEqual([1, 2], crawler.client.calls)

    async def test_run_only_performs_incremental_fetch_and_closes_client(self):
        crawler = AsyncOrderCrawler('https://fixture.test')
        client = SimpleNamespace(close=AsyncMock(), get=Mock(side_effect=AssertionError('unexpected historical request')))
        crawler.login = AsyncMock(return_value='fixture-token')
        crawler.fetch_orders = AsyncMock(return_value=([], '150'))
        with patch('crawlers.order_crawler.aiohttp.ClientSession', return_value=client):
            rows, cursor = await crawler.run('fixture', 'password', '150')
        self.assertEqual(([], '150'), (rows, cursor))
        crawler.fetch_orders.assert_awaited_once_with('150')
        client.get.assert_not_called()
        client.close.assert_awaited_once()

    async def test_consumer_uses_cursor_without_querying_or_refreshing_historical_orders(self):
        for records in [[], [{'id': 151, 'is_fraud': 1, 'is_valid': 0}]]:
            repo = SimpleNamespace(**{name: AsyncMock() for name in (
                'connect', 'reset_task_log', 'wait_for_task_control', 'update_task_status',
                'update_task_progress', 'update_cursor', 'close')})
            repo.pool = SimpleNamespace(acquire=Mock(side_effect=AssertionError('unexpected historical database scan')))
            consumer = OrderConsumer.__new__(OrderConsumer)
            consumer.repo = repo
            consumer.append_task_log = AsyncMock()
            consumer._save_orders = AsyncMock(return_value=len(records))
            consumer._publish_result = Mock()
            cursor = '151' if records else '150'
            crawler = SimpleNamespace(page_size=100, run=AsyncMock(return_value=(records, cursor)))
            with patch('consumers.order_consumer.AsyncOrderCrawler', return_value=crawler):
                await consumer.process({'task_id': 'fixture-task', 'payload': {
                    'user_group': 'A', 'cursor': {'max_order_id': '150'},
                    'platform': {'baseUrl': 'https://fixture.test', 'account': 'fixture', 'password': 'password'},
                }})
            crawler.run.assert_awaited_once_with('fixture', 'password', '150')
            consumer._save_orders.assert_awaited_once_with(records, 'A')
            repo.pool.acquire.assert_not_called()
            repo.update_cursor.assert_awaited_once_with('order_crawler_A', cursor)
            self.assertEqual(len(records), repo.update_task_status.await_args_list[-1].kwargs['rows_affected'])

    async def test_new_orders_store_both_platform_flags(self):
        cursor = FakeCursor(('alice', 'A', '', '', 0))
        consumer = OrderConsumer.__new__(OrderConsumer)
        consumer.repo = type('Repository', (), {'pool': FakePool(cursor)})()
        await consumer._save_orders([{'id': 1, 'product_host': 'shop.test', 'is_fraud': '0', 'is_alarm': '10009', 'is_valid': 0}], 'A')
        sql, params = next((sql, params) for sql, params in cursor.queries if 'INSERT INTO orders' in sql)
        self.assertIn('is_fraud=COALESCE(VALUES(is_fraud), is_fraud)', sql)
        self.assertEqual(1, params[-1])
        self.assertEqual(10009, params[-2])
        self.assertEqual(0, params[10])

    def test_pagination_uses_actual_platform_page_size_and_total(self):
        crawler = AsyncOrderCrawler('https://fixture.test', page_size=500)
        self.assertTrue(crawler._has_next_page({'data': {'count': '230', 'page_size': '100'}}, 100, 1))
        self.assertFalse(crawler._has_next_page({'data': {'count': '230', 'page_size': '100'}}, 30, 3))

    def test_risk_flag_uses_alarm_code_not_api_fraud_boolean(self):
        for value, expected in [(0, 0), ('0', 0), (10009, 1), ('10009', 1), (10001, 1), (None, None), ('', None)]:
            self.assertEqual(expected, OrderConsumer._risk_flag(value))
        for value in [-1, 'yes', False, 0.5, '2147483648']:
            with self.assertRaises(ValueError): OrderConsumer._risk_flag(value)

    def test_platform_flags_preserve_zero_and_reject_unsupported_values(self):
        for value, expected in [(0, 0), ('0', 0), (False, 0), (1, 1), ('1', 1), (True, 1), (None, None), ('', None)]:
            self.assertEqual(expected, OrderConsumer._platform_flag(value))
        for value in [2, 'yes', 'false', [], 0.5]:
            with self.assertRaises(ValueError): OrderConsumer._platform_flag(value)
