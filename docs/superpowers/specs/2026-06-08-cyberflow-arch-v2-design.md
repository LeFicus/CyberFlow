# CyberFlow 架构 v2.0 — 设计文档

> 日期: 2026-06-08 | 状态: Draft

## 1. 概述

### 1.1 动机

当前 v1 架构存在以下结构性问题：

| 痛点 | 现状 | 影响 |
|------|------|------|
| 三层调用链 | Vue → Spring Boot → FastAPI → Celery，FastAPI 为纯转发层 | 增加延迟和故障面 |
| 全量拉取 | 每次分页遍历全部数据 | 90% 数据无变化，浪费带宽 |
| 同步阻塞 | Celery Worker 使用同步 httpx.Client | concurrency=1 完全无法并发 |
| 内存追踪 | ConcurrentHashMap 存任务状态 | 重启即丢失 |
| Scrapy 游离 | 手动 python xxx.py 运行 | 无调度、无状态、无重试 |
| 选择器散落 | JSON 文件散落在 configs/selectors/ | 无版本管理、无法复用 |

### 1.2 目标

- **Spring Boot 为调度大脑**：直接控制所有爬虫任务，不再经过 FastAPI 转发
- **RabbitMQ 为消息中枢**：彻底解耦调度与执行，支持重试/死信/延迟
- **Python asyncio 执行器**：抛弃 Celery，纯消息消费者 + aiohttp 高并发
- **增量爬取**：订单用游标增量，站点用时间窗口增量
- **选择器模板库**：系统化复用选择器配置，支持多模板合并为或选择器链
- **任务持久化**：MySQL 表追踪任务历史与增量游标

---

## 2. 目标架构全景

```
┌──────────────────────────────────────────────────────────┐
│                  Vue.js 前端 (Vite)                        │
│  站点管理 · 订单看板 · 商品列表 · 爬虫调度 · 系统管理        │
│  选择器模板库管理 · 站点注册(域名+选模板+分类)               │
└────────────┬─────────────────────────────────────────────┘
             │ JWT + REST
             ▼
┌──────────────────────────────────────────────────────────┐
│              🧠 Spring Boot Admin :8080                    │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ Quartz 调度   │  │ 任务管理      │  │ 数据看板       │  │
│  │              │  │              │  │               │  │
│  │ · 站点 每天1次 │  │ · 发布到 MQ   │  │ · MyBatis-Plus │  │
│  │ · 订单 每6小时 │  │ · 查询状态    │  │ · 直连 MySQL   │  │
│  │ · 商品 手动触发 │  │ · 历史记录    │  │               │  │
│  └──────────────┘  └──────────────┘  └───────────────┘  │
│                                                          │
│  ┌──────────────────────────────────────────────────┐    │
│  │ 站点配置管理                                       │    │
│  │ · 选择器模板 CRUD                                  │    │
│  │ · 站点注册 (域名 + 多模板 + 分类)                    │    │
│  │ · site_template_mapping 维护                       │    │
│  └──────────────────────────────────────────────────┘    │
│                                                          │
│  RBAC (Spring Security + JWT) · 操作审计 (AOP)            │
└────────────┬─────────────────────────────────────────────┘
             │ 发布任务消息
             ▼
┌──────────────────────────────────────────────────────────┐
│                  🐰 RabbitMQ                              │
│                                                          │
│  Exchange: crawler.tasks (topic)                         │
│  ┌─────────────┐ ┌─────────────┐ ┌──────────────┐       │
│  │site.crawl   │ │order.crawl  │ │product.crawl  │       │
│  │(Queue)      │ │(Queue)      │ │(Queue)        │       │
│  └─────────────┘ └─────────────┘ └──────────────┘       │
│  ┌─────────────┐ ┌──────────────────────────────┐       │
│  │task.result  │ │task.dead (DLX, 失败重试3次)    │       │
│  │(Queue)      │ │                              │       │
│  └─────────────┘ └──────────────────────────────┘       │
└────────────┬─────────────────────────────────────────────┘
             │ 消费任务
             ▼
┌──────────────────────────────────────────────────────────┐
│              🐍 Python asyncio Consumers                  │
│                                                          │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐     │
│  │Site Consumer │ │Order Consumer│ │Product Consumer│    │
│  │pika + aiohttp│ │pika + aiohttp│ │pika + subprocess│   │
│  │              │ │              │ │              │     │
│  │增量: 时间窗口 │ │增量: order_id│ │Scrapy Spider  │     │
│  │→ site_info   │ │→ orders      │ │→ ecommerce_  │     │
│  │              │ │              │ │  products    │     │
│  └──────────────┘ └──────────────┘ └──────────────┘     │
│                                                          │
│  所有 Consumer 从 DB 读取配置，结果写回 DB + task.result    │
└────────────┬─────────────────────────────────────────────┘
             │ httpx / aiohttp / Scrapy
             ▼
┌──────────────────────────────────────────────────────────┐
│  外部平台              │  存储                             │
│  · 管理平台 API         │  · MySQL (cyberflow)             │
│  · 支付平台 API         │  · MySQL (scraped_data)          │
│  · Shopify / WooCommerce│  · Redis (仅 Scrapy SKU 去重)    │
└──────────────────────────────────────────────────────────┘
```

