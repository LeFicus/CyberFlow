"""
app 包 — CyberFlow 爬虫服务主应用模块

本包包含爬虫服务的核心配置、数据模型、业务逻辑和 Scrapy 爬虫实现。
服务架构：
    - core/     : 配置管理与数据库连接
    - model/    : ORM 数据模型与请求体定义
    - services/ : 站点爬虫、订单爬虫、收录统计等业务服务
    - crawler/  : Scrapy 电商商品爬虫
"""
