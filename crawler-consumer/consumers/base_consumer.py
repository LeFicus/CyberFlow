"""
消费者抽象基类 —— 封装 RabbitMQ 消费者的通用生命周期。

本模块使用 pika 的 BlockingConnection 实现消息消费，
提供连接管理、消息接收、任务分发、优雅关闭等通用功能。
所有具体消费者（Site、Order、Product）均继承此类并实现 process() 方法。
"""

import json
import time
from abc import ABC, abstractmethod
from loguru import logger
import pika
from pika.exceptions import AMQPConnectionError, ChannelClosedByBroker
from config import (
    EXCHANGE_TASKS,
    QUEUE_ORDER_CRAWL,
    QUEUE_PRODUCT_CRAWL,
    QUEUE_SITE_CRAWL,
    QUEUE_TASK_RESULT,
)

EXCHANGE_DLX = "crawler.tasks.dlx"
QUEUE_TASK_DEAD = "task.dead"
RK_DEAD = "crawler.task.dead"
QUEUE_BINDINGS = {
    QUEUE_SITE_CRAWL: "crawler.task.site",
    QUEUE_ORDER_CRAWL: "crawler.task.order",
    QUEUE_PRODUCT_CRAWL: "crawler.task.product",
    QUEUE_TASK_RESULT: "crawler.task.result",
}


class BaseConsumer(ABC):
    """RabbitMQ 消费者抽象基类。

    封装 pika BlockingConnection 的完整生命周期：
    1. 连接建立与队列拓扑声明
    2. Channel 打开与 QoS 配置（prefetch_count=1，确保公平调度）
    3. 消息反序列化与任务分发
    4. 信号处理（SIGINT / SIGTERM）以优雅关闭

    子类需要实现:
        async def process(self, message: dict) -> None
            处理单条任务消息的核心逻辑

    参数:
        queue_name (str): 要监听的 RabbitMQ 队列名称
        rabbitmq_url (str): RabbitMQ 的连接 URL（amqp://user:pass@host:port/）
    """

    def __init__(self, queue_name: str, rabbitmq_url: str):
        """初始化 BaseConsumer 实例。

        参数:
            queue_name (str): RabbitMQ 队列名称
            rabbitmq_url (str): RabbitMQ AMQP 连接 URL
        """
        self.queue_name = queue_name
        self.rabbitmq_url = rabbitmq_url
        self.connection: pika.BlockingConnection | None = None
        self.channel: pika.channel.Channel | None = None
        self._closing = False

    def on_message(self, ch, method, properties, body):
        """消息到达回调 —— 反序列化 JSON 并分发到异步 handle_task。

        参数:
            ch: pika Channel 对象
            method: pika 投递方法，包含 delivery_tag
            properties: 消息属性
            body (bytes): 消息体（JSON 字节串）
        """
        try:
            message = json.loads(body)
            task_id = message.get("task_id", "unknown")
            logger.info(f"[{self.queue_name}] Received task: {task_id}")
            import asyncio
            asyncio.run(self.handle_task(message, ch, method.delivery_tag))
        except Exception as e:
            logger.error(f"[{self.queue_name}] Failed to parse message: {e}")
            ch.basic_nack(method.delivery_tag, requeue=False)

    async def handle_task(self, message: dict, ch, delivery_tag: int):
        """异步处理任务 —— 成功后 ACK，失败后 NACK 且不重新入队。

        参数:
            message (dict): 反序列化后的任务消息字典
            ch: pika Channel 对象
            delivery_tag (int): 投递标签，用于 ACK/NACK 确认

        异常处理:
            若 process() 抛出异常，记录错误日志并发送 NACK（不重新入队），
            避免死循环重试。
        """
        try:
            await self.process(message)
            ch.basic_ack(delivery_tag)
        except Exception as e:
            logger.error(f"[{self.queue_name}] Task failed: {e}")
            ch.basic_nack(delivery_tag, requeue=False)

    @abstractmethod
    async def process(self, message: dict):
        """处理单条任务消息的抽象方法 —— 由子类实现具体爬取逻辑。

        参数:
            message (dict): 任务消息字典，结构如下:
                {
                    "task_id": "任务唯一ID",
                    "payload": { ... 具体载荷 ... }
                }

        异常:
            子类应抛出异常以触发消息 NACK（不重新入队）
        """
        ...

    def run(self):
        """启动消费者并阻塞消费当前队列。"""
        while not self._closing:
            try:
                params = pika.URLParameters(self.rabbitmq_url)
                self.connection = pika.BlockingConnection(params)
                self.channel = self.connection.channel()
                self._declare_topology()
                self.channel.basic_qos(prefetch_count=1)
                self.channel.basic_consume(queue=self.queue_name, on_message_callback=self.on_message)
                logger.info(f"[{self.queue_name}] Consumer started")
                self.channel.start_consuming()
            except (AMQPConnectionError, ChannelClosedByBroker) as e:
                if self._closing:
                    break
                logger.warning(f"[{self.queue_name}] RabbitMQ not ready or topology missing: {e}; retrying in 5s")
                self._close_connection()
                time.sleep(5)
            except Exception as e:
                if self._closing:
                    break
                logger.exception(f"[{self.queue_name}] Consumer stopped unexpectedly: {e}; retrying in 5s")
                self._close_connection()
                time.sleep(5)

    def _declare_topology(self):
        """声明与 Spring Boot 端一致的 RabbitMQ 拓扑，避免启动顺序依赖。"""
        self.channel.exchange_declare(exchange=EXCHANGE_TASKS, exchange_type="topic", durable=True)
        self.channel.exchange_declare(exchange=EXCHANGE_DLX, exchange_type="topic", durable=True)

        queue_args = {
            "x-dead-letter-exchange": EXCHANGE_DLX,
            "x-dead-letter-routing-key": RK_DEAD,
        }
        for queue_name, routing_key in QUEUE_BINDINGS.items():
            args = queue_args if queue_name != QUEUE_TASK_RESULT else None
            self.channel.queue_declare(queue=queue_name, durable=True, arguments=args)
            self.channel.queue_bind(exchange=EXCHANGE_TASKS, queue=queue_name, routing_key=routing_key)

        self.channel.queue_declare(queue=QUEUE_TASK_DEAD, durable=True)
        self.channel.queue_bind(exchange=EXCHANGE_DLX, queue=QUEUE_TASK_DEAD, routing_key=RK_DEAD)

    def _close_connection(self):
        try:
            if self.connection and self.connection.is_open:
                self.connection.close()
        except Exception:
            pass

    def stop(self):
        """优雅关闭消费者：关闭 Channel 和 Connection。

        设置 _closing 标记为 True，防止关闭时触发自动重连。
        先关闭 Channel，再关闭 Connection。
        """
        logger.info(f"[{self.queue_name}] Stopping...")
        self._closing = True
        try:
            if self.channel and self.channel.is_open:
                self.channel.stop_consuming()
        except Exception:
            pass
        try:
            if self.connection and self.connection.is_open:
                self.connection.close()
        except Exception:
            pass
