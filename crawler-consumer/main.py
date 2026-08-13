"""
爬虫消费者服务入口模块 —— CyberFlow v2.0。

本模块是 crawler-consumer 微服务的主入口，负责：
1. 加载环境变量配置
2. 组装 RabbitMQ 连接 URL
3. 初始化站点、订单和商品三个消费者实例
4. 启动所有消费者并进入异步事件循环
5. 优雅处理 SIGINT/SIGTERM 信号以安全关闭所有消费者

使用方式：
    python main.py
"""

import signal
import time
from threading import Event, Thread
from dotenv import load_dotenv
from loguru import logger
from config import RABBITMQ_USER, RABBITMQ_PASS, RABBITMQ_HOST, RABBITMQ_PORT
from consumers.site_consumer import SiteConsumer
from consumers.order_consumer import OrderConsumer
from consumers.product_consumer import ProductConsumer

load_dotenv()

# 组装 RabbitMQ AMQP 连接 URL
RABBITMQ_URL = f"amqp://{RABBITMQ_USER}:{RABBITMQ_PASS}@{RABBITMQ_HOST}:{RABBITMQ_PORT}/"


def main():
    """启动所有爬虫消费者的主协程。

    初始化 Site、Order 和 Product 消费者，启动各自的 RabbitMQ 连接与消息监听，
    然后进入无限循环保持进程运行。捕获 asyncio.CancelledError 以处理取消信号，
    在 finally 块中确保所有消费者被安全关闭。

    异常:
        asyncio.CancelledError: 当事件循环被取消时捕获并优雅退出
        Exception: 记录任意运行时错误
    """
    logger.info("🚀 Starting CyberFlow v2.0 Consumers...")

    site = SiteConsumer(RABBITMQ_URL)
    order = OrderConsumer(RABBITMQ_URL)
    product = ProductConsumer(RABBITMQ_URL)

    consumers = [site, order, product]
    stop_event = Event()

    def request_stop(signum=None, frame=None):
        logger.info("Stopping consumers...")
        stop_event.set()
        for consumer in consumers:
            consumer.stop()

    signal.signal(signal.SIGTERM, request_stop)
    signal.signal(signal.SIGINT, request_stop)

    threads = [
        Thread(target=consumer.run, name=f"{consumer.queue_name}-consumer", daemon=True)
        for consumer in consumers
    ]
    for thread in threads:
        thread.start()

    while not stop_event.is_set():
        time.sleep(1)


if __name__ == "__main__":
    main()
