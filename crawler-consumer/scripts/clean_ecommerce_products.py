"""Apply the current product quality rules to existing scraped products."""

import os
from decimal import Decimal
from urllib.parse import urlparse

import pymysql

from ecommerce_spider.normalization import (
    MAX_PRODUCT_PRICE_USD,
    has_content,
    normalize_category,
    normalize_name,
)


def mysql_config():
    parsed = urlparse(os.environ.get("SCRAPED_DB_URL", "mysql+pymysql://root:123456@mysql:3306/scraped_data"))
    return {
        "host": parsed.hostname or "mysql",
        "port": parsed.port or 3306,
        "user": parsed.username or "root",
        "password": parsed.password or "123456",
        "database": (parsed.path or "/scraped_data").lstrip("/"),
        "charset": "utf8mb4",
    }


def main():
    conn = pymysql.connect(**mysql_config())
    deleted = 0
    updated_categories = 0
    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute(
                "SELECT id, name, description, images, regular_price, categories "
                "FROM ecommerce_products"
            )
            rows = cursor.fetchall()
            for row in rows:
                try:
                    normalize_name(row.get("name"))
                    price = Decimal(str(row.get("regular_price") or 0))
                    valid = (
                        price <= Decimal(str(MAX_PRODUCT_PRICE_USD))
                        and has_content(row.get("description"))
                        and has_content(row.get("images"))
                    )
                except Exception:
                    valid = False

                if not valid:
                    cursor.execute("DELETE FROM ecommerce_products WHERE id = %s", (row["id"],))
                    deleted += 1
                    continue

                normalized = normalize_category(row.get("categories"))
                if normalized != (row.get("categories") or ""):
                    cursor.execute(
                        "UPDATE ecommerce_products SET categories = %s WHERE id = %s",
                        (normalized, row["id"]),
                    )
                    updated_categories += 1
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

    print(f"scanned={len(rows)} deleted={deleted} updated_categories={updated_categories} remaining={len(rows) - deleted}")


if __name__ == "__main__":
    main()
