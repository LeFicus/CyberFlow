# CyberFlow — 电商数据爬取与聚合平台

CyberFlow 是一套面向电商站点的数据采集与聚合系统。它能够自动登录管理平台、抓取站点元信息与订单数据，并通过 Scrapy 爬虫采集 Shopify/WooCommerce 店铺的商品详情，最终将所有数据持久化到 MySQL，以支持下游的数据分析和运营决策。

## 系统架构

```
┌──────────────────────────────────────────────────────────────┐
│                     FastAPI REST API                         │
│   POST /crawler/site/start   POST /crawler/order/start       │
│   POST /crawler/site/collect GET /crawler/status/{task_id}   │
└───────────────┬──────────────────────────────────────────────┘
                │ 派发 Celery 任务
┌───────────────▼──────────────────────────────────────────────┐
│                    Celery Workers                             │
│  run_site_crawler  run_order_crawler  run_site_index_crawler │
└───────┬───────────────────────┬──────────────────────────────┘
        │                       │
        │ httpx 登录 & 分页抓取   │
        ▼                       ▼
┌───────────────┐     ┌──────────────────┐
│ 管理平台       │     │ 支付平台          │
│ 104.233.194.10│     │ c4partypay.com    │
└───────────────┘     └──────────────────┘
        │                       │
        ▼                       ▼
┌──────────────────────────────────────────────────────────────┐
│                  MySQL (cyberflow)                            │
│  site_info | site_indexing_history | orders                   │
└──────────────────────────────────────────────────────────────┘


┌──────────────────────────────────────────────────────────────┐
│             Scrapy Spiders (独立进程)                          │
│  ShopifyCrawlFastSpider  |  WooCrawlSpider                    │
└───────────────┬──────────────────────────────────────────────┘
                │ Redis SKU 去重
                ▼
┌──────────────────────────────────────────────────────────────┐
│            MySQL (scraped_data) — ecommerce_products          │
└──────────────────────────────────────────────────────────────┘
```

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 运行时 | Python | 3.12 |
| Web 框架 | FastAPI | 0.135.1 |
| ASGI 服务器 | Uvicorn | 0.41.0 |
| 任务队列 | Celery | 5.6.2 |
| 消息/缓存 | Redis | 7.2.1 |
| 爬虫框架 | Scrapy | (venv) |
| ORM | SQLAlchemy | 2.0.48 |
| 数据库驱动 | PyMySQL | 1.1.2 |
| HTTP 客户端 | httpx | (venv) |
| 数据处理 | Pandas | 3.0.1 |
| 日志 | loguru | 0.7.3 |
| 浏览器自动化 | DrissionPage | 4.1.1.2 |
| 配置管理 | pydantic-settings + python-dotenv | 1.2.2 |

## 目录结构

```
CyberFlow/
├── README.md
├── docker-compose.yml          # 待完善
├── crawler-service/            # 核心服务
│   ├── requirements.txt
│   └── app/
│       ├── main.py             # FastAPI 入口
│       ├── api/crawler.py      # REST 路由
│       ├── core/
│       │   ├── config.py       # Pydantic 配置（从 .env 读取）
│       │   ├── database.py     # SQLAlchemy 引擎与 Session
│       │   └── celery_app.py   # Celery 实例配置
│       ├── model/
│       │   ├── order.py        # Order 表模型
│       │   ├── site_info.py    # SiteInfo & SiteIndexingHistory 模型
│       │   └── request/        # Pydantic 请求体 Schema
│       ├── services/           # 业务逻辑层
│       ├── tasks/              # Celery 任务包装
│       └── crawler/ecommerce_spider/  # Scrapy 项目（独立）
├── backend-admin/              # 后台管理（待开发）
├── frontend/                   # 前端（待开发）
├── docs/                       # 项目文档
└── script/                     # 辅助脚本（待开发）
```

## 快速开始

### 1. 环境要求

- Python 3.12+
- MySQL 8.0+
- Redis 7.0+

### 2. 安装依赖

```bash
cd crawler-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 3. 配置环境变量

在 `crawler-service/` 目录下创建 `.env` 文件：

```env
# 数据库
DATABASE_URL=mysql+pymysql://root:123456@localhost:3306/cyberflow

# Redis
REDIS_URL=redis://127.0.0.1:6379/0
BROKER_URL=redis://127.0.0.1:6379/1
BACKEND_URL=redis://127.0.0.1:6379/2

# 爬虫账号（可选，订单爬虫使用）
CRAWLER_USERNAME=your_username
CRAWLER_PASSWORD=your_password

# 任务队列名称
QUEUE_NAME=crawler
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

### 6. 启动 FastAPI

```bash
cd crawler-service
uvicorn app.main:app --reload --port 8000
```

### 7. 运行 Scrapy 爬虫（独立进程）

```bash
cd crawler-service/app/crawler/ecommerce_spider

# Shopify 站点
python shopify站点.py

# 非 Shopify（WooCommerce）站点
python 非shopify站点.py
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DATABASE_URL` | `mysql+pymysql://root:123456@localhost:3306/cyberflow` | MySQL 主库连接串 |
| `REDIS_URL` | `redis://127.0.0.1:6379/0` | Scrapy 去重缓存 |
| `BROKER_URL` | `redis://127.0.0.1:6379/1` | Celery 消息代理 |
| `BACKEND_URL` | `redis://127.0.0.1:6379/2` | Celery 结果存储 |
| `CRAWLER_USERNAME` | `None` | 订单爬虫登录账号 |
| `CRAWLER_PASSWORD` | `None` | 订单爬虫登录密码 |
| `QUEUE_NAME` | `crawler` | Celery 任务队列名称 |

## 数据库

系统使用两个 MySQL 数据库：

| 数据库 | 用途 | 主要表 |
|--------|------|--------|
| `cyberflow` | 站点信息、订单数据 | `site_info`, `site_indexing_history`, `orders` |
| `scraped_data` | 电商商品数据 | `ecommerce_products` |

> `cyberflow` 通过 SQLAlchemy ORM 访问，`scraped_data` 通过 mysql-connector 连接池直接访问。

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/crawler/site/start` | 启动站点信息爬取 |
| POST | `/crawler/site/collect` | 启动站点收录统计 |
| POST | `/crawler/order/start` | 启动订单爬取 |
| GET | `/crawler/status/{task_id}` | 查询任务状态 |

启动后访问 http://localhost:8000/docs 查看 Swagger 文档。

## 已知限制

- Scrapy 爬虫未集成到 Celery 任务调度，需手动运行
- docker-compose.yml 尚未配置
- 外部管理平台 URL 硬编码在 Service 层
- httpx 使用同步模式，阻塞 Celery worker
- 部分依赖（Scrapy、httpx、pydantic-settings）未写入 requirements.txt
