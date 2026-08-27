# CyberFlow v2.0 — 电商数据采集与管理平台

## 项目概述

CyberFlow 是一个**电商数据自动化采集与管理平台**，商品采集入口覆盖 Shopify、WooCommerce、BigCommerce、Magento、Wix、Ecwid 和 Shopline，另提供支付平台订单同步、数据看板、RBAC 权限管理和定时/手动任务调度。新增平台的页面/API、授权要求及兼容边界见 [商品爬取修复执行文档](PRODUCT_CRAWL_REPAIR_EXECUTION.md) 第三阶段记录；代码支持不等于所有目标站点均已完成实站验收。

### 核心能力

| 能力 | 说明 |
|------|------|
| 商品采集 | 七个平台的专用 API/页面入口；Shopify 一变体一行；关键失败明确报错 |
| 订单同步 | 从支付平台增量同步订单数据 |
| 站点管理 | 自动发现和同步管理平台中的站点信息 |
| 数据看板 | 总览统计、站点/订单/商品列表，配合 ECharts 图表 |
| 权限管理 | 完整的用户-角色-菜单三级 RBAC 体系 |
| 任务调度 | 支持手动触发和 Quartz 定时任务 |
| 增量采集 | 基于游标（cursor）的去重机制，避免重复抓取 |

---

## 技术架构

```
┌──────────────────────────────┐
│        Frontend (Vue 3)      │  Port 5173 (dev) / Nginx (prod)
│  Element Plus + ECharts +    │
│  Tailwind CSS 4 + Pinia      │
└─────────────┬────────────────┘
              │ HTTP REST API
┌─────────────▼────────────────┐
│   Backend (Spring Boot 3.4)  │  Port 8080
│  Spring Security + JWT +     │
│  MyBatis-Plus + Quartz       │
└──────┬──────────────┬────────┘
       │  Publish     │ Consume
       ▼              ▲
┌──────────────┐ ┌──────────────┐
│  RabbitMQ    │ │  RabbitMQ    │
│  Exchange    │ │  DLX Queue   │
│  (Topic)     │ │              │
└──────┬───────┘ └──────────────┘
       │ Consume
       ▼
┌──────────────────────────────┐
│  crawler-consumer (Python)   │
│  pika + aiohttp + aiomysql   │
│  ┌──────────┐ ┌────────────┐ │
│  │Site      │ │Order       │ │
│  │Consumer  │ │Consumer    │ │
│  └──────────┘ └────────────┘ │
│  ┌──────────────────────┐    │
│  │ProductConsumer       │    │
│  │ → Scrapy subprocess  │    │
│  └──────────────────────┘    │
└──────────────┬───────────────┘
               │
┌──────────────▼───────────────┐
│       MySQL 8.0              │
│  cyberflow (管理数据)        │
│  scraped_data (商品数据)     │
└──────────────────────────────┘
┌──────────────┐ ┌──────────────┐
│  Redis 7     │ │  Quartz      │
│  可选缓存    │ │  定时调度    │
└──────────────┘ └──────────────┘
```

### 技术栈一览

| 层级 | 技术 | 版本 |
|------|------|------|
| 前端 | Vue 3 + Vite + Element Plus + Pinia + ECharts + Tailwind CSS 4 | Vue 3.5 / Vite 6 |
| 后端 | Spring Boot + MyBatis-Plus + Spring Security + JWT | Boot 3.4 / Java 17 |
| 消息队列 | RabbitMQ (Topic Exchange + 死信队列) | 3.13 |
| 定时任务 | Quartz (内存模式) | — |
| 缓存 | Redis | 7 |
| 数据库 | MySQL | 8.0 |
| 抓取引擎 | Scrapy + BeautifulSoup + lxml | Scrapy 2.x |
| 异步消费 | pika (async) + aiohttp + aiomysql + loguru | Python 3.12 |
| 容器化 | Docker + Docker Compose | — |
| API 文档 | SpringDoc (OpenAPI 3.0 / Swagger UI) | 2.7.0 |

---

## 项目结构

