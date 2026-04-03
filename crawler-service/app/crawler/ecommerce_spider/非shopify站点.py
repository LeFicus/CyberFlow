from scrapy.crawler import CrawlerProcess
from ecommerce_spider.spiders.woo_crawl import WooCrawlSpider


def run(domain: str, category: str = "未知分类", config_file: str = None,mode="dev"):
    if not domain or not domain.startswith("http"):
        print("请传入正确的域名，例如：https://bazaarica.com/sitemaps/en-us/sitemap.xml")
        return

    # 动态生成文件名
    site_name = domain.split("//")[-1].replace(".", "_")
    export_file = f"{site_name}.xlsx"

    process = CrawlerProcess(settings={
        "PANDAS_EXPORT_FILE": export_file,
        "PANDAS_FIELDS": [
            "SKU", "Name", "Categories", "Regular price", "cf_opingts",
            "Description", "Images", "自定义分类", "原站域名", "分布网站识别", "语言"
        ],
        # ==== 核心提速设置 ====
        "CONCURRENT_REQUESTS": 64,  # 全局并发请求数，建议 16-64
        "CONCURRENT_REQUESTS_PER_DOMAIN": 16,  # 每个域名最大并发（防单个站点被封）
        "CONCURRENT_REQUESTS_PER_IP": 0,  # 通常保持 0，除非你用代理

        "DOWNLOAD_DELAY": 0.5,  # 基础延迟降到 0.5 秒
        "RANDOMIZE_DOWNLOAD_DELAY": True,  # 随机化延迟（0.25-0.75秒），防封


        # ==== 其他性能优化 ====
        "RETRY_TIMES": 3,  # 减少重试次数（避免卡住）
        "DOWNLOAD_TIMEOUT": 15,  # 超时 15 秒，快速丢弃慢请求
        "REDIRECT_ENABLED": False,  # 禁用重定向，节省时间
        "COOKIES_ENABLED": False,  # Woo 站一般不需要 cookie
        "LOG_LEVEL": "INFO",  # 减少日志输出
        "HTTPCACHE_ENABLED": True,  # 启用缓存，重复跑时超快（开发测试用）

        # ==== 其他原有设置保持不变 ====
        "ITEM_PIPELINES": {
            # 'ecommerce_spider.pipelines.PandasExporter': 300,
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
        "DB_BATCH_SIZE": 500,

        # 5. 内存保护：当内存超过一定限制时自动关闭（可选）
        "MEMUSAGE_LIMIT_MB": 1024,

        # Scrapy 内置的自动关闭机制
        # "CLOSESPIDER_ITEMCOUNT": 10 if mode == "dev" else 0,
    })
    process.crawl(WooCrawlSpider, domain=domain, category=category, config_file=config_file)
    process.start()          # 阻塞直到爬完


if __name__ == "__main__":
    url= "https://www.charming-florist.com/sitemap_index.xml"
    category = "鲜花"
    config_file = "configs/selectors/test.json"
    # mode = "dev"
    # 这里改成你想爬的站
    # run(url, category, config_file, mode)
    run(url, category, config_file)