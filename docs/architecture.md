# 系统架构

## 总体架构

CyberFlow 采用分层架构，包含两大子系统：

### 子系统一：爬虫服务 (Python)

```
┌─────────────────────────────────────────────────────────────┐
│              Spring Boot Admin (业务入口)                     │
│              backend-admin/ → :8080                          │
│              职责：用户认证、触发爬虫、数据看板、系统管理        │
└──────────────┬──────────────────┬───────────────────────────┘
               │ X-Internal-Token │ 读 (MyBatis-Plus)
               ▼                  ▼
┌──────────────────────────┐ ┌───────────────────────────────┐
│   FastAPI (:8000)        │ │        MySQL                   │
│   app/api/crawler.py     │ │  cyberflow + scraped_data      │
│   职责：派发 Celery 任务   │ │                               │
└────────────┬─────────────┘ └───────────────────────────────┘
             │ Celery Task
┌────────────▼────────────────────────────────────────────────┐
│                 FastAPI (Uvicorn)                            │
│                 app/main.py → app/api/crawler.py             │
│                 职责：接收请求，参数校验，派发 Celery 任务      │
└────────────────────────┬────────────────────────────────────┘
                         │ Celery Task (Redis Broker)
┌────────────────────────▼────────────────────────────────────┐
│                 Celery Worker                                │
│                 app/tasks/*.py                               │
│                 职责：任务编排，调用 Service 层                │
└────────────────────────┬────────────────────────────────────┘
                         │ 方法调用
┌────────────────────────▼────────────────────────────────────┐
│                 Service 层                                    │
│  SiteCrawler | SiteIndexCrawler | OrderCrawler               │
│  职责：登录外部平台、分页抓取 API、数据清洗、去重入库          │
└─────────┬──────────────────────────┬────────────────────────┘
          │ httpx                    │ mysql-connector
          ▼                          ▼
┌──────────────────┐    ┌─────────────────────────────────────┐
│  外部平台         │    │           MySQL (cyberflow)          │
│  104.233.194.18  │    │  site_info                           │
│  c4partypay.com  │    │  site_indexing_history               │
└──────────────────┘    │  orders                              │
                        └─────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────┐
│              Scrapy 引擎 (独立进程，手动触发)                  │
│                                                             │
│  spiders/shopify_crawl.py  →  ShopifyCrawlFastSpider         │
│  spiders/woo_crawl.py     →  WooCrawlSpider                  │
│                     │                                       │
│                     ▼                                       │
│         middlewares.py (CustomUserAgentMiddleware)           │
│                     │                                       │
│                     ▼                                       │
│         pipelines.py (MySQLRedisPipeline)                    │
│           ├── Redis SADD 去重 (SKU 维度)                     │
│           └── MySQL executemany 批量入库                      │
│                     │                                       │
│                     ▼                                       │
│         MySQL (scraped_data) — ecommerce_products            │
└─────────────────────────────────────────────────────────────┘
```

## 组件职责

### Spring Boot Admin (backend-admin/)

- 提供用户认证与权限管理（Spring Security + JWT）
- 爬虫任务管理：通过 RestTemplate 调用 FastAPI `/crawler` 端点
- 数据看板：通过 MyBatis-Plus 直连 MySQL 查询统计数据
- 系统管理：用户/角色/菜单 CRUD + 操作审计日志
- 内部调用 FastAPI 时携带 `X-Internal-Token` Header

### FastAPI (app/main.py, app/api/crawler.py)

- 4 个 REST 端点，挂载在 `/crawler` 前缀下
- **所有端点需要 `X-Internal-Token` Header 验证**（由 `app/core/auth.py` 中间件提供）
- 接收 Spring Boot Admin 的请求，派发 Celery 任务

### Celery Worker (app/core/celery_app.py)

- 以 Redis 为 broker 和 backend
- 3 个任务路由到同一队列 `crawler`
- `worker_max_tasks_per_child=50`：每 50 个任务后重启 worker，防止内存泄漏
- 时区设为 `Asia/Shanghai`
- 序列化方式：JSON

