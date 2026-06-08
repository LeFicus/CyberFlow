# API 参考

## 概述

所有 API 端点均挂载在 `/crawler` 路径前缀下。启动服务后访问 `http://localhost:8000/docs` 可查看 Swagger 交互式文档。

## 端点列表

### 1. 启动站点信息爬取

```
POST /crawler/site/start
```

**描述**: 登录管理平台，抓取所有站点的域名、管理员、主题、产品分类信息，同步到 `site_info` 表。

**请求体**:

```json
{
  "username": "admin",
  "password": "your_password"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 管理平台登录账号 |
| password | string | 是 | 管理平台登录密码 |

**响应** (200):

```json
{
  "task_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "crawler_type": "site",
  "status": "Task dispatched"
}
```

**错误** (500):

```json
{
  "detail": "Site crawler dispatch error: <错误详情>"
}
```

---

### 2. 启动站点收录统计

```
POST /crawler/site/collect
```

**描述**: 登录管理平台，抓取各站点的 Google 收录数量和产品总数，记录到 `site_indexing_history` 表。同一天内同一域名会更新而非新增。

**请求体**:

```json
{
  "username": "admin",
  "password": "your_password"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 管理平台登录账号 |
| password | string | 是 | 管理平台登录密码 |

**响应** (200):

```json
{
  "task_id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "crawler_type": "site_index",
  "status": "Task dispatched"
}
```

**错误** (500):

```json
{
  "detail": "Site crawler dispatch error: <错误详情>"
}
```

---

### 3. 启动订单爬取

```
POST /crawler/order/start
```

**描述**: 登录支付平台 (`c4partypay.com`)，按时间范围分页抓取订单数据，交叉 `site_info` 表补全站点维度信息后写入 `orders` 表。自动按订单 ID 去重。

**请求体**:

```json
{
  "start_time": "2026-04-01 00:00:00",
  "end_time": "2026-04-28 23:59:59"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| start_time | string | 是 | 开始时间，格式 `YYYY-MM-DD HH:MM:SS` |
| end_time | string | 是 | 结束时间，格式 `YYYY-MM-DD HH:MM:SS` |

> 注意：此端点使用的登录账号密码来自环境变量 `CRAWLER_USERNAME` 和 `CRAWLER_PASSWORD`，而非请求体。

**响应** (200):

```json
{
  "task_id": "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "crawler_type": "order",
  "status": "Task dispatched"
}
```

**错误** (500):

```json
{
  "detail": "Order crawler dispatch error: <错误详情>"
}
```

---

### 4. 查询任务状态

```
GET /crawler/status/{task_id}
```

**描述**: 查询任意 Celery 任务的执行状态和结果。

**路径参数**:

| 参数 | 类型 | 说明 |
|------|------|------|
| task_id | string | Celery 任务 ID（由启动接口返回） |

**响应** (200):

```json
{
  "task_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "state": "SUCCESS",
  "result": "null"
}
```

**可能的 state 值**:

| state | 说明 |
|------|------|
| `PENDING` | 任务在队列中等待 |
| `STARTED` | 任务已开始执行 |
| `SUCCESS` | 任务执行成功 |
| `FAILURE` | 任务执行失败，result 字段包含错误信息 |

---

## 认证说明

- **站点爬取 / 站点收录统计**：账号密码通过请求体传入，用于登录 `http://104.233.194.18` 管理平台
- **订单爬取**：账号密码从环境变量 `CRAWLER_USERNAME` / `CRAWLER_PASSWORD` 读取，用于登录 `https://c4partypay.com` 支付平台
- 两个外部平台使用不同的认证 Token 格式：管理平台用 `Authorization: Bearer <token>`，支付平台用自定义 `token` Header
