"""Backfill non-unique product lookup hints without merging or deleting products."""

import os
from urllib.parse import urlparse

import pymysql

from ecommerce_spider.normalization import product_dedupe_key
from ecommerce_spider.pipelines import MySQLRedisPipeline


def config():
    parsed = urlparse(os.environ.get("SCRAPED_DB_URL", "mysql+pymysql://root:123456@mysql:3306/scraped_data"))
    return {
        "host": parsed.hostname or "mysql", "port": parsed.port or 3306,
        "user": parsed.username or "root", "password": parsed.password or "123456",
        "database": (parsed.path or "/scraped_data").lstrip("/"), "charset": "utf8mb4",
    }


def main():
    conn = pymysql.connect(**config())
    try:
        with conn.cursor() as cur:
            MySQLRedisPipeline._check_identity(cur)
        with conn.cursor(pymysql.cursors.DictCursor) as cur:
            cur.execute("SELECT id, source_domain, name, images FROM ecommerce_products")
            rows = cur.fetchall()
            for row in rows:
                cur.execute(
                    "UPDATE ecommerce_products SET dedupe_key=%s WHERE id=%s",
                    (product_dedupe_key(row.get("source_domain"), row.get("name"), row.get("images")), row["id"]),
                )
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
    print(f"scanned={len(rows)} deleted=0; product identity remains (source_domain, sku)")


if __name__ == "__main__":
    main()
