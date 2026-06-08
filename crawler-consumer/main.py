#!/usr/bin/env python3
"""CyberFlow v2.0 — Python Consumer Entry Point"""
import asyncio
from dotenv import load_dotenv
from loguru import logger
from config import RABBITMQ_USER, RABBITMQ_PASS, RABBITMQ_HOST, RABBITMQ_PORT
from consumers.site_consumer import SiteConsumer
from consumers.order_consumer import OrderConsumer

load_dotenv()

RABBITMQ_URL = f"amqp://{RABBITMQ_USER}:{RABBITMQ_PASS}@{RABBITMQ_HOST}:{RABBITMQ_PORT}/"


async def main():
    logger.info("🚀 Starting CyberFlow v2.0 Consumers...")

    site = SiteConsumer(RABBITMQ_URL)
    order = OrderConsumer(RABBITMQ_URL)

    # Product consumer will be added in Phase 4
    consumers = [site, order]

    for consumer in consumers:
        consumer.run()

    try:
        while True:
            await asyncio.sleep(1)
    except asyncio.CancelledError:
        logger.info("Consumers cancelled")
    except Exception as e:
        logger.error(f"Consumer error: {e}")
    finally:
        for consumer in consumers:
            consumer.stop()


if __name__ == "__main__":
    asyncio.run(main())
