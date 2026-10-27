"""Opt-in integration tests using a disposable schema and synthetic orders only."""
import json
import os
import re
import unittest
import uuid
from contextlib import asynccontextmanager
from types import SimpleNamespace
import aiomysql
from config import DATABASE_URL
from db.repository import _parse_mysql_url
from consumers.order_consumer import OrderConsumer


class Pool:
    def __init__(self, connection): self.connection = connection
    @asynccontextmanager
    async def acquire(self): yield self.connection


@unittest.skipUnless(os.environ.get('CYBERFLOW_ORDER_DB_TEST') == '1', 'opt-in MySQL test')
class OrderPlatformStatusDatabaseTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        settings = _parse_mysql_url(DATABASE_URL)
        source = settings.pop('db')
        if not re.fullmatch(r'[A-Za-z0-9_]+', source): raise ValueError('Unsupported schema')
        self.connection = await aiomysql.connect(**settings, charset='utf8mb4', autocommit=True)
        self.schema = 'cyberflow_order_status_test_' + uuid.uuid4().hex
        self.addAsyncCleanup(self.cleanup)
        async with self.connection.cursor() as cur:
            await cur.execute(f'CREATE DATABASE `{self.schema}` CHARACTER SET utf8mb4')
        await self.connection.select_db(self.schema)
        async with self.connection.cursor() as cur:
            await cur.execute('SELECT DATABASE()')
            self.assertEqual(self.schema, (await cur.fetchone())[0])
            for table in ('orders', 'site_info', 'retired_site_info'):
                await cur.execute(f'CREATE TABLE `{table}` LIKE `{source}`.`{table}`')
            await cur.execute("SHOW COLUMNS FROM orders LIKE 'is_fraud'")
            if not await cur.fetchone(): await cur.execute('ALTER TABLE orders ADD is_fraud TINYINT NULL')
            await cur.execute("SHOW COLUMNS FROM orders LIKE 'is_alarm'")
            if not await cur.fetchone(): await cur.execute('ALTER TABLE orders ADD is_alarm INT NULL')
            await cur.execute("INSERT INTO site_info (username,site_domain,admin_name,user_group) VALUES ('fixture','shop.test','alice','A')")
        self.consumer = OrderConsumer.__new__(OrderConsumer)
        self.consumer.repo = SimpleNamespace(pool=Pool(self.connection))

    async def cleanup(self):
        try:
            async with self.connection.cursor() as cur: await cur.execute(f'DROP DATABASE IF EXISTS `{self.schema}`')
        finally: self.connection.close()

    async def row(self):
        async with self.connection.cursor() as cur:
            await cur.execute('SELECT is_fraud,is_valid,amount,shipping_email,dedupe_key,is_alarm FROM orders WHERE id=1 AND user_group=\'A\'')
            return await cur.fetchone()

    async def test_new_order_insert_and_retry_preserve_platform_flags_and_other_order_data(self):
        record = {'id': 1, 'product_host': 'shop.test', 'amount': '10.00', 'currency': 'USD',
                  'create_time': '2026-10-27 10:00:00', 'shipping_email': 'first.last.123@gmail.com',
                  'is_fraud': '0', 'is_alarm': '10009', 'is_valid': '0'}
        self.assertEqual(1, await self.consumer._save_orders([record], 'A'))
        self.assertEqual((1, 0), (await self.row())[:2])
        self.assertEqual(10009, (await self.row())[-1])
        await self.consumer._save_orders([{**record, 'is_fraud': 1, 'is_alarm': 0, 'is_valid': 1}], 'A')
        before = await self.row()
        self.assertEqual((0, 1), before[:2])
        self.assertEqual(0, before[-1])
        without_flags = {key: value for key, value in record.items() if key not in ('is_fraud', 'is_alarm', 'is_valid')}
        await self.consumer._save_orders([without_flags], 'A')
        self.assertEqual(before, await self.row())
