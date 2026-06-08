import os
from dotenv import load_dotenv

load_dotenv()

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", "5672"))
RABBITMQ_USER = os.getenv("RABBITMQ_USER", "admin")
RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "admin123")

DATABASE_URL = os.getenv("DATABASE_URL", "mysql+pymysql://root:123456@localhost:3306/cyberflow")
SCRAPED_DB_URL = os.getenv("SCRAPED_DB_URL", "mysql+pymysql://root:123456@localhost:3306/scraped_data")

REDIS_URL = os.getenv("REDIS_URL", "redis://127.0.0.1:6379/0")
SCRAPY_PROJECT_PATH = os.getenv("SCRAPY_PROJECT_PATH", "")  # Docker override

# Exchange & Queue names — must match Spring Boot RabbitMQConfig
EXCHANGE_TASKS = "crawler.tasks"
QUEUE_SITE_CRAWL = "site.crawl"
QUEUE_ORDER_CRAWL = "order.crawl"
QUEUE_PRODUCT_CRAWL = "product.crawl"
QUEUE_TASK_RESULT = "task.result"