### 2.1 移除的组件

| 组件 | 原因 | 替代方案 |
|------|------|----------|
| FastAPI (:8000) | 纯转发层，无业务逻辑 | Spring Boot 直连 RabbitMQ |
| Redis Broker/Backend | 功能被 RabbitMQ 覆盖 | RabbitMQ 持久化消息 |
| Celery Worker | 同步阻塞，框架束缚 | Python asyncio 纯消费者 |
| 同步 httpx.Client | 阻塞 Worker | aiohttp / httpx-async |
| 内存 ConcurrentHashMap | 重启丢失 | MySQL task_history 表 |
| CrawlerApiClient (RestTemplate) | FastAPI 已移除 | RabbitMQ 消息发布 |

### 2.2 保留的组件

| 组件 | 用途 |
|------|------|
| Redis | 仅 Scrapy Pipeline SKU 去重 (SADD) |
| Scrapy | 商品爬取引擎 (Shopify + WooCommerce Spiders) |
| MySQL × 2 | cyberflow (主库) + scraped_data (商品库) |
| Vue.js 前端 | 不变，仅 API 端点调整 |

---

## 3. 增量爬取策略

### 3.1 策略总览

| 数据源 | 策略 | 游标字段 | 频率 | 触发方式 |
|--------|------|----------|------|----------|
| 站点信息 | 时间窗口增量 | `last_updated_at` (API 返回) | 每天 1 次 (如 02:00) | Quartz Cron |
| 站点收录 | 时间窗口增量 | `last_recorded_at` (API 返回) | 每天 1 次 (如 03:00) | Quartz Cron |
| 订单数据 | 游标增量 | `max_order_id` (已入库最大 ID) | 每 6 小时 | Quartz Cron |
| Shopify 商品 | 按域全量 + SKU 去重 | — | 用户手动触发 | 前端触发 → MQ |
| 非 Shopify 商品 | Sitemap + 选择器解析 | — | 用户手动触发 | 前端触发 → MQ |

### 3.2 游标存储

表 `crawl_cursor`：

