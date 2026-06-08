FROM python:3.12-slim

WORKDIR /app

# System deps for Scrapy + aiohttp
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc libxml2-dev libxslt-dev \
    && rm -rf /var/lib/apt/lists/*

# Python deps
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && pip install --no-cache-dir scrapy aiohttp aiomysql beautifulsoup4 lxml

# Consumer code
COPY config.py .
COPY main.py .
COPY consumers/ consumers/
COPY crawlers/ crawlers/
COPY db/ db/

# Scrapy project (subprocess target)
COPY ../crawler-service/app/crawler/ecommerce_spider/ scrapy_app/

# Override scrapy path for Docker
ENV SCRAPY_PROJECT_PATH=/app/scrapy_app

CMD ["python", "main.py"]
