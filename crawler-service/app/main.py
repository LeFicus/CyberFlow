"""CyberFlow Crawler Service — v2.0

服务已迁移至 Spring Boot + RabbitMQ + Python asyncio consumers。
Scrapy 商品爬虫仍在此目录下运行，由 crawler-consumer/product_consumer.py 子进程调度。

原 FastAPI 端点 (POST /crawler/site/start 等) 已废弃，
任务触发请通过 Spring Boot Admin :8080 操作。
"""
# FastAPI 入口保留，供将来可能的健康检查或管理端点使用