### Service 层 (app/services/)

#### SiteCrawler — 站点信息爬取

- 登录管理平台 `POST /adminapi/login`
- 构建站点地图：分页 `GET /adminapi/site/site/list`
- 获取域名列表：分页 `GET /adminapi/domain/domain/list`
- 过滤规则：`admin_name == "super"` 跳过，`status != 2` 跳过
- 交叉 site_map 补全 `theme_name`、`product_category`
- 写入 `site_info` 表：逐条按域名 UPSERT

#### SiteIndexCrawler — 站点收录统计

- 登录管理平台（同上）
- 获取收录统计：分页 `GET /adminapi/statistics/theme/list`
- 遍历结果，提取 `google_count`（Google 收录数）和 `total_product`（产品总数）
- 写入 `site_indexing_history` 表：同域名同天更新，否则新增

#### OrderCrawler — 订单爬取

- 登录支付平台 `POST /platformapi/login/account`
- 分页 `GET /platformapi/pay.pay_order/lists`（`tenant_id=95`, `page_size=100`）
- 过滤测试数据：排除 `测试`、`ig-3`、`test-mutiwp` 渠道，`400000******0000`、`411111******1111` 卡号
- 交叉 `site_info` 表补全 `admin_name`/`theme_name`/`product_category`
- 按订单 ID 去重，`bulk_save_objects` 批量入库

### Scrapy Spiders (app/crawler/ecommerce_spider/)

- 不通过 Celery 调度，手动执行 Python 脚本
- Shopify: 直接请求 `products.json` API 快速获取商品列表
- WooCommerce: 解析 sitemap XML 提取产品页 URL，再用 XPath/CSS 选择器抓取详情页
- 使用 `CustomUserAgentMiddleware` 随机切换 User-Agent
- `MySQLRedisPipeline`：Redis SKU 去重 → 内存缓冲 → 批量 MySQL 写入

### 内部认证

FastAPI 使用 `X-Internal-Token` Header 保护 `/crawler` 端点：

```python
# app/core/auth.py
async def verify_internal_token(x_internal_token: str = Header(None)):
    if x_internal_token != settings.INTERNAL_API_TOKEN:
        raise HTTPException(status_code=403)
```

Spring Boot Admin 在所有 FastAPI 调用中自动携带该 Header。

### MySQL

| 数据库 | 访问方式 | 表 |
|--------|----------|-----|
| `cyberflow` | SQLAlchemy ORM + MyBatis-Plus | `site_info`, `site_indexing_history`, `orders`, `sys_user`, `sys_role`, `sys_menu`, `sys_user_role`, `sys_role_menu`, `sys_operation_log` |
| `scraped_data` | mysql-connector + MyBatis-Plus (只读) | `ecommerce_products` |

### 后台管理表

Spring Boot 后台管理系统新增 6 张表：

| 表 | 说明 |
|----|------|
| `sys_user` | 系统用户（BCrypt 加密密码） |
| `sys_role` | 角色（ROLE_ADMIN, ROLE_OPERATOR） |
| `sys_menu` | 菜单树 + 权限标识 |
| `sys_user_role` | 用户-角色关联 |
| `sys_role_menu` | 角色-菜单关联 |
| `sys_operation_log` | 操作审计日志 |

### Redis

| 用途 | 数据库编号 | 访问方 |
|------|-----------|--------|
| Celery Broker | 1 | Celery |
| Celery Backend | 2 | Celery |
| Scrapy 去重缓存 | 0 | Scrapy Pipeline |

## 数据库表结构

### site_info

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT (PK) | 自增主键 |
| username | VARCHAR(50) | 爬虫账号 |
| site_domain | VARCHAR(100) (UNIQUE) | 站点域名 |
| admin_name | VARCHAR(50) | 管理员名称 |
| theme_name | VARCHAR(50) | 主题名称 |
| product_category | VARCHAR(50) | 产品分类 |
| created_at | DATETIME | 创建时间 |

