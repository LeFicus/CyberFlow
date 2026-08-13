# CyberFlow v2.0

电商数据采集与管理平台 — 支持多站点商品信息自动采集、订单数据同步、数据看板展示，提供完整 RBAC 权限管理。

---

## 项目结构

```
CyberFlow/
├── backend-admin/          # Spring Boot 管理后台 (Java 17)
├── frontend/               # Vue 3 前端 SPA
├── crawler-consumer/       # Python 爬虫消费者 (RabbitMQ 消费端)
├── crawler-service/        # Scrapy 爬虫项目 (由消费者调度)
├── docker-compose.yml      # 一键部署编排
├── .env.docker             # Docker 环境变量模板
├── script/                 # SQL 初始化脚本
└── docs/                   # 项目文档
```

---

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| **前端** | Vue 3 + Vite + Element Plus + Pinia + ECharts | Vue 3.5 / Vite 6 |
| **后端** | Spring Boot + MyBatis-Plus + Spring Security + JWT | Boot 3.4 / Java 17 |
| **消息队列** | RabbitMQ (Topic Exchange) | 3.13 |
| **定时调度** | Quartz (内存模式) | — |
| **缓存** | Redis | 7 |
| **数据库** | MySQL | 8.0 |
| **爬虫引擎** | Scrapy + DrissionPage | Scrapy 2.x |
| **消费端** | Python (pika + aiohttp + aiomysql) | 3.12 |
| **容器化** | Docker + Docker Compose | — |
| **API 文档** | SpringDoc (OpenAPI 3.0) | — |

### 前端依赖

- **UI 框架**: Element Plus 2.9
- **状态管理**: Pinia 2.3
- **路由**: Vue Router 4.5
- **图表**: ECharts 5.5 + vue-echarts 7.0
- **HTTP**: Axios 1.7
- **Mock**: MockJS 1.1 (开发环境)
- **CSS**: Sass 1.83

### 后端依赖

- **Web**: spring-boot-starter-web
- **ORM**: mybatis-plus 3.5.15
- **安全**: spring-boot-starter-security + jjwt 0.12.6
- **消息**: spring-boot-starter-amqp (RabbitMQ)
- **调度**: spring-boot-starter-quartz
- **文档**: springdoc-openapi (Swagger UI)
- **数据库**: mysql-connector-j

---

## 架构概览

```
                         ┌───────────────────────────────┐
                         │       Frontend (Vue 3)        │
                         │       Vite :5173              │
                         └─────────────┬─────────────────┘
                                       │ HTTP /admin/**
                                       ▼
┌─────────────────────────────────────────────────────────────────────┐
│                 backend-admin (Spring Boot :8080)                    │
│                                                                     │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌──────────────────┐  │
│  │  system  │  │  crawler │  │ dashboard │  │     common       │  │
│  │  (RBAC)  │  │ (调度+API)│  │  (数据看板)│  │ JWT / AOP / CORS │  │
│  └──────────┘  └─────┬─────┘  └───────────┘  └──────────────────┘  │
│                       │                                              │
│            ┌──────────┼──────────┐                                   │
│            ▼          ▼          ▼                                   │
│     ┌────────────────────────────────────┐                           │
│     │        RabbitMQ Message Bus        │                           │
│     │  Exchange: crawler.tasks           │                           │
│     │  Queues: site.crawl / order.crawl  │                           │
│     │          product.crawl             │                           │
│     └────────────────────────────────────┘                           │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────────────┐
│                   crawler-consumer (Python)                          │
│                                                                      │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────┐  ┌──────────────┐ │
│  │  consumers   │  │   crawlers   │  │    db     │  │  scrapy_app  │ │
│  │ site/order/  │  │ site/order   │  │ repository│  │ shopify/woo  │ │
│  │ product      │  │              │  │           │  │              │ │
│  └─────────────┘  └──────────────┘  └───────────┘  └──────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
                          │
                          ▼
          ┌───────────────┴───────────────┐
          ▼                               ▼
┌──────────────────┐           ┌──────────────────┐
│  MySQL :3306     │           │  Redis :6379     │
│  cyberflow (管理)│           │  缓存 / 去重      │
│  scraped_data    │           └──────────────────┘
└──────────────────┘
```

### 消息流

```
backend-admin ──(publish)──► RabbitMQ ──(consume)──► crawler-consumer
  TaskMessagePublisher        crawler.tasks         site/order/product consumer
                                                          │
                              RabbitMQ ◄──(publish)───────┘
                              task.result         TaskResultConsumer
                                                         │
                                                    更新任务状态
```

### 数据库

