# CyberFlow

CyberFlow 是电商数据采集与管理平台，提供站点和订单同步、商品采集、任务调度、数据看板及基于角色的权限管理。商品采集支持 Shopify、WooCommerce、BigCommerce、Magento、Wix、Ecwid 和 Shopline；具体站点的页面结构与授权条件可能影响采集结果。

## 快速开始

### 使用 Docker Compose 启动服务

需要 Docker 和 Docker Compose。以下命令在仓库根目录执行：

```bash
cp .env.docker.example .env
# 编辑 .env，至少替换 MySQL、RabbitMQ、JWT 和通知加密密钥的占位值
docker compose up -d --build
docker compose ps
```

Compose 会启动 MySQL、RabbitMQ、Redis、管理后端和爬虫消费者，并通过一次性的 `db-migrate` 服务执行尚未应用的 SQL 迁移。首次启动时，MySQL 会使用 `script/init_all_databases.sql` 初始化数据库。迁移记录保存在 `cyberflow.schema_migrations`；迁移失败时，后端和消费者不会启动。

前端不在 Compose 编排中，需要另开终端运行：

```bash
cd frontend
npm ci
npm run dev
```

打开 <http://localhost:5173>。前端开发服务器将 `/admin` 请求代理到 `http://localhost:8080`。首次初始化的管理员账号为 `admin`，初始密码为 `admin123`；部署后请及时修改。

| 入口 | 默认地址 |
| --- | --- |
| 前端开发服务器 | <http://localhost:5173> |
| 后端 API | <http://localhost:8080> |
| Swagger UI | <http://localhost:8080/admin/swagger-ui.html> |
| OpenAPI 文档 | <http://localhost:8080/admin/api-docs> |
| RabbitMQ 管理界面 | <http://localhost:15672> |

MySQL、RabbitMQ 和 Redis 默认分别占用主机端口 `3306`、`5672` 和 `6379`；后端默认占用 `8080`。这些端口可通过 `.env` 中的 `MYSQL_PORT`、`RABBITMQ_PORT`、`REDIS_PORT` 和 `ADMIN_PORT` 修改。前端开发服务器的代理目标固定为本机 `8080`，修改后端端口时需同步调整 `frontend/vite.config.js`。

### 本地开发后端与消费者

需要 JDK 17、Maven 3.9、Node.js 20+ 和 Python 3.12。先启动依赖服务与数据库迁移：

```bash
docker compose up -d mysql rabbitmq redis db-migrate
```

然后在不同终端启动所需模块：

```bash
# 管理后端
cd backend-admin
mvn spring-boot:run
```

```bash
# 爬虫消费者
cd crawler-consumer
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python main.py
```

本地进程不会自动读取仓库根目录的 Compose `.env`。启动前，需为后端设置 `MYSQL_USER`、`MYSQL_PASSWORD`、`RABBITMQ_USER`、`RABBITMQ_PASS` 等环境变量；消费者还需设置指向本机数据库的 `DATABASE_URL` 和 `SCRAPED_DB_URL`。两者的凭据应与 `.env` 保持一致。商品消费者会从相邻的 `crawler-service` 目录运行 Scrapy 项目；`crawler-service/app/main.py` 不是可独立启动的 FastAPI 服务入口。

## 功能与架构

| 模块 | 主要职责 |
| --- | --- |
| `frontend/` | Vue 3 管理界面，包含看板、站点、订单、商品、任务和系统管理页面 |
| `backend-admin/` | Spring Boot API、JWT/RBAC、业务配置、Quartz 调度和消息发布 |
| `crawler-consumer/` | 消费 RabbitMQ 任务，执行站点、订单及商品采集并回传结果 |
| `crawler-service/` | 供商品消费者调度的 Scrapy 爬虫项目 |
| `script/` | 数据库初始化与增量迁移脚本 |
| `docs/` | 专题设计、修复和升级文档 |

