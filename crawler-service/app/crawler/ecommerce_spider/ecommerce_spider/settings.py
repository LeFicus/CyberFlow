"""
Scrapy 配置文件 — ecommerce_spider 项目的全局默认设置

此文件定义了 Scrapy 项目的基础配置默认值。实际运行中，
调用方（shopify站点.py 或 product_consumer.py）会在 CrawlerProcess 中
动态覆盖关键参数（并发、延迟、Pipeline、MySQL/Redis 配置等）。

详细配置参考: https://docs.scrapy.org/en/latest/topics/settings.html
"""

import os
from urllib.parse import unquote, urlparse

# ========== 项目基本配置 ==========
# 爬虫机器人名称，用于标识该项目
BOT_NAME = "ecommerce_spider"
PRODUCT_PLATFORM_CONFIGS = os.getenv("PRODUCT_PLATFORM_CONFIGS", "{}") or "{}"
PRODUCT_REDIS_ENABLED = os.getenv("PRODUCT_REDIS_ENABLED", "true").lower() in {"true", "1", "yes"}

# 指定爬虫模块的查找路径
SPIDER_MODULES = ["ecommerce_spider.spiders"]

# 新建爬虫的默认生成模块
NEWSPIDER_MODULE = "ecommerce_spider.spiders"

# ========== Scrapy Addons (v2.0+) ==========
# 扩展插件配置，此处为空表示不启用额外 addon
ADDONS = {}

EXTENSIONS = {"ecommerce_spider.crawl_result.CrawlResultExtension": 500}
DOWNLOADER_MIDDLEWARES = {
    "ecommerce_spider.middlewares.ProductRequestPolicyMiddleware": 540,
    "ecommerce_spider.middlewares.BigCommerceRequestsFallbackMiddleware": 560,
}
DOWNLOADER_CLIENTCONTEXTFACTORY = "ecommerce_spider.request_policy.ProductTLSContextFactory"

# ========== 爬取礼仪配置 ==========
# 是否遵守目标站点的 robots.txt 协议
# 生产环境中应设为 True，数据采集任务中通常设为 False（在启动脚本中覆盖）
ROBOTSTXT_OBEY = True

# ========== 并发与限速配置 ==========
# 全局并发请求数（可在启动时覆盖以提升性能）
# CONCURRENT_REQUESTS = 16

# 每个域名的最大并发请求数
# 设置为 1 是最保守的策略，生产环境中会在启动脚本中提升
CONCURRENT_REQUESTS_PER_DOMAIN = 1

# 基础下载延迟（秒），配合随机化避免请求过于规律
DOWNLOAD_DELAY = 1

# ========== Cookie 设置 ==========
# 是否启用 Cookie，部分站点不需要 Cookie，关闭可提升性能
# COOKIES_ENABLED = False

# ========== Telnet 控制台 ==========
# 是否启用 Telnet 调试控制台（生产环境建议关闭）
# TELNETCONSOLE_ENABLED = False

# ========== 默认请求头 ==========
# 全局默认请求头，由 CustomUserAgentMiddleware 动态覆盖 User-Agent
# DEFAULT_REQUEST_HEADERS = {
#     "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
#     "Accept-Language": "en",
# }

# ========== 爬虫中间件配置 ==========
# 在此启用/禁用自定义爬虫中间件，数值越小优先级越高（越靠近引擎）
# SPIDER_MIDDLEWARES = {
#     "ecommerce_spider.middlewares.EcommerceSpiderSpiderMiddleware": 543,
# }

# ========== 下载器中间件配置 ==========
# 在此启用/禁用自定义下载器中间件
# DOWNLOADER_MIDDLEWARES = {
#     "ecommerce_spider.middlewares.EcommerceSpiderDownloaderMiddleware": 543,
# }

# ========== 扩展组件 ==========
# 在此启用/禁用 Scrapy 扩展
# EXTENSIONS = {
#     "scrapy.extensions.telnet.TelnetConsole": None,
# }

# ========== Item Pipelines 配置 ==========
# 数据管道的处理链，数值越小优先级越高（范围 0-1000）
# 实际运行中由启动脚本动态设置 MySQLRedisPipeline
ITEM_PIPELINES = {
    "ecommerce_spider.pipelines.MySQLRedisPipeline": 300,
}


def _mysql_config():
    parsed = urlparse(
        os.getenv(
            "SCRAPED_DB_URL",
            "mysql+pymysql://root:123456@localhost:3306/scraped_data",
        )
    )
    return {
        "host": parsed.hostname or "localhost",
        "port": parsed.port or 3306,
        "user": unquote(parsed.username or "root"),
        "password": unquote(parsed.password or ""),
        "database": parsed.path.lstrip("/") or "scraped_data",
        "charset": "utf8mb4",
        "autocommit": False,
    }


def _redis_config():
    parsed = urlparse(os.getenv("REDIS_URL", "redis://127.0.0.1:6379/0"))
    config = {
        "host": parsed.hostname or "127.0.0.1",
        "port": parsed.port or 6379,
        "db": int(parsed.path.lstrip("/") or 0),
        "decode_responses": True,
    }
    if parsed.password:
        config["password"] = unquote(parsed.password)
    if parsed.scheme == "rediss":
        config["ssl"] = True
    return config


MYSQL_CONFIG = _mysql_config()
REDIS_CONFIG = _redis_config()
DB_BATCH_SIZE = int(os.getenv("DB_BATCH_SIZE", "250"))
PRODUCT_DOMAIN_CURRENCIES = os.getenv("PRODUCT_DOMAIN_CURRENCIES") or "{}"
PRODUCT_CRAWL_PROXY_URL = os.getenv("PRODUCT_CRAWL_PROXY_URL", "")
PRODUCT_CRAWL_CA_BUNDLE = os.getenv("PRODUCT_CRAWL_CA_BUNDLE", "")
PRODUCT_DISCOVERY_MAX_PAGES = int(os.getenv("PRODUCT_DISCOVERY_MAX_PAGES", "100"))
TELNETCONSOLE_ENABLED = False

# ========== AutoThrottle 自动限速 ==========
# AutoThrottle 根据服务器负载动态调整下载延迟，是友好的爬取策略
# 但在批量数据采集场景下通常关闭，由固定延迟控制
# AUTOTHROTTLE_ENABLED = True
# AUTOTHROTTLE_START_DELAY = 5      # 初始延迟
# AUTOTHROTTLE_MAX_DELAY = 60       # 最大延迟
# AUTOTHROTTLE_TARGET_CONCURRENCY = 1.0  # 目标并发量
# AUTOTHROTTLE_DEBUG = False        # 是否输出限速调试信息

# ========== HTTP 缓存配置 ==========
# 启用 HTTP 缓存可避免重复下载未变化的页面，适合开发调试
# 生产采集任务中通常关闭以获取最新数据
# HTTPCACHE_ENABLED = True
# HTTPCACHE_EXPIRATION_SECS = 0       # 过期时间（0=永不过期）
# HTTPCACHE_DIR = "httpcache"         # 缓存目录
# HTTPCACHE_IGNORE_HTTP_CODES = []    # 不缓存的状态码列表
# HTTPCACHE_STORAGE = "scrapy.extensions.httpcache.FilesystemCacheStorage"

# ========== Feed 导出配置 ==========
# 设置导出文件的编码格式为 UTF-8，确保中文字符正确输出
FEED_EXPORT_ENCODING = "utf-8"