```
MySQL :3306
├── cyberflow (管理库)
│   ├── sys_user / sys_role / sys_menu           # RBAC 权限
│   ├── sys_user_role / sys_role_menu            # 关联表
│   ├── sys_operation_log                        # 操作日志
│   ├── selector_template                        # 选择器模板
│   ├── crawl_site_config                        # 站点配置
│   ├── site_template_mapping                    # 站点-模板映射
│   ├── task_history                             # 任务历史
│   └── crawl_cursor                             # 采集游标 (断点续爬)
│
└── scraped_data (爬取数据)
    ├── site_info                                # 站点信息
    ├── ecommerce_product                        # 商品数据
    ├── order_info                               # 订单数据
    └── site_indexing_history                    # 索引历史
```

---

## 模块说明

### 1. backend-admin — Spring Boot 管理后台

```
backend-admin/
├── Dockerfile
├── pom.xml
└── src/main/java/com/cyberflow/admin/
    ├── CyberFlowAdminApplication.java       # 启动入口
    ├── common/                              # 基础设施
    │   ├── CorsConfig.java                  # 跨域
    │   ├── GlobalExceptionHandler.java      # 全局异常
    │   ├── JwtUtils.java                    # JWT 工具
    │   ├── MybatisPlusConfig.java           # MyBatis-Plus 分页
    │   ├── OperationLogAspect.java          # 操作日志 AOP
    │   └── Result.java                      # 统一响应体
    ├── system/                              # 系统管理 (RBAC)
    │   ├── config/SecurityConfig.java       # Spring Security
    │   ├── controller/                      # Auth / User / Role / Menu / OpLog
    │   ├── entity/                          # SysUser / SysRole / SysMenu ...
    │   ├── mapper/                          # MyBatis Mapper 接口
    │   └── service/                         # 业务服务
    ├── crawler/                             # 爬虫管理
    │   ├── config/QuartzConfig.java         # 定时任务
    │   ├── config/RabbitMQConfig.java       # 消息队列配置
    │   ├── controller/CrawlerController.java
    │   ├── scheduler/                       # SiteCrawlJob / OrderCrawlJob
    │   ├── messaging/                       # TaskMessagePublisher / TaskResultConsumer
    │   ├── service/CrawlerService.java
    │   ├── selector/                        # 选择器模板 CRUD
    │   ├── siteconfig/                      # 站点配置 CRUD
    │   └── task/                            # 任务历史 & 采集游标
    └── dashboard/                           # 数据看板
        ├── controller/DashboardController.java
        ├── mapper/                          # SiteInfo / Order / Product
        └── service/DashboardService.java
```

### 2. frontend — Vue 3 管理前端

```
frontend/
├── vite.config.js              # Vite 配置 (代理 /admin → :8080)
└── src/
    ├── main.js                 # 入口 (挂载 App + Router + Pinia)
    ├── App.vue                 # 根组件
    ├── api/                    # API 层 (auth / crawler / dashboard / selector / system)
    ├── router/index.js         # 路由配置
    ├── store/                  # Pinia (app + user)
    ├── utils/request.js        # Axios 拦截器 (Token + 401)
    ├── mock/                   # Mock 数据 (开发用)
    └── views/                  # 页面组件
        ├── login/              # 登录页
        ├── layout/             # 主布局 (侧栏 + 顶栏)
        ├── dashboard/          # Overview / SiteList / OrderList / ProductList
        ├── crawler/            # SiteCrawler / OrderCrawler / CollectCrawler
        │                       # SelectorTemplate / SiteConfig / TaskHistory
        └── system/             # UserList / RoleList / MenuTree / OperationLog
```

### 3. crawler-consumer — Python 爬虫消费者

```
crawler-consumer/
├── Dockerfile
├── requirements.txt            # pika, aiohttp, aiomysql, loguru
├── config.py                   # 连接配置 (RabbitMQ / MySQL / Redis)
├── main.py                     # 启动入口
├── consumers/                  # RabbitMQ 消费者
│   ├── base_consumer.py        # 基础类 (连接/ACK/重试)
│   ├── site_consumer.py        # 站点采集
│   ├── order_consumer.py       # 订单采集
│   └── product_consumer.py     # 商品采集
├── crawlers/                   # 爬虫执行器
│   ├── site_crawler.py         # 站点爬虫 (DrissionPage)
│   └── order_crawler.py        # 订单爬虫
└── db/
    └── repository.py           # 数据库读写
```

### 4. crawler-service — Scrapy 爬虫项目

```
crawler-service/
├── requirements.txt            # scrapy, DrissionPage, pandas, SQLAlchemy 等
└── app/
    ├── main.py                 # 旧 FastAPI 入口说明，当前不提供运行中的 app 对象
    ├── core/                   # config / database
    ├── model/                  # 数据模型 (order / site_info / request)
    ├── services/               # site_crawler / site_collect / order_crawler
    └── crawler/ecommerce_spider/
        ├── scrapy.cfg
        └── ecommerce_spider/
            ├── items.py        # Scrapy Item
            ├── middlewares.py  # 中间件
            ├── pipelines.py    # 数据管道
            ├── settings.py     # 爬虫配置
            └── spiders/
                ├── shopify_crawl.py   # Shopify 站点
                ├── woo_crawl.py       # WooCommerce 站点
                └── exchange_rates.json
```

