"""Encoding regression checks. Opt-in MySQL tests write connection-private temporary tables only."""
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
from urllib.parse import urlparse, unquote

ROOT = Path(__file__).resolve().parents[1]
SEED_FILE = ROOT / 'script/migrations/20260827_shared_categories_indexing.sql'
REPAIR_FILE = ROOT / 'script/migrations/20260827_utf8_category_repair.sql'
SEED = SEED_FILE.read_text(encoding='utf-8').split('INSERT INTO sys_menu',1)[0]
EXPECTED = {int(i):name for i,name in re.findall(r"SELECT (\d+) AS id,\d+ AS parent_id,'([^']+)' AS name",SEED)}

class EncodingSourceTests(unittest.TestCase):
    def test_seed_encoding_and_repair_catalog_stay_in_sync(self):
        self.assertTrue(SEED.startswith('SET NAMES utf8mb4'))
        recovered={int(i):bytes.fromhex(h).decode('utf-8') for i,h in re.findall(r"\('category',(\d+),CONVERT\(0x([0-9A-F]+) USING utf8mb4\)\)",REPAIR_FILE.read_text())}
        self.assertEqual(len(EXPECTED),199)
        self.assertEqual(recovered,EXPECTED)
        self.assertIn('encoding: UTF-8',(ROOT/'backend-admin/src/main/resources/application.yml').read_text())
    def test_every_migration_client_invocation_sets_utf8(self):
        with tempfile.TemporaryDirectory() as d:
            tmp=Path(d);log=tmp/'calls.log'
            mysql=tmp/'mysql'
            mysql.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> "$MYSQL_TEST_LOG"\ncase "$*" in *"SELECT COUNT"*) echo 0;; esac\n')
            mysql.chmod(0o700)
            (tmp/'sample.sql').write_text("SELECT '书籍';")
            env={**os.environ,'PATH':f'{tmp}:'+os.environ['PATH'],'MYSQL_TEST_LOG':str(log),'MIGRATIONS_DIR':str(tmp)}
            subprocess.run(['sh',str(ROOT/'script/apply_migrations.sh')],env=env,check=True,capture_output=True)
            calls=log.read_text().splitlines()
            # One argument may contain SQL newlines. Each mysql invocation starts with its encoding option.
            self.assertEqual(log.read_text().count('--default-character-set=utf8mb4'),5)
            self.assertTrue(calls[0].startswith('--default-character-set=utf8mb4'))

