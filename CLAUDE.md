# CLAUDE.md — CyberFlow 项目上下文

## 项目概述

CyberFlow 是一个电商数据爬取与聚合平台，通过 FastAPI + Celery 调度爬虫任务，从管理平台和支付平台抓取站点信息与订单数据，同时使用 Scrapy 独立爬取 Shopify/WooCommerce 商品详情，所有数据存入 MySQL。

## 架构分层

```
API 层    → app/api/crawler.py          (FastAPI Router，接收 HTTP 请求，派发 Celery 任务)
Task 层   → app/tasks/*_task.py         (Celery 任务封装，薄层，仅做参数转发)
Service 层 → app/services/*_service.py  (业务逻辑：登录、分页抓取、去重入库)
Core 层   → app/core/                   (config.py 配置, database.py 数据库, celery_app.py 队列)
Model 层  → app/model/                  (SQLAlchemy ORM 模型 + Pydantic 请求体)

独立项目  → app/crawler/ecommerce_spider/ (Scrapy 项目，不经过 Celery，手动运行)
```

## 四大数据流

### 1. 站点信息爬取 (Site Crawler)

**触发**: `POST /crawler/site/start`
**流程**:
1. [crawler.py:16-22](crawler-service/app/api/crawler.py#L16-L22) 接收 `SiteRequest(username, password)`，调用 `run_site_crawler.delay()`
2. Celery Worker 执行 [site_crawler_task.py](crawler-service/app/tasks/site_crawler_task.py) → 调用 [site_crawler_service.py#SiteCrawler](crawler-service/app/services/site_crawler_service.py#L13)
3. `login()` → POST `http://104.233.194.18/adminapi/login`，获取 Bearer Token
4. `fetch_site_map()` → GET `http://104.233.194.18/adminapi/site/site/list`，构建域名→站点映射
5. `run()` → 分页 GET `http://104.233.194.18/adminapi/domain/domain/list`，交叉 site_map 补全主题/分类，过滤 `status != 2` 和 `admin_name == "super"`
6. `save_to_mysql()` → 按 `site_domain` UPSERT 到 `site_info` 表

### 2. 站点收录统计 (Site Index Crawler)

**触发**: `POST /crawler/site/collect`
**流程**:
1. [crawler.py:26-34](crawler-service/app/api/crawler.py#L26-L34) 派发 `run_site_index_crawler.delay()`
2. Celery Worker → [site_collect_service.py#SiteIndexCrawler](crawler-service/app/services/site_collect_service.py#L15)
3. `login()` → 同上
4. `fetch_site_map()` → GET `http://104.233.194.18/adminapi/statistics/theme/list`
5. `run()` → 遍历 site_map，组装 `google_count` 和 `total_product`
6. `save_to_mysql()` → 按天去重：同域名当天已有记录则更新 `index_count`/`product_count`，否则 INSERT 到 `site_indexing_history`

### 3. 订单爬取 (Order Crawler)

**触发**: `POST /crawler/order/start`
**流程**:
1. [crawler.py:36-47](crawler-service/app/api/crawler.py#L36-L47) 接收 `OrderRequest(start_time, end_time)`，使用环境变量中的 `CRAWLER_USERNAME`/`CRAWLER_PASSWORD` 派发 `run_order_crawler.delay()`
2. Celery Worker → [order_crawler_service.py#OrderCrawler](crawler-service/app/services/order_crawler_service.py#L12)
3. `login()` → POST `https://c4partypay.com/platformapi/login/account`，获取 Token
4. `run()` → 分页 GET `https://c4partypay.com/platformapi/pay.pay_order/lists` (`tenant_id=95`)，过滤测试订单（`测试`, `ig-3`, `test-mutiwp`）和测试卡号
5. `save_to_db()` → 按订单 ID 去重，交叉 `site_info` 表补全 `admin_name`/`theme_name`/`product_category`，`bulk_save_objects` 批量入 `orders` 表

### 4. 商品爬取 (Scrapy Spiders — 独立进程)

**触发**: 手动运行 `python shopify站点.py` 或 `python 非shopify站点.py`
**流程**:
- Shopify: `ShopifyCrawlFastSpider` 直接请求 `products.json` API
- WooCommerce: `WooCrawlSpider` 解析站点 sitemap XML 提取产品页 URL
- Pipeline `MySQLRedisPipeline`:
  1. Redis `SADD scraped_skus:{spider}:{domain}` 按 SKU 去重
  2. 缓冲区攒满 50 条后 `executemany` 批量 INSERT 到 `scraped_data.ecommerce_products`
  3. 结束时 dev 模式清除 Redis 缓存，prod 模式保留

## 关键编码约定

### 日志

使用 `loguru`，通过 `logger.bind(user=username)` 注入用户上下文：

```python
self.log = logger.bind(user=username)
self.log.info("🔑 正在登录...")
self.log.success("🔓 登录成功")
self.log.error(f"❌ 失败: {e}")
```

### 配置

使用 `pydantic-settings` 的 `BaseSettings`，自动从 `.env` 文件读取：

```python
from app.core.config import settings
db_url = settings.DATABASE_URL
```

### Celery 任务模式

```python
# 任务定义 (tasks/*.py)
@celery_app.task(name="run_site_crawler")
def run_site_crawler(username, password):
    crawler = SiteCrawler(username, password)
    return crawler.run()

# 任务派发 (api/*.py)
task = run_site_crawler.delay(username, password)
return {"task_id": task.id, "status": "Task dispatched"}
```

### 数据库 Session

```python
from app.core.database import SessionLocal

with SessionLocal() as db:
    # 查询、写入
    db.commit()

# 或手动管理
db = SessionLocal()
try:
    ...
    db.commit()
except:
    db.rollback()
finally:
    db.close()
```

### httpx 客户端

Service 层使用同步 `httpx.Client`（非 async），在构造函数中创建，`run()` 结束时关闭：

```python
self.client = httpx.Client(verify=False, timeout=30)
# ... 业务逻辑 ...
self.client.close()
```

## 常用命令

```bash
# 启动 Celery Worker
cd crawler-service
celery -A app.core.celery_app worker --loglevel=info --concurrency=1 -Q crawler

# 启动 FastAPI (开发模式)
cd crawler-service
uvicorn app.main:app --reload --port 8000

# 运行 Scrapy 爬虫
cd crawler-service/app/crawler/ecommerce_spider
python shopify站点.py       # Shopify 商品
python 非shopify站点.py       # WooCommerce 商品

# Swagger 文档
open http://localhost:8000/docs

# 调用 API
curl -X POST http://localhost:8000/crawler/site/start \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "pass123"}'

curl -X POST http://localhost:8000/crawler/order/start \
  -H "Content-Type: application/json" \
  -d '{"start_time": "2026-04-01 00:00:00", "end_time": "2026-04-28 23:59:59"}'

curl http://localhost:8000/crawler/status/{task_id}
```

## 重要边界条件

1. **Scrapy 与 Celery 完全解耦** — Scrapy 是独立脚本，不通过 Celery 调度，不共享数据库驱动（Scrapy 用 mysql-connector，Celery 用 SQLAlchemy）
2. **两个 MySQL 数据库** — `cyberflow` 存站点/订单（SQLAlchemy），`scraped_data` 存商品（mysql-connector），二者无外键关系
3. **硬编码外部 URL** — 管理平台 `http://104.233.194.18`，支付平台 `https://c4partypay.com`，无抽象层
4. **同步 httpx 阻塞 Celery Worker** — Service 层全部使用同步 `httpx.Client`，Celery Worker 在爬取期间被完全阻塞，`concurrency=1` 时无法并发
5. **认证方式不一致** — 站点爬取/收录端点的账号密码由请求体传入，订单爬取端点的账号密码从环境变量读取
6. **Scrapy 配置硬编码** — `settings.py` 和 `pipelines.py` 中的 MySQL/Redis 配置是硬编码的，不读取 `.env`

## 技术债

1. Scrapy 爬虫应封装为 Celery 任务，统一调度和监控
2. 外部 URL 应提取到配置文件
3. httpx 应改为 async 客户端或使用 `run_in_executor`
4. requirements.txt 缺少 Scrapy、httpx、pydantic-settings 等依赖声明
5. docker-compose.yml 需要配置 MySQL + Redis + App + Worker 服务
6. `OrderCrawler` 和 `SiteIndexCrawler` 的 `save_to_db` 有重复的 site_map 构建逻辑，可提取
7. `SiteCrawler` 使用逐条 UPSERT，订单量增长后应改为批量操作