---

## 快速开始

### 环境要求

- Docker & Docker Compose
- (本地开发) JDK 17 / Maven 3.9 / Node.js 20+ / Python 3.12

### Docker 基础设施启动

```bash
# 1. 配置环境变量
cp .env.docker .env
# 按需编辑 .env 中的密码和端口

# 2. 启动基础设施服务
docker compose up -d

# 3. 查看运行状态
docker compose ps
```

当前 `docker-compose.yml` 默认启用 RabbitMQ、MySQL、Redis。`backend-admin` 和 `crawler-consumer` 的 Docker 服务配置仍保留在文件中，但处于注释状态；如需容器化启动应用服务，需要先取消对应服务注释并确认环境变量。

基础设施端口：

| 服务 | 端口 | 说明 |
|------|------|------|
| RabbitMQ | `:5672` | AMQP 消息 |
| RabbitMQ 管理 | `:15672` | Web 管理界面 |
| MySQL | `:3306` | 数据库 |
| Redis | `:6379` | 缓存 |

本地开发端口：

| 服务 | 端口 | 说明 |
|------|------|------|
| 管理后台 API | `:8080` | Spring Boot REST API |
| 管理后台前端 | `:5173` | Vue 3 Dev Server |

### 本地开发

```bash
# 后端
cd backend-admin
mvn spring-boot:run

# 前端 (另一个终端)
cd frontend
npm install
npm run dev

# 爬虫消费者 (另一个终端)
cd crawler-consumer
pip install -r requirements.txt
python main.py
```

`crawler-service` 当前主要作为 Scrapy 项目目录，由 `crawler-consumer` 中的商品消费者以子进程方式调度。`crawler-service/app/main.py` 只保留旧 FastAPI 入口说明，当前没有定义可供 `uvicorn app.main:app` 启动的 `app` 对象。

### API 文档

启动后端后访问:
- Swagger UI: `http://localhost:8080/admin/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/admin/api-docs`

---

## 功能模块

| 模块 | 功能 |
|------|------|
| **登录认证** | JWT Token + Spring Security + RBAC 权限 |
| **用户管理** | 用户 CRUD、角色分配 |
| **角色管理** | 角色 CRUD、菜单权限分配 |
| **菜单管理** | 树形菜单、动态路由 |
| **操作日志** | AOP 自动记录 API 调用 |
| **平台配置** | Admin API、Payment API、采集策略和收入参数维护 |
| **站点配置** | Shopify 商品站点注册与手动商品采集 |
| **选择器模板** | Shopify 默认模板标识 |
| **站点采集** | 后台配置驱动的每日增量站点采集 |
| **订单采集** | 后台配置驱动的每日增量订单同步 |
| **任务历史** | 任务状态、耗时、结果查看 |
| **采集游标** | 断点续爬，记录采集进度 |
| **数据看板** | 站点/订单/商品统计 + ECharts 图表 |

---

## 定时任务

| 任务 | Cron 表达式 | 说明 |
|------|------------|------|
| SiteCrawlJob | `0 0 2 * * ?` | 每天凌晨 2:00 执行站点增量采集 |
| OrderCrawlJob | `0 0 3 * * ?` | 每天凌晨 3:00 执行订单增量采集 |

> 可在 Spring Boot 管理后台修改 cron 和启用状态。站点任务使用 `crawl_cursor.site_crawler` 的 `last_updated_at` 增量游标；订单任务使用 `crawl_cursor.order_crawler` 的最大订单 ID 增量游标。

---

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_ROOT_PASSWORD` | `123456` | MySQL root 密码 |
| `MYSQL_USER` | `root` | MySQL 用户名 |
| `RABBITMQ_USER` | `admin` | RabbitMQ 用户名 |
| `RABBITMQ_PASS` | `admin123` | RabbitMQ 密码 |
| `JWT_SECRET` | `cyberflow-admin-jwt-secret-key-2026` | JWT 签名密钥 |
| `ADMIN_API_BASE_URL` | — | Admin API 地址，后台配置优先 |
| `ADMIN_API_USERNAME` | — | Admin API 账号，后台配置优先 |
| `ADMIN_API_PASSWORD` | — | Admin API 密码，后台配置优先 |
| `PAYMENT_API_BASE_URL` | — | Payment API 地址，后台配置优先 |
| `PAYMENT_API_ACCOUNT` | — | Payment API 账号，后台配置优先 |
| `PAYMENT_API_PASSWORD` | — | Payment API 密码，后台配置优先 |
| `VERIFY_SSL` | `true` | 消费者本地兜底 SSL 校验开关 |
| `ADMIN_PORT` | `8080` | 管理后台端口 |
| `MYSQL_PORT` | `3306` | MySQL 端口 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `RABBITMQ_PORT` | `5672` | RabbitMQ 端口 |
| `RABBITMQ_MGMT_PORT` | `15672` | RabbitMQ 管理界面端口 |
