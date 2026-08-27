"""Opt-in quality backfill test using connection-private temporary tables only."""
import os
import sys
import unittest
from pathlib import Path
from urllib.parse import urlparse,unquote
import pymysql
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'script'))
from apply_product_policy import run

@unittest.skipUnless(os.environ.get('CYBERFLOW_PRODUCT_POLICY_DB_TEST'), 'Enable temporary-table DB policy checks explicitly')
class PolicyDatabaseTests(unittest.TestCase):
    def test_preview_audit_and_repeat_are_safe(self):
        url=urlparse(os.environ['SCRAPED_DB_URL'])
        with pymysql.connect(host=url.hostname,port=url.port or 3306,user=unquote(url.username),password=unquote(url.password),database=url.path.lstrip('/'),charset='utf8mb4',cursorclass=pymysql.cursors.DictCursor,autocommit=False) as db:
            with db.cursor() as c:
                for table in ['ecommerce_products','product_policy_audit']:
                    c.execute(f'SHOW CREATE TABLE {table}')
                    ddl=c.fetchone()['Create Table'].replace('CREATE TABLE','CREATE TEMPORARY TABLE',1)
                    c.execute(ddl)
                c.executemany('INSERT INTO ecommerce_products(id,sku,source_domain,regular_price,images) VALUES(%s,%s,%s,%s,%s)',[
                    (1,'A','shop.test',151,'https://cdn.test/ProductDefault.gif'),
                    (2,'B','shop.test',150,'https://cdn.test/hammer.jpg'),
                    (3,'C','shop.test',999,'https://cdn.test/placeholder.png, https://cdn.test/drill.jpg'),
                    (4,'D','shop.test',12,None)])
            db.commit()
            self.assertEqual(run(db,False,batch_size=2)['changed'],3)
            with db.cursor() as c:
                c.execute('SELECT COUNT(*) n FROM product_policy_audit');self.assertEqual(c.fetchone()['n'],0)
            self.assertEqual(run(db,True,batch_size=2)['adjusted'],2)
            with db.cursor() as c:
                c.execute('SELECT * FROM ecommerce_products ORDER BY id');rows=c.fetchall()
                self.assertEqual(len(rows),4)
                self.assertEqual(rows[0]['image_usable'],0)
                self.assertEqual(rows[0]['original_price_usd'],151)
                self.assertEqual(rows[1]['regular_price'],150)
                self.assertEqual(rows[2]['images'],'https://cdn.test/drill.jpg')
                self.assertTrue(120<=rows[2]['regular_price']<=150)
                c.execute('SELECT COUNT(*) n FROM product_policy_audit');self.assertEqual(c.fetchone()['n'],3)
            self.assertEqual(run(db,True,batch_size=2)['changed'],0)