@unittest.skipUnless(os.environ.get('CYBERFLOW_CATEGORY_ENCODING_DB_TEST')=='1','Enable MySQL temporary-table tests explicitly')
class EncodingDatabaseTests(unittest.TestCase):
    def setUp(self):
        import pymysql
        from pymysql.constants import CLIENT
        url=urlparse(os.environ['DATABASE_URL'])
        self.db=pymysql.connect(host=url.hostname,port=url.port or 3306,user=unquote(url.username or ''),password=unquote(url.password or ''),database=url.path.lstrip('/'),charset='utf8mb4',autocommit=True,client_flag=CLIENT.MULTI_STATEMENTS)
        self.addCleanup(self.db.close)
        self.script('''
CREATE TEMPORARY TABLE custom_category(id BIGINT PRIMARY KEY,parent_id BIGINT NOT NULL DEFAULT 0,name VARCHAR(100) NOT NULL UNIQUE,enabled TINYINT NOT NULL DEFAULT 1,sort_order INT NOT NULL DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TEMPORARY TABLE custom_category_seed(id INT PRIMARY KEY);
CREATE TEMPORARY TABLE crawl_site_config(id BIGINT PRIMARY KEY,category VARCHAR(100)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TEMPORARY TABLE scraped_data.ecommerce_products(id BIGINT PRIMARY KEY,custom_category VARCHAR(100)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TEMPORARY TABLE sys_menu(id BIGINT PRIMARY KEY,menu_name VARCHAR(100)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TEMPORARY TABLE category_encoding_repair_backup(target_table VARCHAR(64),row_id BIGINT,old_value VARCHAR(255),repaired_value VARCHAR(100),repaired_at DATETIME DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(target_table,row_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
''')
    def script(self,sql):
        results=[]
        with self.db.cursor() as c:
            c.execute(sql)
            while True:
                if c.description: results.append(c.fetchall())
                if not c.nextset(): break
        return results
    def query(self,sql,args=None):
        with self.db.cursor() as c:
            c.execute(sql,args)
            return c.fetchall()
    def seed(self,broken=False):
        sql= re.sub(r'SET NAMES[^;]+;','',SEED) if broken else SEED
        self.script(b'SET NAMES latin1;\n'+sql.encode('utf-8'))
        self.script('SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;')
    def test_reproduces_and_repairs_known_mojibake_and_references(self):
        self.seed(broken=True)
        self.assertNotEqual(self.query('SELECT name FROM custom_category WHERE id=1')[0][0],'书籍')
        self.script('''
UPDATE custom_category SET name=CONVERT(CONVERT(CAST(name AS BINARY) USING latin1) USING utf8mb4) WHERE id=2;
UPDATE custom_category SET enabled=0,sort_order=999,parent_id=77 WHERE id=1;
UPDATE custom_category SET name='用户改过的分类' WHERE id=3;
DELETE FROM custom_category WHERE id=4;
INSERT INTO custom_category(id,name) VALUES(1000,'我自己的分类');
INSERT INTO crawl_site_config SELECT id,name FROM custom_category WHERE id IN(1,2,3,1000);
INSERT INTO scraped_data.ecommerce_products SELECT id,name FROM custom_category WHERE id IN(1,2,3,1000);
INSERT INTO sys_menu VALUES(70,CONVERT(CONVERT(CAST('自定义分类' AS BINARY) USING latin1) USING utf8mb4)),(68,'我的汇总菜单');
''')
        before=self.query('SELECT name FROM custom_category WHERE id=1')[0][0]
        result=self.script(REPAIR_FILE.read_text())
        self.assertEqual(result[-1],((197,2,2,1),))
        names=dict(self.query('SELECT id,name FROM custom_category'))
        for i,name in EXPECTED.items():
            if i not in (3,4): self.assertEqual(names[i],name)
        self.assertEqual(names[3],'用户改过的分类');self.assertEqual(names[1000],'我自己的分类');self.assertNotIn(4,names)
        self.assertEqual(self.query('SELECT parent_id,enabled,sort_order FROM custom_category WHERE id=1'),((77,0,999),))
        self.assertEqual(dict(self.query('SELECT id,category FROM crawl_site_config')),{1:'书籍',2:'五金',3:'用户改过的分类',1000:'我自己的分类'})
        self.assertEqual(dict(self.query('SELECT id,custom_category FROM scraped_data.ecommerce_products')),{1:'书籍',2:'五金',3:'用户改过的分类',1000:'我自己的分类'})
        self.assertEqual(dict(self.query('SELECT id,menu_name FROM sys_menu')),{70:'自定义分类',68:'我的汇总菜单'})
        self.assertEqual(self.query("SELECT old_value,repaired_value FROM category_encoding_repair_backup WHERE target_table='custom_category' AND row_id=1"),((before,'书籍'),))
        self.assertEqual(self.query('SELECT COUNT(*) FROM category_encoding_repair_backup'),((202,),))
        self.assertEqual(self.script(REPAIR_FILE.read_text())[-1],((0,0,0,0),))
        self.assertEqual(self.query('SELECT COUNT(*) FROM category_encoding_repair_backup'),((202,),))
    def test_fixed_seed_overrides_latin1_client_and_good_data_is_untouched(self):
        self.seed()
        self.assertEqual(dict(self.query('SELECT id,name FROM custom_category')),EXPECTED)
        self.assertEqual(self.script(REPAIR_FILE.read_text())[-1],((0,0,0,0),))
    def test_unknown_or_lossy_names_are_not_guessed(self):
        self.seed()
        self.query("UPDATE custom_category SET name='???' WHERE id=1")
        self.assertEqual(self.script(REPAIR_FILE.read_text())[-1],((0,0,0,0),))
        self.assertEqual(self.query('SELECT name FROM custom_category WHERE id=1'),(('???',),))
    def test_unique_name_conflict_rolls_back_all_repairs(self):
        import pymysql
        self.seed(broken=True)
        before=self.query('SELECT id,name FROM custom_category ORDER BY id')
        self.query("INSERT INTO custom_category(id,name) VALUES(1000,'书籍')")
        with self.assertRaises(pymysql.IntegrityError): self.script(REPAIR_FILE.read_text())
        self.db.rollback()
        self.assertEqual(self.query('SELECT id,name FROM custom_category WHERE id<1000 ORDER BY id'),before)
        self.assertEqual(self.query('SELECT COUNT(*) FROM category_encoding_repair_backup'),((0,),))
