"""Preview (default), or apply audited product quality rules in 500-row transactions.

Run with the crawler dependencies and ecommerce_spider on PYTHONPATH.
Uses SCRAPED_DB_URL; --apply is required to modify product data.
"""
import argparse
import json
import os
from urllib.parse import urlparse, unquote
import pymysql
from ecommerce_spider.normalization import adjusted_price_usd, usable_product_images


def run(connection, apply=False, batch_size=500):
    totals = dict(scanned=0, adjusted=0, excluded=0, cleaned=0, changed=0)
    with connection.cursor() as cursor:
        cursor.execute("SELECT COALESCE(MAX(id),0) FROM ecommerce_products")
        snapshot = cursor.fetchone()["COALESCE(MAX(id),0)"]
    connection.commit()
    after = 0
    while after < snapshot:
        try:
            with connection.cursor() as cursor:
                cursor.execute("SELECT id,sku,source_domain,regular_price,images,original_price_usd,image_usable FROM ecommerce_products WHERE id>%s AND id<=%s ORDER BY id LIMIT %s" + (" FOR UPDATE" if apply else ""), (after,snapshot,batch_size))
                rows = cursor.fetchall()
                if not rows:
                    break
                for row in rows:
                    after = row['id']; totals['scanned'] += 1
                    price = float(row['regular_price'] or 0)
                    adjusted = adjusted_price_usd(price, row['source_domain'], row['sku'])
                    images = usable_product_images(row['images'], row['source_domain'])
                    usable = int(bool(images))
                    # Preserve original invalid URLs in the row; image_usable excludes them.
                    stored_images = images if usable else row['images']
                    repriced = adjusted != price
                    cleaned = stored_images != row['images']
                    if not repriced and not cleaned and usable == row['image_usable']:
                        continue
                    totals['changed'] += 1
                    totals['adjusted'] += int(repriced)
                    totals['excluded'] += int(not usable and row['image_usable'] != 0)
                    totals['cleaned'] += int(cleaned)
                    if apply:
                        cursor.execute("INSERT IGNORE INTO product_policy_audit(product_id,regular_price,images,original_price_usd,image_usable) VALUES(%s,%s,%s,%s,%s)", (row['id'],row['regular_price'],row['images'],row['original_price_usd'],row['image_usable']))
                        cursor.execute("UPDATE ecommerce_products SET regular_price=%s,images=%s,image_usable=%s,original_price_usd=%s WHERE id=%s", (adjusted if repriced else row['regular_price'],stored_images,usable,row['regular_price'] if repriced and row['original_price_usd'] is None else row['original_price_usd'],row['id']))
            connection.commit()
        except Exception:
            connection.rollback()
            raise
    print(json.dumps({'mode':'apply' if apply else 'preview','snapshot_id':snapshot,**totals}))
    return totals


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    url=urlparse(os.environ['SCRAPED_DB_URL'])
    with pymysql.connect(host=url.hostname,port=url.port or 3306,user=unquote(url.username or ''),password=unquote(url.password or ''),database=url.path.lstrip('/'),charset='utf8mb4',cursorclass=pymysql.cursors.DictCursor,autocommit=False) as connection:
        run(connection,args.apply)