```sql
CREATE TABLE crawl_cursor (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    cursor_key  VARCHAR(100) NOT NULL UNIQUE,  -- e.g. 'site_crawler', 'order_crawler'
    cursor_value VARCHAR(255) NOT NULL,         -- e.g. '2026-06-08T02:00:00', '1234567'
    last_sync_at DATETIME NOT NULL,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 3.3 订单游标增量流程

1. Quartz 触发 (每6小时) → Spring Boot 读 `crawl_cursor` 获取 `max_order_id`
2. 组装消息 `{cursor: "1234567", ...}` → 发布到 `order.crawl` 队列
3. Python Order Consumer: 请求 API `?since_id=1234567&page_size=100` → 翻页直到无新数据
4. 入库后更新 `crawl_cursor.cursor_value` 为新 `max_order_id`
5. 发送 `task.result` 消息 (包含 `rows_affected`, `new_cursor`)

### 3.4 站点时间窗口增量流程

1. Quartz 触发 (每天1次) → Spring Boot 读 `crawl_cursor` 获取 `last_updated_at`
2. 组装消息 `{since: "2026-06-07T02:00:00", ...}` → 发布到 `site.crawl`
3. Python Site Consumer: 请求 API `?updated_since=2026-06-07T02:00:00` → 仅处理变更数据
4. 入库后更新游标为本次启动时间

---

## 4. 选择器模板库

### 4.1 设计动机

当前系统在 `configs/selectors/` 目录下散落 JSON 文件（magnolia.json、sachdevabeauty_com.json、test.json），由 `WooCrawlSpider` 通过 `config_file` 参数加载。问题：
- 无法在前端管理
- 每站点一个文件，不能复用
- 无版本控制和模板概念

### 4.2 数据模型

```sql
-- 选择器模板库
CREATE TABLE selector_template (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                     VARCHAR(100) NOT NULL,          -- e.g. "Magnolia Theme"
    platform                 VARCHAR(20) NOT NULL,           -- woo / shopify / magento / custom
    title_selector           VARCHAR(500),                   -- XPath/CSS
    price_selector           VARCHAR(500),
    price_regex              VARCHAR(200),
    description_selector     VARCHAR(500),
    images_selector          VARCHAR(500),
    currency                 VARCHAR(10) DEFAULT 'USD',
    breadcrumb_links_selector VARCHAR(500),
    breadcrumb_last_selector  VARCHAR(500),
    site_map_selector        VARCHAR(500),                   -- 非 Shopify 专用
    is_system                 TINYINT DEFAULT 0,             -- 系统预置模板不可删除
    created_at                DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 商品爬取站点注册
CREATE TABLE crawl_site_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain      VARCHAR(255) NOT NULL,
    type        VARCHAR(20) NOT NULL,            -- shopify / woo / custom
    category    VARCHAR(100) DEFAULT '未知分类',  -- 自定义分类（如 服饰与配饰）
    status      VARCHAR(20) DEFAULT 'active',    -- active / paused
    created_by  BIGINT,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 站点↔模板多对多关联
CREATE TABLE site_template_mapping (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_config_id      BIGINT NOT NULL,                    -- FK → crawl_site_config.id
    template_id         BIGINT NOT NULL,                    -- FK → selector_template.id
    extra_selectors     JSON,                               -- 可选，该站点额外的选择器（会合并进最终选择器）
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (site_config_id) REFERENCES crawl_site_config(id),
    FOREIGN KEY (template_id) REFERENCES selector_template(id),
    UNIQUE KEY uk_site_template (site_config_id, template_id)
);
```

### 4.3 选择器合并策略

Python Product Consumer 处理非 Shopify 商品详情页时，将站点关联的所有模板的**相同键的值用 `|` (XPath union) 合并**为一个或选择器链，一次性匹配：

**合并规则**：
- 读取该站点所有关联的 `selector_template` + 每个 mapping 的 `extra_selectors`
- 对每个字段键（title、price、description 等），收集所有非空选择器值
- 用 ` | ` 连接，形成 XPath 联合表达式
- 一次性执行 XPath 查询，取第一个非空结果

**示例 — 站点关联了 Magnolia Theme + WooCommerce Default**：

```
合并前 (两个独立模板):
  Magnolia:     title = "//h1/text()"
  Woo Default:  title = "//h1[@class='product_title entry-title']/text() | //h1[contains(@class, 'product-title')]/text()"

合并后 (一个联合表达式):
  title = "//h1/text() | //h1[@class='product_title entry-title']/text() | //h1[contains(@class, 'product-title')]/text()"
```

**extra_selectors 的作用**：
- 站点 mapping 中的 `extra_selectors` (JSON) 提供该站点特有的额外选择器
- 例如某站点标题有特殊 class：`{"title": "//h1[@class='custom-header']/text()"}`
- 会合并进最终的联合表达式

**全部选择器联合后仍无结果** → 记录警告到日志，该 URL 的对应字段留空，不影响其他字段

### 4.4 预置模板（从现有 JSON 迁移）

| 模板名 | 平台 | 来源 |
|--------|------|------|
| WooCommerce Default | woo | woo_crawl.py 内置默认值 |
| WooCommerce — Sachdeva Beauty | woo | sachdevabeauty_com.json |
| Magnolia Theme | woo | magnolia.json |
| Generic Woo Test | woo | test.json |
| Shopify Default | shopify | 无需选择器，走 products.json |

### 4.5 管理功能

Spring Boot 提供：
- 模板 CRUD (列表、新建、编辑、删除、克隆)
- 从现有 JSON 文件批量导入模板
- 站点注册页面：输入域名 → 选类型 → 多选模板（可拖拽排序）→ 可选覆写选择器 → 选分类
- 模板测试功能：输入示例 URL，实时预览解析结果

---

## 5. 消息队列设计

### 5.1 Exchange & Queue

```
Exchange: crawler.tasks (type: topic)

Routing:
  crawler.task.site     → Queue: site.crawl      (站点爬取)
  crawler.task.order    → Queue: order.crawl      (订单爬取)
  crawler.task.product  → Queue: product.crawl    (商品爬取)

Queue 属性:
  · durable: true
  · dead-letter-exchange: crawler.tasks.dlx
  · dead-letter-routing-key: crawler.task.dead
  · x-message-ttl: 每消息可设置过期时间

Exchange: crawler.tasks.dlx (type: topic)
  Routing: crawler.task.dead → Queue: task.dead

Queue: task.result
  · Spring Boot 消费此队列，更新 task_history 状态
```

### 5.2 消息格式

```json
// 站点爬取任务
{
  "task_id": "uuid",
  "type": "site_crawl",
  "trigger": "cron",
  "timestamp": "2026-06-08T02:00:00Z",
  "payload": {
    "username": "admin",
    "password": "pass123",
    "cursor": { "last_updated_at": "2026-06-07T02:00:00Z" }
  }
}

// 订单爬取任务
{
  "task_id": "uuid",
  "type": "order_crawl",
  "trigger": "cron",
  "timestamp": "2026-06-08T06:00:00Z",
  "payload": {
    "cursor": { "max_order_id": "1234567" }
  }
}

// 商品爬取任务 (用户触发)
{
  "task_id": "uuid",
  "type": "product_crawl",
  "trigger": "manual",
  "triggered_by": "user_id",
  "timestamp": "2026-06-08T10:30:00Z",
  "payload": {
    "site_config_id": 42,
    "domain": "example.com",
    "type": "woo",
    "category": "服饰与配饰"
  }
}

// 任务结果
{
  "task_id": "uuid",
  "status": "success" | "failed",
  "rows_affected": 150,
  "new_cursor": { "max_order_id": "1234717" },
  "error": null,
  "duration_ms": 45200,
  "finished_at": "2026-06-08T06:15:23Z"
}
```

---

## 6. 任务生命周期与追踪

### 6.1 task_history 表

```sql
CREATE TABLE task_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         VARCHAR(64) NOT NULL UNIQUE,
    type            VARCHAR(30) NOT NULL,          -- site_crawl / order_crawl / product_crawl / site_index
    trigger_type    VARCHAR(20) NOT NULL,           -- cron / manual
    triggered_by    VARCHAR(64),                    -- 手动触发时的用户 ID
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                                   -- PENDING → RUNNING → SUCCESS / FAILED
    cursor_before   VARCHAR(255),                   -- 任务开始时的游标值
    cursor_after    VARCHAR(255),                   -- 任务结束后的游标值
    rows_affected   INT DEFAULT 0,
    error_msg       TEXT,
    duration_ms     BIGINT,
    started_at      DATETIME,
    finished_at     DATETIME,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 6.2 状态流转

```
PENDING → (Python Consumer picks up) → RUNNING → SUCCESS
                                               → FAILED → (DLX retry ≤ 3次) → RUNNING
                                                                              → FAILED (最终)
```

1. Spring Boot 发布任务 → INSERT task_history (status=PENDING)
2. Python Consumer 消费 → UPDATE task_history (status=RUNNING, started_at=now)
3. 执行完成 → 发布 task.result → Spring Boot 消费 → UPDATE task_history (status=SUCCESS/FAILED)
4. 失败任务 → RabbitMQ DLX 自动重试（最多 3 次），超过后标记 FAILED

---

## 7. Python Consumer 架构

### 7.1 包结构

```
crawler-consumer/
├── requirements.txt
├── main.py                    # 入口，启动所有 Consumer
├── config.py                  # 配置 (RabbitMQ URL, DB URL)
├── consumers/
│   ├── __init__.py
│   ├── base_consumer.py       # 基类：连接管理、ACK/NACK、重试
│   ├── site_consumer.py       # 站点信息爬取
│   ├── order_consumer.py      # 订单爬取
│   └── product_consumer.py    # 商品爬取 (调度 Scrapy)
├── crawlers/
│   ├── __init__.py
│   ├── site_crawler.py        # 原 SiteCrawler 逻辑 (改为 async)
│   ├── order_crawler.py       # 原 OrderCrawler 逻辑 (改为 async)
│   └── product_crawler.py     # Scrapy 子进程管理
├── db/
│   ├── __init__.py
│   ├── models.py              # 数据模型
│   └── repository.py          # 数据库操作 (读写游标、配置等)
└── scrapy_app/                # Scrapy 项目 (从原 ecommerce_spider 迁移)
    └── ...
```

### 7.2 关键设计

- 所有 I/O 使用 asyncio (aiohttp 替代 httpx, aiomysql 替代同步 mysql-connector)
- Scrapy 不支持 asyncio native，通过 `asyncio.create_subprocess_exec` 启动子进程
- 重试逻辑在 `base_consumer.py` 中：nack → RabbitMQ DLX 自动重投
- 优雅关闭：SIGTERM → 等待当前任务完成 → ACK → 退出

---

## 8. Spring Boot 改造清单

### 8.1 新增模块

| 模块 | 说明 |
|------|------|
| `crawler/scheduler/` | Quartz 定时任务配置 |
| `crawler/messaging/` | RabbitMQ 发布/消费 (Spring AMQP) |
| `crawler/selector/` | 选择器模板 CRUD Controller + Service + Mapper |
| `crawler/siteconfig/` | 站点注册 CRUD |
| `crawler/task/` | 任务历史查询 |

### 8.2 修改模块

| 模块 | 改动 |
|------|------|
| `CrawlerController` | 移除 RestTemplate 代理，改为 RabbitMQ 发布 + task_history 查询 |
| `CrawlerApiClient` | 废弃，替换为 `RabbitTemplate` |
| `CrawlerService` | 简化，只做任务发布和状态查询 |
| `application.yml` | 新增 RabbitMQ 和 Quartz 配置 |

### 8.3 移除模块

- `CrawlerApiClient.java` — 不再需要调用 FastAPI
- 对 FastAPI 的所有 HTTP 调用逻辑

---

## 9. 前端改动清单

| 页面 | 改动 |
|------|------|
| 爬虫管理 (Crawler) | 保留站点/订单/商品触发，增加定时任务开关和下次执行时间展示 |
| 任务历史 (TaskHistory) | 已有页面，数据源从 Mock 切到真实 API |
| 🆕 选择器模板库 | 新页面：模板列表、新建/编辑/克隆/删除、从 JSON 导入 |
| 🆕 站点注册 | 新页面：输入域名 → 选类型 → 多选模板（拖拽排序）→ 选分类 → 注册并爬取 |
| Dashboard | 数据源不变，仍通过 MyBatis-Plus 直连查询 |

---

## 10. 迁移路径

### Phase 1: 基础设施 (无业务影响)
1. 部署 RabbitMQ
2. Spring Boot 集成 Spring AMQP + Quartz
3. Python 新建 `crawler-consumer/` 项目
4. 创建新表 (`task_history`, `crawl_cursor`, `selector_template`, `crawl_site_config`, `site_template_mapping`)

### Phase 2: 站点爬取迁移
1. Python 实现 `SiteConsumer` + async `site_crawler.py`
2. Spring Boot 实现 Quartz 定时触发站点爬取 → 发布到 MQ
3. 并行运行 1 周，对比新旧结果
4. 下线 FastAPI 站点端点

### Phase 3: 订单爬取迁移
1. Python 实现 `OrderConsumer` + async `order_crawler.py`
2. Spring Boot 实现 Quartz 定时触发订单爬取 → 发布到 MQ
3. 并行运行，对比结果
4. 下线 FastAPI 订单端点

### Phase 4: 商品爬取迁移
1. 从 JSON 文件导入预置选择器模板
2. 实现选择器模板库管理页面
3. 实现站点注册页面
4. Python 实现 `ProductConsumer`（调度 Scrapy 子进程）
5. 下线手动脚本

### Phase 5: 清理
1. 下线 FastAPI 服务
2. 下线 Celery Worker
3. 清理 Redis 中废弃的 Celery 数据
4. 更新文档和 docker-compose.yml

---

## 11. 待定事项

- [ ] RabbitMQ 集群方案（生产环境高可用）
- [ ] 大规模站点时的 Consumer 水平扩展策略
- [ ] 监控告警方案 (Prometheus + Grafana?)
- [ ] 非 Shopify 站点首次全量爬取的时间预估和限流策略