### site_indexing_history

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT (PK) | 自增主键 |
| site_domain | VARCHAR(100) (INDEX) | 站点域名 |
| index_count | INT | Google 收录数 |
| product_count | INT (INDEX) | 产品总数 |
| recorded_at | DATETIME | 记录时间 |
| created_at | DATETIME | 创建时间 |
| admin_name | VARCHAR(50) (INDEX) | 管理员名称 |

### orders

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT (PK, UNIQUE) | 外部订单 ID |
| amount | DECIMAL(10,2) | 订单金额 |
| currency | VARCHAR(50) | 币种 |
| create_time | DATETIME | 订单时间 |
| product_host | VARCHAR(255) | 产品域名 |
| pay_status_text | VARCHAR(50) | 支付状态 |
| customer_ip_country | VARCHAR(50) | 客户 IP 国家 |
| shipping_email | VARCHAR(50) | 收货邮箱 |
| admin_name | VARCHAR(50) (INDEX) | 冗余：管理员 |
| theme_name | VARCHAR(50) (INDEX) | 冗余：主题 |
| product_category | VARCHAR(50) (INDEX) | 冗余：产品分类 |

### ecommerce_products

| 字段 | 类型 | 说明 |
|------|------|------|
| sku | VARCHAR (UNIQUE) | SKU（去重键） |
| name | VARCHAR | 商品名称 |
| description | TEXT | 商品描述 |
| regular_price | DECIMAL | 常规价格 |
| categories | VARCHAR | 分类 |
| images | TEXT | 图片 URL |
| cf_opingts | VARCHAR | 自定义选项 |
| custom_category | VARCHAR | 自定义分类 |
| source_domain | VARCHAR | 来源域名 |
| language | VARCHAR | 语言 |

> `ecommerce_products` 表使用 `ON DUPLICATE KEY UPDATE`，SKU 重复时更新 name/price/categories/images。

## Scrapy 管道链路

```
Spider (yield item)
  → CustomUserAgentMiddleware (随机 User-Agent)
  → MySQLRedisPipeline.process_item()
      1. 校验 SKU，无 SKU 则 DropItem
      2. Redis SADD scraped_skus:{spider}:{domain} 去重，重复则 DropItem
      3. 加入内存缓冲区
      4. 缓冲区 ≥ 50 条 → executemany 批量写入 MySQL
  → MySQLRedisPipeline.close_spider()
      1. 强制刷缓冲区
      2. dev 模式：删除 Redis 缓存
      3. prod 模式：输出指纹总数日志
```

## 请求生命周期示例

以「订单爬取」为例（经 Spring Boot Admin 触发）：

1. 运营人员点击 "启动订单爬虫" → Vue 前端 `POST /admin/crawler/order/start` (带 JWT)
2. Spring Boot `CrawlerController` 校验权限 `crawler:order:start`
3. `CrawlerApiClient` 携带 `X-Internal-Token` 调用 FastAPI `POST /crawler/order/start`
4. FastAPI `verify_internal_token` 验证通过 → `run_order_crawler.delay(...)` 推入 Redis Broker
5. 返回 `{"task_id":"xxx", "status":"Task dispatched"}`
6. Celery Worker 从队列拉取任务 → `OrderCrawler.run(start_time, end_time)`
7. 登录 `c4partypay.com` → 分页抓取 → 去重 → 交叉 `site_info` → 批量入 `orders`
8. 前端轮询 `GET /admin/crawler/status/{taskId}` → Spring Boot 代理到 FastAPI

以「数据看板」为例：

1. 运营人员访问 Dashboard → Vue 前端 `GET /admin/dashboard/overview` (带 JWT)
2. Spring Boot `DashboardController` 校验权限 `dashboard:overview`
3. `DashboardService` 通过 MyBatis-Plus Mapper 直连 MySQL 查询统计数据
4. 返回聚合结果到前端渲染图表