任务由后端发布到 RabbitMQ，消费者执行采集并写入 MySQL，随后将执行结果回传给后端。MySQL 使用 `cyberflow` 存储管理数据、`scraped_data` 存储采集数据；Redis 用于相关缓存功能。

主要技术：Vue 3、Vite 6、Element Plus、Pinia、ECharts、Tailwind CSS 4；Spring Boot 3.4、Java 17、MyBatis-Plus、Spring Security、Quartz；Python 3.12、Scrapy；MySQL 8.0、RabbitMQ 3.13、Redis 7。

### 支付平台风控与有效订单

订单按现有增量游标采集新增记录，同时保存支付平台的 `is_alarm`（0=正常，非 0=风控，例如 10009）和 `is_valid`（0=有效，1=无效）标记。平台风控码归一化后保存在本地 `is_fraud`（1=风控，0=非风控），接口原始 `is_fraud` 不用于风控判定，不扫描或刷新历史订单标记。首页“今日风控订单”按今日下单且平台标记为风控的原始订单数量展示，遵循当前用户与组别的数据权限。

去重订单统计全部订单，按邮箱或收货地址关联去重，包含平台风控订单；有效去重订单仅统计平台有效且非风控的订单。风控订单按原始笔数独立统计。标记未同步时显示“未同步”，不会推断为非风控，也不计入有效去重。邮箱格式与历史人工审核记录不再参与订单判定，订单列表支持查看和筛选平台风控状态。

## 配置

以 [`.env.docker.example`](.env.docker.example) 为 Docker 配置模板。该文件列出了可覆盖的环境变量；不要将填有真实凭据的 `.env` 提交到仓库。

| 配置 | 用途 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD`、`MYSQL_USER` | MySQL 访问凭据 |
| `RABBITMQ_USER`、`RABBITMQ_PASS` | RabbitMQ 访问凭据 |
| `JWT_SECRET` | 登录令牌签名密钥 |
| `NOTIFICATION_ENCRYPTION_KEY` | 通知 Webhook 等凭据的加密密钥 |
| `DEEPSEEK_API_KEY` | AI 辅助新站点生成；使用该功能时配置 |
| `ADMIN_API_*`、`PAYMENT_API_*` | 外部平台连接的环境兜底值；后台配置优先 |
| `PRODUCT_*` | 商品采集超时、代理、币种、页面上限和平台配置等选项 |
| `ADMIN_PORT`、`MYSQL_PORT`、`REDIS_PORT`、`RABBITMQ_PORT`、`RABBITMQ_MGMT_PORT` | 主机端口覆盖值 |

数据库初始管理员账号来自 `script/init_all_databases.sql`。站点素材和商品导出分别保存在 Docker 持久化卷中。更新后端或消费者代码后，使用 `docker compose up -d --build` 重新构建并启动镜像。

### 定时任务

默认计划定义在 `backend-admin/src/main/resources/application.yml`，使用 Asia/Shanghai 时区：

| 任务 | 默认 Cron | 执行时间 |
| --- | --- | --- |
| 站点采集 | `0 0 */6 * * ?` | 每 6 小时整点 |
| 订单采集 | `0 0 */6 * * ?` | 每 6 小时整点 |
| 站点收录快照 | `0 0 0 * * ?` | 每天 00:00 |

任务计划与启用状态可在管理后台调整。站点和订单采集使用数据库游标续采；实际运行计划以后台保存的配置为准。

## 升级与相关文档

从旧版本升级商品采集时，先暂停相关任务并备份商品表。Shopify 现在按变体存储；旧的聚合商品不会自动删除，可能与新记录共存。商品身份修复、各平台适配范围及部署核验步骤见 [商品采集修复执行文档](docs/PRODUCT_CRAWL_REPAIR_EXECUTION.md)。

- [项目详细说明](docs/PROJECT.md)
- [商品质量与分类](docs/PRODUCT_QUALITY_AND_CATEGORIES.md)
- [商品工作台](docs/PRODUCT_WORKSPACE.md)
- [分类编码修复](docs/CATEGORY_ENCODING_REPAIR.md)