```
CyberFlow/
├── docker-compose.yml          # 5 个容器: rabbitmq, mysql, redis, backend-admin, crawler-consumer
├── .env / .env.docker          # 环境变量 (密码、端口、JWT 密钥等)
├── script/
│   └── init_all_databases.sql  # 数据库初始化 (建库、建表、种子数据)
│
├── frontend/                   # Vue 3 前端
│   ├── src/
│   │   ├── api/                # API 请求封装 (auth, crawler, dashboard, selector, system)
│   │   ├── router/             # 路由配置 (15 条路由)
│   │   ├── store/              # Pinia 状态管理 (user, app)
│   │   ├── utils/              # Axios 封装 (请求拦截、Token 注入、错误处理)
│   │   ├── views/
│   │   │   ├── login/          # 登录页
│   │   │   ├── layout/         # 主布局 (侧边栏 + 顶栏)
│   │   │   ├── dashboard/      # 数据看板 (总览、站点、订单、商品)
│   │   │   ├── crawler/        # 采集管理 (站点采集、商品采集、订单采集、任务历史、选择器模板、站点配置)
│   │   │   └── system/         # 系统管理 (用户、角色、菜单、操作日志)
│   │   └── mock/               # MockJS 开发数据
│   ├── vite.config.js
│   └── package.json
│
├── backend-admin/              # Spring Boot 3.4 后端
│   ├── src/main/java/com/cyberflow/admin/
│   │   ├── common/             # 基础设施 (CORS, JWT, 全局异常, 分页插件, AOP 日志, 统一响应)
│   │   ├── system/             # RBAC 模块 (用户/角色/菜单 CRUD, Spring Security + JWT)
│   │   ├── crawler/            # 采集编排模块
│   │   │   ├── config/         # RabbitMQ 配置 + Quartz 配置
│   │   │   ├── controller/     # 采集 API (手动触发)
│   │   │   ├── service/        # 任务调度编排
│   │   │   ├── scheduler/      # 定时任务 (站点采集每日2点, 订单采集每6小时)
│   │   │   ├── messaging/      # 消息发布 + 结果消费
│   │   │   ├── selector/       # 选择器模板 CRUD
│   │   │   ├── siteconfig/     # 站点配置 CRUD
│   │   │   └── task/           # 任务历史 + 采集游标
│   │   └── dashboard/          # 数据看板 (聚合统计、图表数据)
│   ├── pom.xml
│   └── Dockerfile              # 多阶段构建 (maven 编译 → JRE 运行)
│
├── crawler-consumer/           # Python 异步消费者
│   ├── main.py                 # 入口: 启动 Site + Order 消费者
│   ├── config.py               # 环境变量读取
│   ├── consumers/
│   │   ├── base_consumer.py    # 基础消费者 (pika AsyncioConnection, ack/nack, 自动重连)
│   │   ├── site_consumer.py    # 站点采集: 登录管理平台 → 获取站点列表 → 入库
│   │   ├── order_consumer.py   # 订单采集: 登录支付平台 → 增量获取订单 → 入库
│   │   └── product_consumer.py # 商品采集: 启动 Scrapy 子进程 → 抓取商品
│   ├── crawlers/
│   │   ├── site_crawler.py     # aiohttp: 管理平台 API 调用
│   │   └── order_crawler.py    # aiohttp: 支付平台 API 调用 (游标增量)
│   ├── db/
│   │   └── repository.py       # 异步 MySQL 操作 (游标仓库、商品仓库)
│   ├── requirements.txt
│   └── Dockerfile
│
├── crawler-service/            # Scrapy 爬虫项目
│   └── app/crawler/ecommerce_spider/
│       └── ecommerce_spider/
│           ├── spiders/
│           │   ├── shopify_crawl.py    # Shopify 公共商品 JSON / Storefront 变体分页
│           │   ├── platform_crawl.py   # WooCommerce: 站点地图与字段提取
│           │   ├── bigcommerce_crawl.py # BigCommerce 页面、BCData 与导航回退
│           │   ├── magento_crawl.py    # Magento GraphQL / 页面
│           │   ├── wix_crawl.py        # Wix 商品 Sitemap / JSON-LD
│           │   ├── ecwid_crawl.py      # Ecwid SEO 页面 / 授权公开 API
│           │   └── shopline_crawl.py   # Shopline 页面 / Ajax
│           ├── pipelines.py            # 持续 MySQL 提交 → 可选 Redis 观察性缓存
│           ├── settings.py             # 1 req/domain, 1s 延迟
│           └── exchange_rates.json     # 多币种汇率
│
└── docs/
    └── PROJECT.md              # 本文档
```

---

## 核心流程

### 1. 站点采集流程

```
[前端/定时任务] → POST /crawler/site/start
    → CrawlerService: 生成 taskId, 入库 task_history(PENDING)
    → TaskMessagePublisher: publish "crawler.site" 消息到 RabbitMQ
    → SiteConsumer: 接收消息, 异步登录管理平台, 获取站点列表
    → 数据写入 site_info 表, 更新 task_history(SUCCESS)
    → publish 结果回 crawler.task.result 队列
    → TaskResultConsumer: 更新 task_history 最终状态
```

### 2. 商品采集流程

```
[前端/定时任务] → POST /crawler/site/collect (携带任务ID和站点列表)
    → CrawlerService: 生成 taskId, 入库 task_history(PENDING)
    → TaskMessagePublisher: 按站点拆分, publish "crawler.product" 消息
    → ProductConsumer: 接收消息, 启动 Scrapy 子进程
      ├── shopify: shopify_crawl_fast（按变体保存）
      ├── woocommerce: platform_crawl（使用站点模板）
      ├── bigcommerce: bigcommerce_crawl
      └── magento / wix / ecwid / shopline: 对应独立 *_crawl
    → Scrapy Pipeline: 校验/过滤 → 分批 MySQL Upsert + commit → 可选 Redis 缓存
    → 结构化已提交计数 → Consumer 更新任务；暂停冻结活动 deadline
    → ProductConsumer: 统计采集数量, 更新 task_history + crawl_cursor
    → publish 结果回 crawler.task.result 队列
```

