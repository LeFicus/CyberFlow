"""
配置模块 —— 管理爬虫消费者服务的所有环境变量与常量。

本模块负责：
1. 通过 python-dotenv 加载 .env 文件中的环境变量
2. 定义 RabbitMQ 连接参数（主机、端口、用户名、密码）
3. 定义数据库连接 URL（主数据库、爬取结果数据库）
4. 定义 Redis 连接 URL
5. 定义 RabbitMQ Exchange 与 Queue 的名称常量（与 Spring Boot 端对齐）

所有配置项均提供合理的默认值，可在不配置 .env 文件的情况下运行。
"""

import os
from dotenv import load_dotenv

load_dotenv()

# ========== RabbitMQ 连接参数 ==========
RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "admin")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "admin123")

# ========== 数据库连接 URL ==========
DATABASE_URL = os.getenv("DATABASE_URL", "mysql+pymysql://root:123456@localhost:3306/cyberflow")
SCRAPED_DB_URL = os.getenv("SCRAPED_DB_URL", "mysql+pymysql://root:123456@localhost:3306/scraped_data")

# ========== Redis 连接 ==========
REDIS_URL = os.getenv("REDIS_URL", "redis://127.0.0.1:6379/0")

# ========== 平台配置（后台 payload 优先，这些值仅作本地兜底） ==========
ADMIN_API_BASE_URL = os.getenv("ADMIN_API_BASE_URL", "")
ADMIN_API_USERNAME = os.getenv("ADMIN_API_USERNAME", "")
ADMIN_API_PASSWORD = os.getenv("ADMIN_API_PASSWORD", "")

PAYMENT_API_BASE_URL = os.getenv("PAYMENT_API_BASE_URL", "")
PAYMENT_API_ACCOUNT = os.getenv("PAYMENT_API_ACCOUNT", "")
PAYMENT_API_PASSWORD = os.getenv("PAYMENT_API_PASSWORD", "")

VERIFY_SSL = os.getenv("VERIFY_SSL", "true").lower() == "true"

# ========== Scrapy 项目路径（Docker 部署时可覆盖） ==========
SCRAPY_PROJECT_PATH = os.getenv("SCRAPY_PROJECT_PATH", "")

# ========== RabbitMQ Exchange 与 Queue 名称（须与 Spring Boot RabbitMQConfig 一致） ==========
EXCHANGE_TASKS = "crawler.tasks"
QUEUE_SITE_CRAWL = "site.crawl"
QUEUE_ORDER_CRAWL = "order.crawl"
QUEUE_PRODUCT_CRAWL = "product.crawl"
QUEUE_TASK_RESULT = "task.result"
