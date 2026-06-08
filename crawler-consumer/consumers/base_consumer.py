import asyncio
import json
import signal
from abc import ABC, abstractmethod
from loguru import logger
import pika
from pika.adapters.asyncio_connection import AsyncioConnection
from pika.channel import Channel


class BaseConsumer(ABC):
    """Base async consumer using pika AsyncioConnection."""

    def __init__(self, queue_name: str, rabbitmq_url: str):
        self.queue_name = queue_name
        self.rabbitmq_url = rabbitmq_url
        self.connection: AsyncioConnection | None = None
        self.channel: Channel | None = None
        self._closing = False
        self._consuming = False

    def connect(self):
        params = pika.URLParameters(self.rabbitmq_url)
        self.connection = AsyncioConnection(
            parameters=params,
            on_open_callback=self.on_connection_open,
            on_close_callback=self.on_connection_closed,
        )

    def on_connection_open(self, connection):
        logger.info(f"[{self.queue_name}] Connection opened")
        self.connection.channel(on_open_callback=self.on_channel_open)

    def on_connection_closed(self, connection, exception):
        logger.warning(f"[{self.queue_name}] Connection closed: {exception}")
        if not self._closing:
            asyncio.get_event_loop().call_later(5, self.connect)

    def on_channel_open(self, channel):
        self.channel = channel
        channel.basic_qos(prefetch_count=1)
        channel.basic_consume(self.queue_name, on_message_callback=self.on_message)
        logger.info(f"[{self.queue_name}] Consuming...")

    def on_message(self, ch, method, properties, body):
        try:
            message = json.loads(body)
            task_id = message.get("task_id", "unknown")
            logger.info(f"[{self.queue_name}] Received task: {task_id}")
            asyncio.ensure_future(self.handle_task(message, ch, method.delivery_tag))
        except Exception as e:
            logger.error(f"[{self.queue_name}] Failed to parse message: {e}")
            ch.basic_nack(method.delivery_tag, requeue=False)

    async def handle_task(self, message: dict, ch, delivery_tag: int):
        try:
            await self.process(message)
            ch.basic_ack(delivery_tag)
        except Exception as e:
            logger.error(f"[{self.queue_name}] Task failed: {e}")
            ch.basic_nack(delivery_tag, requeue=False)

    @abstractmethod
    async def process(self, message: dict):
        """Subclasses implement the actual crawling logic."""
        ...

    def run(self):
        self.connect()
        loop = asyncio.get_event_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            try:
                loop.add_signal_handler(sig, self.stop)
            except NotImplementedError:
                pass  # Windows doesn't support add_signal_handler
        logger.info(f"[{self.queue_name}] Consumer started")

    def stop(self):
        logger.info(f"[{self.queue_name}] Stopping...")
        self._closing = True
        if self.channel and self.channel.is_open:
            self.channel.close()
        if self.connection and self.connection.is_open:
            self.connection.close()
