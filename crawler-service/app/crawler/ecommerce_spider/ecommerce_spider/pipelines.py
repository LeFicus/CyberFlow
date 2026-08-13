"""
Scrapy 数据管道 — MySQLRedisPipeline 持久化管道

本管道实现了高效的商品数据持久化策略，兼顾性能和去重：

流程:
    1. 字段校验    — 检查 SKU 是否存在，缺失则丢弃
    2. Redis 去重  — 基于 SKU 使用 Redis Set 集合去重（支持分布式增量爬取）
    3. 批量缓冲    — 将 Item 缓存到内存缓冲区
    4. 批量入库    — 达到阈值时使用 executemany 批量写入 MySQL（10x+ 性能提升）
    5. 异常安全    — 写入失败时回滚事务，不清空缓存（防止数据丢失）
    6. 环境适配    — dev 模式下爬取完成后自动清理 Redis 缓存

数据库:
    MySQL  : ecommerce_products 表（使用 ON DUPLICATE KEY UPDATE 处理重复）
    Redis  : scraped_skus:{spider_name}:{domain} Set 集合
"""

import mysql.connector
from mysql.connector import pooling
import redis
import logging
from scrapy.exceptions import DropItem


class MySQLRedisPipeline:
    """
    MySQL + Redis 联合持久化管道

    设计理念:
        - Redis 作为去重缓存层（速度快、支持分布式）
        - MySQL 作为持久化存储层（数据可靠、支持复杂查询）
        - 批量缓冲写入策略，减少数据库网络往返次数

    属性:
        mysql_config  (dict): MySQL 连接配置（host, user, password, database, charset）
        redis_config  (dict): Redis 连接配置（host, port, db, decode_responses）
        batch_size    (int) : 批量写入阈值，缓冲区达到此规模时触发 flush
        items_buffer  (list): 内存缓冲区，暂存待写入的 Item
        db_pool       : MySQL 连接池（线程安全复用）
        r             : Redis 客户端
    """

    def __init__(self, mysql_config, redis_config, batch_size=50):
        """
        初始化管道

        Args:
            mysql_config (dict): MySQL 连接配置字典
            redis_config (dict): Redis 连接配置字典
            batch_size   (int) : 批量写入阈值（默认 50）
        """
        self.mysql_config = mysql_config
        self.redis_config = redis_config
        self.batch_size = batch_size
        self.items_buffer = []  # 内存缓冲区，批量写入时清空
        self.logger = logging.getLogger(__name__)
        self.db_pool = None  # MySQL 连接池（在 open_spider 时初始化）
        self.r = None        # Redis 客户端（在 open_spider 时初始化）

    @classmethod
    def from_crawler(cls, crawler):
        """
        从 Crawler 对象创建 Pipeline 实例（Scrapy 工厂方法）

        自动从 settings 中读取 MySQL 和 Redis 配置。

        Args:
            crawler: Scrapy Crawler 对象

        Returns:
            MySQLRedisPipeline: 管道实例
        """
        return cls(
            mysql_config=crawler.settings.get('MYSQL_CONFIG'),
            redis_config=crawler.settings.get('REDIS_CONFIG'),
            batch_size=crawler.settings.get('DB_BATCH_SIZE', 50)
        )

    def open_spider(self, spider):
        """
        Spider 启动时回调 — 初始化数据库连接

        创建 MySQL 连接池和 Redis 客户端，确保后续操作可复用连接。
        连接池支持最多 20 个并发连接，适用于多并发爬取场景。

        Args:
            spider: 启动的 Spider 实例
        """
        # 初始化 MySQL 连接池（复用连接，避免频繁创建销毁）
        self.db_pool = pooling.MySQLConnectionPool(
            pool_name="shopify_pool",  # 连接池名称
            pool_size=20,           # 最大连接数
            **self.mysql_config     # 展开 host, user, password, database 等配置
        )
        # 初始化 Redis 连接
        self.r = redis.Redis(**self.redis_config)
        self.logger.info("✅ Pipeline 启动：MySQL 线程池与 Redis 已就绪")

    def process_item(self, item, spider):
        """
        处理每个爬虫产出的 Item

        处理步骤:
            1. 校验 SKU 字段，缺失则引发 DropItem 丢弃
            2. 以 SKU 为键检查 Redis 去重集合
            3. 通过去重检查后加入内存缓冲区
            4. 缓冲区达到 batch_size 阈值时批量写入 MySQL

        Args:
            item  (dict): 爬虫产出的商品数据字典
            spider      : Spider 实例

        Returns:
            dict: 处理后的 item（继续向下传递）

        Raises:
            DropItem: SKU 缺失或 Redis 中已存在时丢弃该 item
        """
        # 1. 字段校验 — SKU 是必填字段
        sku = item.get('SKU')
        if not sku:
            raise DropItem("⚠️ 丢弃：缺失 SKU")

        # 2. Redis 实时去重 — 基于 SKU 值
        # 为不同爬虫和不同域名维护独立的去重集合
        domain = item.get('原站域名')
        redis_key = f"scraped_skus:{spider.name}:{domain}"
        # SADD 返回 0 表示元素已存在（重复），返回 1 表示新增成功
        if self.r.sadd(redis_key, sku) == 0:
            raise DropItem(f"🚫 Redis 已存在 SKU: {sku}")

        # 3. 加入批量缓冲区 — 暂存到内存等待批量写入
        self.items_buffer.append(item)

        # 4. 达到阈值时触发批量写入 MySQL
        if len(self.items_buffer) >= self.batch_size:
            self._flush_to_mysql()

        return item

    def _flush_to_mysql(self):
        """
        将内存缓冲区中的数据批量写入 MySQL

        使用 executemany 一次性执行多条 INSERT 语句，性能比逐条插入提升 10 倍以上。
        使用 ON DUPLICATE KEY UPDATE 处理 SKU 唯一键冲突，自动更新已有记录。

        异常处理:
            - 写入失败时回滚事务，保留缓冲区数据不丢失
            - 成功后才清空缓冲区（保证数据安全）
        """
        if not self.items_buffer:
            return

        conn = None
        cursor = None

        # ========== SQL 语句 ==========
        # 使用 REPLACE 语义：新记录插入，已有记录更新
        # 主键为自增 id，SKU 上有唯一索引确保去重
        sql = """
            INSERT INTO ecommerce_products 
            (sku, name, description, regular_price, categories, images, cf_opingts, custom_category, source_domain, language)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE 
                name=VALUES(name), 
                regular_price=VALUES(regular_price),
                categories=VALUES(categories),
                images=VALUES(images)
        """

        # ========== 数据格式转换 ==========
        # 将 Item 字典转换为 SQL 参数元组列表
        batch_values = []
        for item in self.items_buffer:
            try:
                val = (
                    item.get('SKU'),
                    item.get('Name'),
                    item.get('Description'),
                    float(item.get('Regular price', 0) or 0),  # 价格转为 float，空值转为 0
                    item.get('Categories'),
                    item.get('Images'),
                    item.get('cf_opingts'),      # 商品属性选项
                    item.get('自定义分类'),        # 业务自定义分类
                    item.get('原站域名'),          # 原始站点域名
                    item.get('语言'),              # 语言代码（如 'en'）
                )
                batch_values.append(val)
            except Exception as e:
                self.logger.error(f"数据转换异常 SKU {item.get('SKU')}: {e}")

        # ========== 执行批量写入 ==========
        try:
            conn = self.db_pool.get_connection()   # 从连接池获取连接
            cursor = conn.cursor()
            cursor.executemany(sql, batch_values)   # 批量执行（核心性能优化点）
            conn.commit()
            self.logger.info(f"💾 批量入库成功：{len(batch_values)} 条记录")
            self.items_buffer = []  # 成功后才清空缓存（失败时不丢数据）
        except Exception as e:
            if conn:
                conn.rollback()
            self.logger.error(f"❌ MySQL 批量写入失败: {e}")
        finally:
            if cursor:
                cursor.close()
            if conn:
                conn.close()

    def close_spider(self, spider):
        """
        Spider 关闭时回调 — 刷新缓存并清理资源

        执行操作:
            1. 强制刷新缓冲区中剩余的未写入数据
            2. 根据运行模式（dev / prod）决定是否清理 Redis 去重缓存
               - dev  模式: 删除 Redis 中的去重集合（方便重复测试）
               - prod 模式: 保留 Redis 去重数据并输出统计信息

        Args:
            spider: 将要关闭的 Spider 实例
        """
        # 1. 强制刷新剩余数据 — 缓冲区可能还有不足 batch_size 的数据
        if self.items_buffer:
            self._flush_to_mysql()

        # 2. 提取站点域名 — 用于构造 Redis key
        # 方案 A: 从 spider 的 domain 属性获取（推荐）
        domain = getattr(spider, 'domain', '')
        if '://' in domain:
            from urllib.parse import urlparse
            domain = urlparse(domain).netloc  # 从 URL 中提取纯域名部分

        # 方案 B: 从 allowed_domains 获取（兜底）
        if not domain and hasattr(spider, 'allowed_domains'):
            domain = spider.allowed_domains[0]

        # 构造与 process_item 完全一致的 Redis Key
        redis_key = f"scraped_skus:{spider.name}:{domain}"

        # 获取运行模式（dev 或 prod）
        mode = getattr(spider, 'mode', 'prod')

        # 3. 根据模式处理 Redis 缓存
        if mode == 'dev':
            # 开发模式：清理 Redis 缓存，方便下次重新测试
            self.r.delete(redis_key)
            self.logger.info(f"🗑️ [Dev Mode] 已清理站点 {domain} 的 Redis 缓存")
        else:
            # 生产模式：保留 Redis 数据作为永久去重指纹库
            total_count = self.r.scard(redis_key)
            self.logger.info(f"📊 [Prod Mode] 站点 {domain} 持久化指纹总数: {total_count}")
