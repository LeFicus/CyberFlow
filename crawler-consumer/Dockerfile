FROM python:3.12-slim

WORKDIR /app

# System deps for Scrapy + aiohttp
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc libxml2-dev libxslt-dev \
    && rm -rf /var/lib/apt/lists/*

# Python deps
COPY crawler-consumer/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    --prefer-binary

# Consumer code
COPY crawler-consumer/config.py .
COPY crawler-consumer/main.py .
COPY crawler-consumer/consumers/ consumers/
COPY crawler-consumer/crawlers/ crawlers/
COPY crawler-consumer/db/ db/

# Scrapy project (subprocess target)
COPY crawler-service/app/crawler/ecommerce_spider/ scrapy_app/

# Override scrapy path for Docker
ENV SCRAPY_PROJECT_PATH=/app/scrapy_app
ENV PYTHONUNBUFFERED=1 PYTHONIOENCODING=utf-8 PYTHONDONTWRITEBYTECODE=1

RUN PYTHONPATH=/app/scrapy_app python -m ecommerce_spider.check_runtime

CMD ["python", "main.py"]
