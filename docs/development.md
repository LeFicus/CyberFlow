# 开发指南

## 本地开发环境

```bash
cd crawler-service
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

确保本地已运行 MySQL 和 Redis。创建 `.env` 文件配置连接信息。

启动开发服务：

```bash
# 终端 1: Celery Worker
celery -A app.core.celery_app worker --loglevel=info --concurrency=1 -Q crawler

# 终端 2: FastAPI (热重载)
uvicorn app.main:app --reload --port 8000
```

## 项目分层约定

```
app/
├── api/           # 只做参数提取和任务派发，不写业务逻辑
├── tasks/         # 薄层，只做参数转发到 Service
├── services/      # 业务逻辑核心：登录、分页抓取、清洗、入库
├── model/         # SQLAlchemy ORM 模型 + Pydantic 请求体
└── core/          # 基础设施：配置、数据库、Celery
```

新增功能时遵循以上分层。

## 如何添加新的 API 端点

### 步骤 1：定义请求体（如需要）

在 `app/model/request/` 下创建 Pydantic 模型：

```python
from pydantic import BaseModel

class NewCrawlerRequest(BaseModel):
    param1: str
    param2: int
```

### 步骤 2：编写 Service

在 `app/services/` 下创建业务逻辑类，遵循现有模式：

```python
import httpx
from loguru import logger

class NewCrawlerService:
    def __init__(self, param1, param2):
        self.log = logger.bind(task="new_crawler")
        self.client = httpx.Client(verify=False, timeout=30)

    def run(self):
        # 业务逻辑
        ...

    def __del__(self):
        self.client.close()
```

### 步骤 3：编写 Celery 任务包装

在 `app/tasks/` 下创建任务：

```python
from app.core.celery_app import celery_app
from app.services.new_crawler_service import NewCrawlerService

@celery_app.task(name="run_new_crawler")
def run_new_crawler(param1, param2):
    service = NewCrawlerService(param1, param2)
    return service.run()
```

### 步骤 4：注册 API 路由 + Celery 路由

在 `app/api/crawler.py` 添加端点；在 `app/core/celery_app.py` 的 `include` 和 `task_routes` 中注册。

## 如何添加新的 Scrapy 爬虫

### 1. 创建 Spider

在 `app/crawler/ecommerce_spider/ecommerce_spider/spiders/` 下新建文件，继承 `scrapy.Spider`。

### 2. 添加选择器配置（WooCommerce）

在 `app/crawler/ecommerce_spider/configs/selectors/` 下创建 JSON 配置文件，定义 XPath/CSS 选择器。

### 3. 创建启动脚本

在 `app/crawler/ecommerce_spider/` 下创建 Python 脚本，调用 CrawlerProcess 启动爬虫。

## 日志约定

- 框架：`loguru`
- 使用 `logger.bind(key=value)` 注入上下文
- 级别选择：
  - `info`: 正常流程节点（"正在登录..."、"第 X 页处理完毕"）
  - `success`: 操作成功（"登录成功"、"入库成功"）
  - `warning`: 可恢复异常（"第 X 页抓取失败"、"未发现数据"）
  - `error`: 数据异常（"接口返回异常"、"写入失败"）
  - `critical`: 任务级别失败（"任务异常终止"）

## 配置约定

- 所有配置通过 `app/core/config.py` 的 `Settings` 类管理
- 敏感信息（密码、Token）从环境变量读取，不硬编码
- 环境变量在 `.env` 文件中定义，不要提交到 Git
- 使用 `from app.core.config import settings` 统一访问

## 调试技巧

### 查看 Celery 任务日志

```bash
celery -A app.core.celery_app worker --loglevel=debug -Q crawler
```

### 查看 Redis 去重状态

```bash
redis-cli
> SELECT 0
> KEYS scraped_skus:*
> SCARD scraped_skus:spider_name:domain.com
```

### 直接测试 Service 层

```python
# 在 Python REPL 中
from app.services.site_crawler_service import SiteCrawler
crawler = SiteCrawler("username", "password")
crawler.run()
```

### 测试 API 端点

访问 `http://localhost:8000/docs` 使用 Swagger UI 交互式调试。
