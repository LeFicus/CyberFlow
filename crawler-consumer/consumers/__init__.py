"""
消费者层 (consumers) —— RabbitMQ 消息消费者模块。

本包包含所有爬虫任务的消息消费者类：
1. BaseConsumer —— 消费者抽象基类，封装 pika AsyncioConnection 生命周期管理
2. SiteConsumer —— 站点信息爬取消费者，监听 site.crawl 队列
3. OrderConsumer —— 订单数据爬取消费者，监听 order.crawl 队列
4. ProductConsumer —— 产品数据爬取消费者，监听 product.crawl 队列

每个消费者负责从 RabbitMQ 消费任务消息、调用对应的爬虫执行器、
将结果写回数据库，并将执行结果发布到 task.result 队列。
"""
