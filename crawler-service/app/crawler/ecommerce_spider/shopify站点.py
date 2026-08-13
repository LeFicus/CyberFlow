"""
Shopify 站点批量爬虫启动模块

提供 run_batch() 函数，一次性批量启动多个 Shopify 站点的商品爬虫。
每个站点使用 ShopifyCrawlFastSpider 爬虫，通过 Shopify products.json 接口
快速采集商品数据，并写入 MySQL + Redis 去重管道。

架构说明:
    - 使用 Scrapy CrawlerProcess 统一管理所有 Spider 实例
    - 每个站点独立爬取，按 分类目录/站点名称.xlsx 路径导出文件
    - 配置文件硬编码了高并发策略（256 并发请求、无延迟）

使用方式:
    >>> from app.crawler.ecommerce_spider.shopify站点 import run_batch
    >>> run_batch(sites)
"""

import os
from datetime import datetime

import pandas as pd
from scrapy.crawler import CrawlerProcess
from ecommerce_spider.spiders.shopify_crawl import ShopifyCrawlFastSpider


def run_batch(sites: list[dict]):
    """
    批量启动 Shopify 站点商品爬虫

    为每个站点创建独立的 ShopifyCrawlFastSpider 实例，
    使用同一个 CrawlerProcess 统一调度和管理所有爬虫。

    数据流:
        Shopify API (products.json) → ShopifyCrawlFastSpider
            → MySQLRedisPipeline (Redis 去重 + MySQL 批量入库)
            → 导出 Excel 文件到 ./data/{日期}/{分类}/{站点名}.xlsx

    配置要点:
        - CONCURRENT_REQUESTS=256         : 全局高并发
        - CONCURRENT_REQUESTS_PER_DOMAIN=32 : 每域名最大并发
        - DOWNLOAD_DELAY=0                 : 无延迟极速采集
        - AUTO_THROTTLE=False              : 关闭自适应限速
        - ROBOTSTXT_OBEY=False             : 忽略 robots.txt

    Args:
        sites (list[dict]): 站点列表，每个元素包含:
            domain   (str): 站点完整 URL（如 "https://example.com"）
            category (str): 业务自定义分类（如 "电子产品"、"服饰与配饰"）
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
    # ========== 本地调试入口 ==========
    # 直接在终端运行 python shopify站点.py 可进行单次调试
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
