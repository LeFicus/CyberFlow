"""
数据库访问层 (db) —— 爬虫消费者服务的数据持久化模块。

本包提供：
1. CursorRepository —— 管理爬取游标 (crawl_cursor) 的读写，实现增量爬取
2. ProductRepository —— 管理爬取结果数据库 (scraped_data) 的连接池

所有数据库操作均基于 aiomysql 异步驱动，支持连接池管理与自动提交。
"""
