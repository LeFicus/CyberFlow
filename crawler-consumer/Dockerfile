FROM python:3.12-slim

WORKDIR /app

# System deps for Scrapy + aiohttp
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc libxml2-dev libxslt-dev \
    && rm -rf /var/lib/apt/lists/*

# Python deps
COPY crawler-consumer/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && pip install --no-cache-dir scrapy aiohttp aiomysql beautifulsoup4 lxml

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

CMD ["python", "main.py"]
