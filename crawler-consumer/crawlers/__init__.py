"""
爬虫执行器层 (crawlers) —— 实际执行爬取操作的模块。

本包包含负责直接调用外部 API 执行数据抓取的类：
1. AsyncSiteCrawler —— 登录管理平台 API，获取站点域名列表和站点映射信息
2. AsyncOrderCrawler —— 登录支付平台 API，按 order_id 增量拉取订单数据

所有爬虫均基于 aiohttp 实现异步 HTTP 请求，支持分页查询和增量爬取。
"""
