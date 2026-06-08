import mysql.connector
from mysql.connector import pooling
import redis
import logging
from scrapy.exceptions import DropItem


class MySQLRedisPipeline:
    """
    高效持久化管道：
    1. Redis SADD 集合去重 (支持分布式/增量爬取)
    2. MySQL 批量插入 (利用 executemany 提升 10 倍以上性能)
    3. 异常安全：写入失败时不丢失缓存
    4. 环境适配：dev 模式自动清理 Redis 缓存
    """

    def __init__(self, mysql_config, redis_config, batch_size=50):
        self.mysql_config = mysql_config
        self.redis_config = redis_config
        self.batch_size = batch_size
        self.items_buffer = []  # 内存缓冲区
        self.logger = logging.getLogger(__name__)
        self.db_pool = None
        self.r = None

    @classmethod
    def from_crawler(cls, crawler):
        return cls(
            mysql_config=crawler.settings.get('MYSQL_CONFIG'),
            redis_config=crawler.settings.get('REDIS_CONFIG'),
            batch_size=crawler.settings.get('DB_BATCH_SIZE', 50)
        )

    def open_spider(self, spider):
        # 初始化 MySQL 连接池
        self.db_pool = pooling.MySQLConnectionPool(
            pool_name="woo_pool",
            pool_size=20,
            **self.mysql_config
        )
        # 初始化 Redis 连接
        self.r = redis.Redis(**self.redis_config)
        self.logger.info("✅ Pipeline 启动：MySQL 线程池与 Redis 已就绪")

    def process_item(self, item, spider):
        # 1. 字段校验
        sku = item.get('SKU')
        if not sku:
            raise DropItem("⚠️ 丢弃：缺失 SKU")

        # 2. Redis 实时去重 (基于 SKU)
        # 为不同爬虫设置不同的去重 key
        domain = item.get('原站域名')
        redis_key = f"scraped_skus:{spider.name}:{domain}"
        if self.r.sadd(redis_key, sku) == 0:
            raise DropItem(f"🚫 Redis 已存在 SKU: {sku}")

        # 3. 压入批量缓冲区
        self.items_buffer.append(item)

        # 4. 达到阈值刷入数据库
        if len(self.items_buffer) >= self.batch_size:
            self._flush_to_mysql()

        return item

    def _flush_to_mysql(self):
        """将缓存中的数据批量写入 MySQL"""
        if not self.items_buffer:
            return

        conn = None
        cursor = None

        # 准备 SQL 语句
        sql = """
            INSERT INTO ecommerce_products 
            (sku, name,description, regular_price, categories, images,cf_opingts,custom_category, source_domain, language)
            VALUES (%s, %s,%s, %s, %s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE 
                name=VALUES(name), 
                regular_price=VALUES(regular_price),
                categories=VALUES(categories),
                images=VALUES(images)
        """

        # 转换数据格式
        batch_values = []
        for item in self.items_buffer:
            try:
                val = (
                    item.get('SKU'),
                    item.get('Name'),
                    item.get('Description'),
                    float(item.get('Regular price', 0) or 0),
                    item.get('Categories'),
                    item.get('Images'),
                    item.get('cf_opingts'),
                    item.get('自定义分类'),
                    item.get('原站域名'),
                    item.get('语言')
                )
                batch_values.append(val)
            except Exception as e:
                self.logger.error(f"数据转换异常 SKU {item.get('SKU')}: {e}")

        # 执行批量写入
        try:
            conn = self.db_pool.get_connection()
            cursor = conn.cursor()
            cursor.executemany(sql, batch_values)
            conn.commit()
            self.logger.info(f"💾 批量入库成功：{len(batch_values)} 条记录")
            self.items_buffer = []  # 只有成功后才清空缓存
        except Exception as e:
            if conn: conn.rollback()
            self.logger.error(f"❌ MySQL 批量写入失败: {e}")
        finally:
            if cursor: cursor.close()
            if conn: conn.close()

    def close_spider(self, spider):
        # 1. 强刷余量数据
        if self.items_buffer:
            self._flush_to_mysql()

        # 2. 统一获取域名（尝试多种途径确保拿到 domain）
        # 方案 A: 从 spider 的属性拿（推荐）
        domain = getattr(spider, 'domain', '')
        if '://' in domain:
            from urllib.parse import urlparse
            domain = urlparse(domain).netloc

        # 如果 A 拿不到，尝试从 allowed_domains 拿
        if not domain and hasattr(spider, 'allowed_domains'):
            domain = spider.allowed_domains[0]

        # 构造与 process_item 完全一致的 Key
        redis_key = f"scraped_skus:{spider.name}:{domain}"

        mode = getattr(spider, 'mode', 'prod')

        if mode == 'dev':
            self.r.delete(redis_key)
            self.logger.info(f"🗑️ [Dev Mode] 已清理站点 {domain} 的 Redis 缓存")
        else:
            total_count = self.r.scard(redis_key)
            self.logger.info(f"📊 [Prod Mode] 站点 {domain} 持久化指纹总数: {total_count}")