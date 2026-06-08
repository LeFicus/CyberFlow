# shopify站点.py
import os
from datetime import datetime

import pandas as pd
from scrapy.crawler import CrawlerProcess
from ecommerce_spider.spiders.shopify_crawl import ShopifyCrawlFastSpider


def run_batch(sites: list[dict]):
    """
    sites = [
        {"domain": "...", "category": "..."},
        {"domain": "...", "category": "..."},
    ]
    """
    generated_files = []

    process = CrawlerProcess(settings={
        # ==== 性能（极速版推荐）====
        "CONCURRENT_REQUESTS": 256,
        "CONCURRENT_REQUESTS_PER_DOMAIN": 32,
        "DOWNLOAD_DELAY": 0,
        "AUTOTHROTTLE_ENABLED": False,
        "RETRY_TIMES": 3,
        "LOG_LEVEL": "INFO",
        "ROBOTSTXT_OBEY": False,
        "FEEDS": {},

        # ==== 导出 ====
        "ITEM_PIPELINES": {
            'ecommerce_spider.pipelines.MySQLRedisPipeline': 300,
        },
        "DOWNLOADER_MIDDLEWARES": {
            'scrapy.downloadermiddlewares.useragent.UserAgentMiddleware': None,
            'ecommerce_spider.middlewares.CustomUserAgentMiddleware': 400,
        },
        # 2. MySQL 配置
        "MYSQL_CONFIG": {
            "host": "localhost",
            "user": "root",
            "password": "123456",
            "database": "scraped_data",
            # "database": "test_scrapy_data",

            "charset": "utf8mb4"
        },

        # 3. Redis 配置
        "REDIS_CONFIG": {
            "host": "127.0.0.1",
            "port": 6379,
            "db": 0,
            "decode_responses": True
        },

        # 4. 批量写入大小
        "DB_BATCH_SIZE": 1000,

        # 5. 内存保护：当内存超过一定限制时自动关闭（可选）
        # "MEMUSAGE_LIMIT_MB": 1024,
    })
    today_str = datetime.now().strftime("%Y-%m-%d")  # 当前日期，例如 2025-12-31
    base_data_dir = os.path.join(".", "data", today_str)  # ./data/2025-12-31
    for site in sites:
        domain = site["domain"]
        category = site.get("category", "未知分类")

        site_name = domain.split("//")[-1].replace(".", "_").replace("/", "")
        os.makedirs(base_data_dir, exist_ok=True)  # 创建日期目录

        # ✅ category 作为目录名（可自行再清洗）
        category_dir = category.strip()
        category_dir = category_dir.replace("/", "_")
        export_file = os.path.join(category_dir, f"{site_name}.xlsx")
        export_file = f"{base_data_dir}/{export_file}"
        generated_files.append(export_file)  # <-- 加入列表

        process.crawl(
            ShopifyCrawlFastSpider,
            domain=domain,
            category=category,
            export_file=export_file,  # 👈 关键
        )

    process.start()

if __name__ == "__main__":
    sites = [
        # {"domain": "https://qwertyqop.com", "category": "键盘托架"},
        # {"domain": "https://pantheonkeys.com", "category": "键盘托架"},
        # {"domain": "https://osume.com", "category": "键盘托架"},
        # {"domain": "https://www.keychron.com", "category": "键盘托架"},
        # {"domain": "https://cannonkeys.com", "category": "键盘托架"},
        # {"domain": "https://landingpad.shop", "category": "键盘托架"},
        # {"domain": "https://garmade.com", "category": "电子产品"},
        # {"domain": "https://sparkleskinkorea.com", "category": "个人护理"},
        # {"domain": "https://www.gingerlilyperles.fr", "category": "珠宝首饰（DIY零件）"},
        {"domain": "https://madeinindiabeads.com", "category": "珠宝首饰（DIY零件）"},

    ]

    run_batch(sites)
