"""Backfill and enforce the stable product identity on existing data."""

import os
from urllib.parse import urlparse

import pymysql

from ecommerce_spider.normalization import product_dedupe_key


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
        with conn.cursor(pymysql.cursors.DictCursor) as cur:
            cur.execute(
                "SELECT COUNT(*) AS n FROM information_schema.columns "
                "WHERE table_schema=DATABASE() AND table_name='ecommerce_products' AND column_name='dedupe_key'"
            )
            if cur.fetchone()["n"] == 0:
                cur.execute("ALTER TABLE ecommerce_products ADD COLUMN dedupe_key VARCHAR(768) NULL")
            cur.execute("SELECT id, source_domain, name, images FROM ecommerce_products")
            rows = cur.fetchall()
            for row in rows:
                cur.execute(
                    "UPDATE ecommerce_products SET dedupe_key=%s WHERE id=%s",
                    (product_dedupe_key(row.get("source_domain"), row.get("name"), row.get("images")), row["id"]),
                )
            cur.execute(
                "SELECT dedupe_key, GROUP_CONCAT(id ORDER BY id) ids FROM ecommerce_products "
                "WHERE dedupe_key IS NOT NULL GROUP BY dedupe_key HAVING COUNT(*) > 1"
            )
            duplicate_groups = cur.fetchall()
            deleted = 0
            for group in duplicate_groups:
                ids = [int(value) for value in str(group["ids"]).split(",")]
                if len(ids) > 1:
                    placeholders = ",".join(["%s"] * (len(ids) - 1))
                    cur.execute(f"DELETE FROM ecommerce_products WHERE id IN ({placeholders})", tuple(ids[1:]))
                    deleted += len(ids) - 1
            cur.execute(
                "SELECT COUNT(*) FROM information_schema.statistics "
                "WHERE table_schema=DATABASE() AND table_name='ecommerce_products' "
                "AND index_name='uk_product_dedupe'"
            )
            if cur.fetchone()["COUNT(*)"] == 0:
                cur.execute("ALTER TABLE ecommerce_products ADD UNIQUE KEY uk_product_dedupe (dedupe_key)")
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
    print(f"scanned={len(rows)} duplicate_groups={len(duplicate_groups)} deleted={deleted}")


if __name__ == "__main__":
    main()
