"""
ecommerce_spider 子包 — Scrapy 爬虫项目核心

标准 Scrapy 项目结构，包含：
    - spiders/    : 爬虫实现（Shopify）
    - items.py    : Item 模型定义
    - pipelines.py: 数据管道（MySQL + Redis 去重）
    - middlewares.py: 自定义中间件（User-Agent 轮换等）
    - settings.py : Scrapy 全局设置
"""