### 3. 订单同步流程

```
[前端/定时任务] → POST /crawler/order/start
    → CrawlerService: 生成 taskId, 入库 task_history(PENDING)
    → TaskMessagePublisher: publish "crawler.order" 消息
    → OrderConsumer: 接收消息, 异步登录支付平台
    → 按 cursor (max_order_id) 增量获取订单
    → 数据写入 orders 表, 更新 task_history + crawl_cursor
    → publish 结果回 crawler.task.result 队列
```

### 4. 增量采集机制

`crawl_cursor` 表记录每次采集的最后位置:

| 采集类型 | 游标字段 | 说明 |
|----------|--------|------|
| 站点采集 | `last_sync_time` | 上次同步时间戳 |
| 订单采集 | `max_order_id` | 已采集的最大订单 ID |
| 商品采集 | `last_crawl_time` | 上次采集时间戳 |

每次采集前读取游标，只获取增量数据，避免全量重复抓取。

---

## 数据库设计

### cyberflow (管理数据库)

| 表名 | 说明 |
|------|------|
| `sys_user` | 系统用户 |
| `sys_role` | 角色 |
| `sys_menu` | 菜单权限 |
| `sys_user_role` | 用户-角色关联 |
| `sys_role_menu` | 角色-菜单关联 |
| `sys_operation_log` | 操作审计日志 |
| `site_info` | 站点信息 (URL、域名、管理员、分类等) |
| `orders` | 订单数据 |
| `site_indexing_history` | 站点索引入库历史 |
| `task_history` | 采集任务执行记录 |
| `crawl_cursor` | 采集游标 |
| `crawler_selector_template` | 选择器模板 |
| `crawl_site_config` | 站点配置 |
| `site_template_mapping` | 站点-模板映射 |

### scraped_data (商品数据库)

| 表名 | 说明 |
|------|------|
| `ecommerce_products` | 电商商品信息 |

---

## 快速开始

### 环境要求

- Docker & Docker Compose
- (本地开发) Java 17+, Node.js 18+, Python 3.12+

### Docker 一键部署

```bash
cp .env.docker .env
docker compose up -d
```

自动启动 6 个容器（其中 `db-migrate` 执行完成后退出）:
- **RabbitMQ**: 管理界面 http://localhost:15672
- **MySQL**: localhost:3306 (root/root123)
- **DB Migrate**: 按顺序执行未完成的数据库迁移
- **Redis**: localhost:6379
- **Admin Backend**: http://localhost:8080 (Swagger: /swagger-ui.html)
- **Crawler Consumer**: Python 异步抓取服务

首次启动 MySQL 会自动执行 `init_all_databases.sql` 创建数据库和种子数据；每次部署会由 `db-migrate` 自动执行 `script/migrations/*.sql` 中尚未记录的迁移。

默认管理员账号: `admin` / `admin123`

### 本地开发

```bash
# 后端
cd backend-admin
mvn spring-boot:run

# 前端
cd frontend
npm install
npm run dev            # http://localhost:5173

# 消费者
cd crawler-consumer
pip install -r requirements.txt
python main.py
```

---

## 关键设计决策

### 1. 消息驱动的异步任务架构
选择 RabbitMQ 作为任务总线，实现后端与采集消费者的解耦。任务可以并行分发、独立扩缩容，失败任务进入死信队列便于排查。

### 2. 游标增量采集
每次采集只取增量数据（按时间戳或自增 ID 游标），大幅降低重复抓取和带宽成本。

### 3. 选择器模板系统
WooCommerce 站点的 CSS/XPath 选择器以 JSON 模板形式存储在数据库中，支持可视化配置和运行时动态组合（`|` 运算符合并多模板），无需代码变更即可适配新站点。

### 4. 双数据库设计
管理数据（`cyberflow`）和商品数据（`scraped_data`）分库存储，隔离业务关注点，便于独立扩容和备份。

### 5. MySQL 真值与可选缓存
商品身份为 `(source_domain, sku)`。Pipeline 在有界缓冲中持续批量提交；逐条 Upsert 的数据库返回值区分新增、更新、未变化，commit 后才发布计数。Redis 仅在提交后更新观察性指纹，关闭或断连不会阻止入库。失败任务保留已提交记录与统计，不用 Spider 生成量兜底。

### 6. 变体与任务控制
Shopify 用完整商品/变体 ID 保存独立价格、图片和已选属性；公开 JSON 疑似截断会失败，可用授权 Storefront token 完整分页。历史聚合商品不会自动迁移或删除，发布前须按执行文档确认处置。任务暂停会悬挂自有子进程并冻结剩余超时预算，取消时先恢复再终止；Linux 容器关闭与提交行为仍需部署验收。

---

## 最近更新 (v1.0 → v2.0)

- 移除旧版 FastAPI + Celery + CrawlerApiClient
- 引入 Spring Boot 3.4 + RabbitMQ + Python 异步消费者
- 新增 Docker Compose 部署方案
- 新增选择器模板和站点配置管理页面
- 前端从 Sass 迁移至 Tailwind CSS 4
