# 部署指南

## 环境要求

| 组件 | 最低版本 |
|------|----------|
| Python | 3.12+ |
| MySQL | 8.0+ |
| Redis | 7.0+ |

## 手动部署

### 1. 初始化 MySQL 数据库

创建两个数据库：

```sql
CREATE DATABASE IF NOT EXISTS cyberflow DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS scraped_data DEFAULT CHARACTER SET utf8mb4;
```

启动应用后，SQLAlchemy 会自动创建 `cyberflow` 中的表（`site_info`, `site_indexing_history`, `orders`）。

`scraped_data.ecommerce_products` 表需要手动创建：

```sql
USE scraped_data;

CREATE TABLE IF NOT EXISTS ecommerce_products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(500),
    description TEXT,
    regular_price DECIMAL(10, 2),
    categories VARCHAR(500),
    images TEXT,
    cf_opingts VARCHAR(500),
    custom_category VARCHAR(255),
    source_domain VARCHAR(255),
    language VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2. 克隆项目并安装依赖

```bash
git clone <repo-url>
cd CyberFlow/crawler-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 3. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 填入实际的数据库和 Redis 连接信息
```

### 4. 启动 Redis

```bash
redis-server
```

### 5. 启动 Celery Worker

```bash
cd crawler-service
celery -A app.core.celery_app worker --loglevel=info --concurrency=1 -Q crawler
```

建议使用 supervisor 或 systemd 守护进程。

### 6. 启动 FastAPI

```bash
cd crawler-service
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

生产环境建议搭配 Nginx 反向代理，并使用 `--workers` 参数启动多个 worker 进程。

### 7. 运行 Scrapy 爬虫（按需）

```bash
cd crawler-service/app/crawler/ecommerce_spider
python shopify站点.py
python 非shopify站点.py
```

可通过 crontab 定时执行。

## 进程总览

部署后应有以下进程运行：

| 进程 | 命令 | 说明 |
|------|------|------|
| FastAPI | `uvicorn app.main:app` | HTTP 服务 |
| Celery Worker | `celery -A app.core.celery_app worker -Q crawler` | 异步任务执行 |
| Redis | `redis-server` | 消息队列 + 缓存 |

Scrapy 爬虫为一次性/定时任务，不作为常驻进程。

## 健康检查

```bash
# FastAPI
curl http://localhost:8000/docs

# Celery Worker
celery -A app.core.celery_app inspect ping

# Redis
redis-cli ping

# MySQL
mysql -u root -p -e "SELECT 1"
```

## Docker 部署（待完善）

`docker-compose.yml` 位于项目根目录，目前尚未配置。计划包含以下服务：

- `app`: FastAPI 服务
- `worker`: Celery Worker
- `redis`: Redis 服务
- `mysql`: MySQL 服务

待完成配置后，使用 `docker-compose up -d` 一键启动。
