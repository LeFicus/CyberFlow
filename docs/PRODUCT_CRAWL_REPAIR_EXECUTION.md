# 商品爬取修复执行文档

> 项目：CyberFlow
>
> 文档状态：第一至三阶段代码已实施；本地验证与部署边界见各阶段记录，生产 MySQL 8 和容器仍待验收
>
> 编制日期：2026-08-27
>
> 适用范围：`crawler-consumer`、`crawler-service`、`scraped_data.ecommerce_products` 及商品采集任务状态
>
> 目标：修复“实际未抓取却显示成功”“已抓取但未入库”“跨站商品互相覆盖”“币种与过滤误判”等问题。

## 第一阶段实施记录

本节描述当前代码，后续章节中未勾选的项目仍是后续计划，不表示已经实现。

- 已实现 Sitemap/API/商品页关键失败、解析失败、Pipeline 启动/写入/关闭失败进入 `FAILED`；部分失败也不会伪装成完整成功。
- 新增 `ecommerce_spider/crawl_result.py`，输出提交后进度和关闭后结果；消费者移除自然语言日志数量兜底。
- `rows_affected = persisted = inserted + updated + unchanged`。它表示本轮已提交的商品写入数，不是新增商品数；同一商品后续采集未变化时计入 `unchanged`。
- 每条 Upsert 使用数据库返回的 1/2/0 分别识别新增/更新/未变化，整批只提交一次，commit 成功后才发布统计。关闭 `CLIENT_FOUND_ROWS`，避免把未变化误记为新增。
- 保留现有质量规则和二级分类 48 条阈值；取消整场 JSONL 暂存，改为分类缓冲及持续提交。低于 48 条的分类仍需等关闭时回退到父分类。
- 新增 `20260827_fix_product_site_identity.sql`：支持旧索引名 `sku`/`uk_sku`，改为 `(source_domain, sku)` 唯一索引；`dedupe_key` 改为普通索引。迁移冲突时停止，不删除商品。
- 更新 `backfill_product_dedupe.py`：只回填查询指纹，不再合并/删除不同 SKU，也不会重新创建旧唯一索引。
- 失败任务保留最后收到的已提交统计。强制退出发生在数据库提交和进度报告之间时，报告可能偏少；没有收到确认的事务不推测为成功，需按失败日志和数据库核查。
- `failed` 包含最终关键请求/解析失败及数据库批次失败的商品数量；具体原因在 `failed_reasons` 中，不能直接与抓取数相加当作商品总数。
- 任务进度摘要显示抓取、生成、过滤、新增、更新、未变化、已提交和失败。完整分类计数写入日志和 RabbitMQ `metrics` 字段。
- 第一阶段未涉及的币种/过滤与访问回退见第二阶段；独立平台、Shopify 变体、Redis 可选和暂停计时见第三阶段。

验证命令：

```powershell
$env:PYTHONIOENCODING = 'utf-8'
$env:PYTHONDONTWRITEBYTECODE = '1'
.\.venv\Scripts\python.exe -m unittest discover -s tests -p 'test_product_crawl*.py' -v
```

数据库用例默认跳过。仅对隔离测试实例设置 `CYBERFLOW_TEST_MYSQL_PORT` 后启用；可用 `CYBERFLOW_TEST_MYSQL_USER`/`CYBERFLOW_TEST_MYSQL_PASSWORD` 指定测试账户。用例创建并清理随机命名的 `cyberflow_phase1_test_*` 数据库。

本地验证覆盖真实 Scrapy 生命周期/消费者协议及隔离 MariaDB 10.4 的 SQL、并发 Upsert、迁移重入、冲突阻断和事务回滚。当前机器没有 Docker，尚未在生产使用的 MySQL 8 镜像中验证；上线前必须再运行这些数据库用例。

第一阶段本地回归结果：31 项测试通过，其中包含 7 项真实数据库测试及 11 种真实 Scrapy 子进程场景。没有访问目标商城或修改现有业务数据库；隔离数据库只用于本次验证。

发布第一阶段时先暂停商品调度并等正在运行的任务结束，再停止消费者、备份商品表。构建新镜像后运行一次性迁移，确认成功才重启消费者：

```powershell
docker compose stop crawler-consumer
docker compose build crawler-consumer
docker compose run --rm db-migrate
docker compose up -d crawler-consumer
docker compose logs --tail=200 crawler-consumer
```

若部署使用 `.env.docker` 而不是默认 `.env`，所有 Compose 命令统一加 `--env-file .env.docker`。迁移需要 ALTER/INDEX/CREATE ROUTINE 权限，可能持有元数据锁，必须在维护窗口执行。新 Pipeline 会拒绝旧唯一索引结构，不能跳过迁移直接启动。

## 第二阶段实施记录（2026-08-27）

### 已实现

- 数据源站点页新增“本次采集选项”，单个运行和批量运行均可使用。参数经管理接口、RabbitMQ、消费者传给 Shopify/BigCommerce/通用 Spider，不修改站点配置。
- 支持 `max_product_price_usd`（默认 `null`，不限制）、`require_description`（默认 `false`）、`require_image`（默认 `true`）、`currency`（默认空，仅作为缺失币种时的后备值）。服务端和爬虫端均验证参数；显式 `false`/`null` 不会被默认值覆盖。
- 也可在已绑定模板的 `extra_selectors.crawl_options` 中配置默认选项，任务消息中的同名键优先。界面会传递完整选项，因此按界面本次值执行。
- 币种顺序为：与选中价格配对的商品数据 → BCData 店铺币种 → 页面明确币种 → 商品元数据 → 本次任务后备币种 → 域名配置 → 显式绑定模板币种。未绑定站点模板时，不再把系统 WooCommerce 模板的 USD 当成站点币种。
- 覆盖 BigCommerce `main[data-currency-code]`、价格 meta、active currency、BCData 价格分支及 JSON-LD Offer。币种必须取自实际选中的价格分支，避免不同 Offer/税价之间串配。
- 不再把 `$` 或 `.com.au` 后缀直接推断为某种货币。缺失币种或汇率时计入 `unknown_currency`，任务失败，已提交记录保留；不按 1:1 默认 USD 入库。
- 价格由 Pipeline 统一换算一次，再执行美元上限。描述、图片要求不再由 Spider 提前硬编码拒绝。BigCommerce HTML 价格范围收紧，防止把评论数或推荐商品的价格当成当前商品价格。
- BigCommerce Sitemap 请求失败、无可识别商品或空地图时，回退至同站首页、商品卡片、分类导航与分页；本轮没有接入需要凭据的 Storefront API。
- 导航仅访问同站（允许 apex/www），排除购物车、账户等非商品入口，默认最多 100 个导航页、5 层深度。达到发现上限或关键回退请求失败时标记 `FAILED`，不把截断目录冒充完整成功。回退完成时可正常成功，原 Sitemap 失败次数仍保留在 `requests_failed`。
- 新请求中间件支持 HTTP(S) 代理；BigCommerce 对 403/429/503、常见连接异常、200 验证页尝试一次 requests 回退。回退关闭叠加重试，最多 6 次同站请求/重定向，总时间预算 60 秒、响应体上限 20 MiB。
- 识别带验证标题和挑战标记的 HTTP 200 页面。仍是验证页时转为可识别的失败响应，不解析为商品；不会自动解验证码。
- Scrapy 和 requests 均校验证书，默认使用同一 certifi 信任库，支持自定义 PEM CA；不会读取未配置的系统代理，也不会在回退日志中打印代理密码。
- `check_runtime.py` 输出代码指纹、修订号及两项中间件是否启用。消费者镜像构建时自检，任务启动日志也输出运行指纹。

### 配置和用法

接口 `POST /admin/crawler/site-config/{id}/crawl` 的请求体示例（路径以现有控制器映射为准）：

```json
{
  "user_id": 1,
  "crawl_options": {
    "max_product_price_usd": 130,
    "require_description": false,
    "require_image": true,
    "currency": ""
  }
}
```

需要恢复此前严格过滤时，设置价格上限 130、描述和图片要求均为 `true`。禁用价格上限使用 `null`，不是 0。当前仍不接受零价商品。

环境配置示例（代理地址请使用自己的授权配置，勿提交真实凭据）：

```dotenv
PRODUCT_CRAWL_PROXY_URL=
PRODUCT_DOMAIN_CURRENCIES={"example.com.au":"AUD","example.com":"USD"}
PRODUCT_DISCOVERY_MAX_PAGES=100
PRODUCT_CRAWL_CA_BUNDLE=
```

自定义 CA 路径必须在容器内存在；如有企业 CA，另加只读文件挂载后再设置该路径。默认不要配置代理或私有 CA。`VERIFY_SSL` 不控制这些商品请求，不提供关闭商品 HTTPS 校验的开关。

### 本地验证结果与限制

- Python 回归：59 项通过，含 7 项隔离 MariaDB 10.4 数据库测试、19 种真实 Scrapy 子进程场景。没有连接现有业务库，测试随机数据库运行后已清理。
- 后端：JDK 17 下 `mvn package` 通过，新增 4 项参数验证/消息传递测试全部通过，无需启动 Spring 或连接数据库。
- 前端：按原锁文件安装，`npm run build` 通过；现有依赖审计报 8 项告警（2 moderate、6 high），未做自动升级。构建存在大 chunk 提示，不影响编译通过。
- 目标站点只读验证：从 `lyalealesstores.com.au` 首页发现商品，并从实际商品数据解析到原价 `46.95 AUD`。同一商品另通过真实 Scrapy 下载器和新中间件复测成功，禁用入库和任务发布。无有效价格的 LDV 页面被明确拒绝，没有把其他文字中的 `3` 当作价格。
- 本机 Scrapy 最初因平台默认证书库校验失败；统一为 certifi 后，在保持 TLS 校验的情况下通过复测。
- **未完成部署验收**：本机无 Docker，不能证明服务端镜像已经重新构建，也没有运行生产 MySQL 8、完整目录采集或真实授权代理的端到端测试。只读样本成功不等于整个站点任务成功。
- 汇率仍使用仓库内静态 `exchange_rates.json`，不是实时汇率；数据库仍保存 USD 金额，未增加来源金额/币种列。修改换算来源需另行确认。
- robots 声明地图、任意匿名分片发现仍待补充；本节为第二阶段历史记录，新增平台、变体、Redis 和暂停改造见第三阶段。

只读诊断（不触发数据库 Pipeline，最多 5 个公开页面请求）：

```powershell
.\.venv\Scripts\python.exe script/probe_product_store.py https://example.com
# 已知商品页也可指定 --scrapy-product-url https://example.com/product/one/
```

### 部署与版本验收（必须在有 Docker 的目标环境执行）

先暂停商品调度、等待在途任务结束并备份；第一阶段迁移未执行时，必须先执行上一节的迁移步骤。第二阶段不新增表结构迁移。

```powershell
docker compose --env-file .env.docker stop crawler-consumer
docker compose --env-file .env.docker build --no-cache crawler-consumer backend-admin
docker compose --env-file .env.docker up -d --force-recreate backend-admin crawler-consumer
docker compose --env-file .env.docker exec -w /app/scrapy_app crawler-consumer python -m ecommerce_spider.check_runtime
docker compose --env-file .env.docker logs --tail=200 crawler-consumer
```

前端不在当前 Compose 中，需另行构建并发布 `frontend/dist`。在目标站点发起小范围验收任务前，检查：

1. 当前发布自检 `revision` 为 `product-crawl-phase3-v1`（第二阶段历史版本为 `product-crawl-phase2-v1`），`protection_ready` 为 `true`，两个中间件均列出。
2. 容器指纹与同一份发布源码本地运行 `python -m ecommerce_spider.check_runtime` 的指纹一致（本地需将 Scrapy 项目目录加入 `PYTHONPATH`）。
3. 新任务日志开头的 `Product crawler runtime` 指纹与容器自检一致。
4. 界面改变过滤要求后，任务命令中的 `crawl_options_json` 与选择一致；币种分类统计和已提交数符合预期。
5. 真实目标任务结束后，核对 `FAILED/SUCCESS`、分原因计数和 MySQL 提交记录，再恢复批量调度。

重建失败或任一版本检查不一致时，不宣称第二阶段已经上线。回滚优先回滚应用镜像；不要恢复全局 SKU 唯一索引或删除商品以迎合旧逻辑。

## 第三阶段实施记录（2026-08-27）

### 平台入口与支持边界

前端平台列表、后端校验和消费者分派统一为 Shopify、WooCommerce、BigCommerce、Magento、Wix、Ecwid、Shopline。已有不支持的平台记录不会删除，但不再静默套用 WooCommerce 规则。

| 平台 | 独立入口 | 本轮实现与边界 |
|---|---|---|
| Magento | `magento_crawl` | Magento 2 的公开 `/graphql` 商品分页；入口不可用时回退 Sitemap/同站导航及商品 JSON-LD、Magento 选择器。不等于适配 Adobe Commerce SaaS Catalog Service。 |
| Wix | `wix_crawl` | 普通/商品 Sitemap、`/product-page/`、商品 JSON-LD 和 Wix data-hook 选择器。没有浏览器渲染器；仅客户端生成且无公开商品证据的页面会失败。 |
| Ecwid | `ecwid_crawl` | SEO 商品页/Instant Site 公开页面；可选 store ID 和 public token，读取 profile 币种及 REST 商品分页。不接受 secret token，不访问订单或客户数据。 |
| Shopline | `shopline_crawl` | 商品 Sitemap/导航/JSON-LD；缺少价格时按 handle 请求同站 Ajax 商品接口。Ajax 金额按明确币种的最小单位换算；未知精度不猜测。 |

新平台均使用公共请求保护和统一 Pipeline，不伪造价格、币种或成功数量。API 后续分页出错、重复分页、已知数量不匹配和新导航/分页上限会进入 `FAILED`，保留之前已提交的批次。四个平台分别提供成功、空响应、入口受阻的 Scrapy fixture；Wix/Shopline 只有空 HTML/空 Sitemap 时，不能证明空目录，会失败；Magento/Ecwid 可依据 API 明确 `total=0` 确认空目录。

这些是代码与样本级兼容，不是对所有主题、区域版本、反爬配置的保证。Magento、Wix、Ecwid、Shopline 本轮仍按商品保存，全部变体拆分仅对 Shopify 实施。旧 Sitemap 200 分片/20,000 商品上限和 robots 声明地图仍需后续统一加固。

### 可选平台配置

推荐在不提交版本库的 `.env.docker` 配置，域名键需与任务主机名一致（含实际使用的 www）；为空时无需凭据：

```dotenv
PRODUCT_PLATFORM_CONFIGS={"ecwid.example":{"store_id":"123456","public_token":"public_REPLACE"},"shopify.example":{"storefront_token":"REPLACE_WITH_AUTHORIZED_STOREFRONT_TOKEN"}}
PRODUCT_DOMAIN_CURRENCIES={"shopline.example":"AUD"}
PRODUCT_REDIS_ENABLED=false
```

以上都是占位符，必须替换为站点授权配置。也可用已绑定模板的 `extra_selectors.platform_config` 设置同名字段，模板值优先；包含凭据时应优先使用本地环境配置。消费者打印执行命令时会遮蔽 `config_json`，但模板数据/环境变量仍需按凭据保护，不上传截图或日志。平台配置和任务过滤选项相互独立。

Ecwid 跨域 API 白名单仅放行 HTTPS `app.ecwid.com/api/v3/{store_id}/profile` 和 `/products`，凭据放在请求头，禁止自动跨域重定向。Shopify token 仅用于目标站点的 Storefront API，不能填 Admin API token。

### Shopify 变体及历史数据升级

- 每个返回的变体保存一行，采用 `SHOPIFY-{完整商品ID}-{完整变体ID}`，仍由 `(source_domain, sku)` 保证唯一。分类修改、变体重排或原站重复 SKU 不再导致合并/覆盖。
- 分别保存变体价格、对应图片和已选属性；原站 SKU 写入 `cf_opingts` 的 `Source SKU`，不丢弃来源编码。任务商品数量现在按变体行数计算。
- 无 token 时展开公开商品 JSON 中的全部变体；单商品达到 250 个返回变体或明确总数大于返回数时报告 `variant_limit`，不能宣称全量成功。配置授权 Storefront token 后，对每个商品使用 100 条一页的游标分页；重复游标、接口失败或达到 20 页保护上限都明确失败。
- **历史聚合行不会自动迁移或删除。** 旧分类前缀/短哈希 SKU 与新变体 SKU 不相同，重采后会共存，直接一起导出会重复。上线前须暂停任务并备份，按站点核对旧记录和新变体结果，再经业务确认选择历史保留/归档/定向清理；本轮不提供按 SKU 前缀批量删除，避免误删其他商品。
- 已知回滚边界：回滚为旧 Spider 后又会写聚合 SKU，不会自动合并新变体行。不得恢复全局 SKU 唯一索引来“修复”此差异。

### Redis 与暂停

- Redis 服务可不启动。设置 `PRODUCT_REDIS_ENABLED=false` 后 Pipeline 不建立 Redis 连接；默认开启时，连接/PING/提交后缓存异常只告警，后续批次停用缓存。数据库 commit 是唯一真值。
- 管理后台删除/清理商品时 Redis 不可用不再中断 MySQL 操作；连接与命令设置 2 秒超时，批量清理首次缓存失败后不反复请求。Python `redis` 库仍是安装依赖，不要求 Redis 服务可用。
- Compose 已移除消费者和后端对 Redis healthy 的启动依赖。若不需要 Redis，显式启动 `mysql rabbitmq db-migrate backend-admin crawler-consumer`；不带服务列表的 `up -d` 仍会启动定义中的 Redis 服务。
- 消费者仅保留一个活动时间预算。轮询发现 `PAUSED` 后同时悬挂自有 Scrapy 子进程并冻结剩余时间；恢复时补回暂停时长。控制状态约每秒检查，数据库延迟仍会影响实际响应时间。
- 暂停中取消/删除/超时清理会先恢复进程，再 terminate，10 秒仍未退出才 kill。Windows terminate 不保证提交未缓冲完的批次；已经提交的记录不删除。Linux 容器的 SIGTERM/flush 行为仍需部署验收。

### 验证和发布

本地最终回归：81 项 Python 测试全部通过，其中包含 8 项隔离 MariaDB 10.4 数据库测试和 31 种真实 Scrapy 子进程样本场景。新增数据库测试确认相同原站 SKU 的 Shopify 变体独立入库、重复采集未变化、单变体价格更新，并全程关闭 Redis。Windows 真实子进程验证暂停超过预算后恢复、暂停中取消、关闭 stdout 后仍受超时控制。首次数据库测试因隔离实例启动失败未通过；关闭该实例的原生异步 I/O 和启动缓存加载后，完整 81 项重新运行通过，未修改应用数据库设置。

后端 JDK 17 全量 `mvn clean package` 通过，6 项测试通过，前端 `npm run build` 通过（仍有大 chunk 提示）。运行器实际加载验证七个平台入口均可用，运行自检为 `product-crawl-phase3-v1`、`protection_ready=true`。本机没有 Docker；未连接业务数据库，也未持有新增平台的真实授权 API 配置，不宣称目标站点全量采集或容器已部署。

第三阶段没有新增 SQL 迁移，但第一阶段身份索引迁移必须已完成。发布前先解决上述 Shopify 历史记录处置，再按第二阶段部署步骤一起发布前端、后端、消费者。自检必须为 `product-crawl-phase3-v1`，`engines` 列出七个平台，`protection_ready=true`，并与发布源码指纹相同。部署验收至少增加：

1. 每个新增平台各选授权实际站点，小范围核对商品名、来源金额/币种、图片、数据库 USD 金额和计数。
2. 多变体 Shopify 核对数量、各自价格及重复采集的更新/未变化；高变体商品用 Storefront 验证翻页到底。
3. 不启动 Redis 仍能写入商品，后台按选中商品清理不因缓存异常回滚。
4. 暂停时间长于剩余预算，恢复后继续；暂停中取消可结束进程，Linux 下再核对已提交批次保留。

本轮适配依据（平台接口仍受店铺权限、版本和配置限制）：[Adobe products query](https://developer.adobe.com/commerce/webapi/graphql/schema/products/queries/products)、[Wix 商品结构化数据](https://www.wix.com/seo/features)、[Ecwid public token 范围](https://docs.ecwid.com/develop-apps/app-settings)、[Ecwid 商品分页](https://docs.ecwid.com/api-reference/rest-api/products/search-products)、[Shopline Ajax 商品及最小货币单位](https://developer.shopline.com/docs/ajax-api/product/query-product/?version=v20250601)、[Shopify Ajax 变体限制](https://shopify.dev/docs/api/ajax)、[Shopify Storefront product](https://shopify.dev/docs/api/storefront/2026-07/queries/product)。

## 1. 执行结论

本次修复必须按以下顺序实施：

1. 建立结构化爬取结果协议，先保证任务状态、计数和错误原因可信。
2. 修改商品唯一身份和入库策略，避免跨站覆盖与任务结束前数据全部滞留。
3. 修复币种解析和质量过滤，使“抓取失败”与“业务过滤”能够区分。
4. 加固 BigCommerce 访问回退和 Sitemap 发现逻辑。
5. 收敛平台支持范围，再逐个平台补齐专用实现。

不得先通过继续增加超时时间、增加 CSS 选择器或把异常改成 warning 来规避问题。这些操作不能解决状态误判和数据丢失。

## 2. 当前基线与修复目标

### 2.1 实施前基线

- 当前代码基线：`f2dfaf1 fix: repair migrations and BigCommerce crawling`。
- 商品消费者镜像在构建时复制 Scrapy 项目，不是宿主机源码实时挂载；每次爬虫代码变更后必须重新构建 `crawler-consumer`。
- 数据库迁移由 `db-migrate` 按 `script/migrations/*.sql` 文件名顺序执行，记录在 `cyberflow.schema_migrations`。
- 当前 `ecommerce_products` 使用全局唯一索引 `uk_sku (sku)`。
- 当前 Pipeline 先将整场商品写入临时 JSONL，Spider 关闭时才统一写入 MySQL。
- 当前 Consumer 通过扫描自然语言日志估算商品数，并主要依赖 Scrapy 退出码判断成功。

### 2.2 完成后的行为

修复完成后必须满足：

- 所有 Sitemap/入口请求失败时，商品任务状态为 `FAILED`，不能是 `SUCCESS, 0`。
- 只有爬虫明确输出完整的结构化结果，Consumer 才能把任务标记为成功。
- `rows_affected` 表示本次已提交到 MySQL 的商品数，不再表示页面解析数。
- 同一站点、同一 SKU 重复采集时更新原记录；不同站点出现相同 SKU 时保留两条独立记录。
- Redis 不可用时，MySQL 商品入库仍能继续。
- 爬虫异常退出时，已经提交的批次仍保留，最多损失当前未提交批次和未达到分类阈值的有界缓冲。
- 任务日志能分别展示发现、解析、过滤、持久化和失败数量。

## 3. 结果协议设计

### 3.1 新增结构化结果标记

新增 Scrapy Extension，例如：

```text
crawler-service/app/crawler/ecommerce_spider/ecommerce_spider/crawl_result.py
```

Extension 监听以下信号：

- `spider_opened`
- `item_scraped`
- `item_dropped`
- `spider_error`
- `spider_closed`

Spider 和 Pipeline 通过 Scrapy Stats 写入统计值，关闭时输出且只输出一条机器可解析的结果：

```text
CYBERFLOW_CRAWL_RESULT={"version":1,"outcome":"success","close_reason":"finished","discovered":120,"fetched":120,"generated":120,"filtered":10,"accepted":110,"inserted":100,"updated":5,"unchanged":5,"persisted":110,"failed":0,"requests_failed":0,"filtered_reasons":{"price_limit":10},"failed_reasons":{},"empty_reason":null,"errors":[]}
```

字段约定：

| 字段 | 含义 |
|---|---|
| `version` | 协议版本，首版固定为 `1` |
| `outcome` | `success` 或 `failed` |
| `close_reason` | Scrapy 的关闭原因 |
| `discovered` | 发现的候选商品 URL/商品对象数 |
| `generated` | Spider 成功生成的 Item 数 |
| `fetched` | 已收到的商品详情页或 API 商品对象数 |
| `filtered` | Spider/Pipeline 按规则丢弃的商品数 |
| `accepted` | 通过 Pipeline 验证并接受写库的 Item 数 |
| `inserted` / `updated` / `unchanged` | 已提交的新增 / 实际更新 / 未变化商品数 |
| `persisted` | 已成功提交到 MySQL 的商品数 |
| `requests_failed` | 重试结束后失败的请求数；入口候选失败可被后续入口恢复 |
| `failed` / `failed_reasons` | 影响完整性的失败数量及分原因统计 |
| `empty_reason` | 合法空结果原因；非空结果时为 `null` |
| `errors` | 最多保留前 10 个结构化错误摘要，不写敏感信息 |

### 3.2 成功与失败判定

Consumer 必须同时满足以下条件才标记 `SUCCESS`：

1. Scrapy 退出码为 `0`；
2. 捕获到且只能捕获到一个合法的 `CYBERFLOW_CRAWL_RESULT`；
3. 结果协议版本受支持；
4. `outcome == "success"`；
5. 如果 `persisted == 0`，必须存在明确的 `empty_reason`，例如：
   - `confirmed_empty_catalog`
   - `all_items_filtered`

以下情况必须为 `FAILED`：

- 所有 Sitemap/API/入口请求失败；
- 发现流程被 403、429、503、验证码页或网络异常阻断；
- 结果标记缺失、重复或 JSON 非法；
- Pipeline 任何 MySQL 批次提交失败；
- Spider 出现未处理异常；
- 进程被超时或操作员强制终止；
- `generated > 0`、`persisted == 0` 且没有明确过滤原因。

### 3.3 Consumer 修改

修改：

```text
crawler-consumer/consumers/product_consumer.py
```

执行内容：

- 删除通过“成功生成商品”和“批量入库成功”自然语言日志估算最终数量的逻辑。
- 日志仍完整保存，但任务状态只信任结构化结果。
- `rows_affected` 设置为 `persisted`。
- 在任务日志末尾追加用户可读摘要：

```text
发现 120，解析 118，过滤 8，成功入库 110，请求失败 0
```

- 将 `generated`、`filtered`、`persisted` 纳入进度说明；没有实时数值时只显示运行时长，不能伪造百分比。
- 第一版保留现有 `rows_affected` 字段，不要求立即修改后端任务表；详细统计先进入完整日志和结果消息。

## 4. 数据库身份与 Upsert 修复

### 4.1 唯一身份规则

商品的数据库逻辑身份统一为：

```text
(source_domain, sku)
```

规则：

- 第一阶段保留 Spider 提供的主机名，仅统一去除首尾空格和转小写。
- 第一阶段不合并 `www.`/apex、不移除端口；canonical 域名归一化留待第二阶段，以免未经确认合并站点。
- SKU 必须去除首尾空白，但保留原始大小写；如业务确认 SKU 不区分大小写，则依赖表字段统一 collation，不能在各 Spider 中各自处理。
- Item 缺少 `source_domain` 或 SKU 时必须丢弃并计入结构化错误，不能进入数据库。

`dedupe_key` 只作为重复候选查询索引，不再作为数据库唯一约束。原因是同一站点可能有名称和图片相同但 SKU 不同的合法变体；继续保持唯一会把不同 SKU 静默合并。

### 4.2 新增迁移

新增：

```text
script/migrations/20260827_fix_product_site_identity.sql
```

迁移步骤：

1. 检查 `source_domain IS NULL OR TRIM(source_domain) = ''` 的历史记录数量。
2. 检查规范化后的 `(source_domain, sku)` 是否冲突，有冲突直接 SIGNAL 终止。
3. 先添加 `UNIQUE KEY uk_product_domain_sku (source_domain, sku)`，保持身份约束。
4. 按实际索引定义删除旧单列 SKU 唯一索引（兼容 `sku`/`uk_sku`）和唯一 `dedupe_key`。
5. 历史空域名标记为 `legacy-unknown`，域名转小写/去首尾空格，SKU 去首尾空格。
6. 保留原字符集与 collation，将 `source_domain` 改为 `NOT NULL`。
7. 添加普通索引 `idx_product_dedupe (dedupe_key)`；应用层拒绝空域名。

迁移必须使用 `information_schema` 判断索引/字段是否存在，保持脚本可重入风格与现有迁移一致。

迁移前检查 SQL：

```sql
SELECT COUNT(*) AS missing_domain
FROM scraped_data.ecommerce_products
WHERE source_domain IS NULL OR TRIM(source_domain) = '';

SELECT LOWER(COALESCE(NULLIF(TRIM(source_domain), ''), 'legacy-unknown')) AS normalized_domain,
       TRIM(sku) AS normalized_sku,
       COUNT(*) AS duplicate_count
FROM scraped_data.ecommerce_products
GROUP BY normalized_domain, normalized_sku
HAVING COUNT(*) > 1;
```

第二条查询返回记录时暂停迁移，先导出冲突记录，由业务确认保留或合并规则，不能直接删除。

### 4.3 同步初始化 Schema

修改：

```text
backend-admin/src/main/resources/schema.sql
```

使新安装环境直接包含：

```sql
source_domain VARCHAR(255) NOT NULL,
UNIQUE KEY uk_product_domain_sku (source_domain, sku),
INDEX idx_product_dedupe (dedupe_key)
```

同时移除 `uk_sku` 和唯一的 `uk_product_dedupe`，保证初始化 Schema 与迁移后结构一致。

### 4.4 Upsert 修改

修改：

```text
crawler-service/app/crawler/ecommerce_spider/ecommerce_spider/pipelines.py
```

要求：

- INSERT 必须总是携带规范化后的 `source_domain`。
- `ON DUPLICATE KEY UPDATE` 只允许由 `(source_domain, sku)` 冲突触发。
- 更新字段包括商品业务字段和 `dedupe_key`，但不能把一条记录改成另一个域名或 SKU。
- 每批提交成功后增加 `persisted` Stats；失败必须回滚并将整个爬取结果设为失败。
- 第一阶段在一个事务内逐条执行 Upsert，使用每条语句的 `rowcount` 1/2/0 识别新增/更新/未变化，最后整批提交；不预查询是否存在，避免并发分类竞态。
- 不使用 `executemany` 的汇总 rowcount 作为商品数，也不启用 `CLIENT_FOUND_ROWS`。每批 commit 后才把统计加入累计值；吞吐优化不能改变该语义。

## 5. 持续入库与异常退出修复

### 5.1 移除整场 JSONL 暂存

当前使用整场临时 JSONL 是为了在爬取结束后计算“二级分类至少 48 条”的规则。修复后改为有界分类缓冲：

- 一级分类商品直接进入常规 `items_buffer`。
- 对每个二级分类，最多暂存前 48 条。
- 某二级分类达到 48 条时，立即把该分类暂存的 48 条加入 MySQL 批次，后续商品直接入批次。
- Spider 正常关闭时，仍不足 48 条的分类回退为父分类，然后写入 MySQL。
- `items_buffer` 达到 `DB_BATCH_SIZE` 时立即提交。

这样可以保留现有业务规则，同时把内存和未提交数据控制在：

```text
当前 MySQL 批次 + 每个未达标二级分类最多 47 条
```

删除以下对象和流程：

- `staging_path`
- `staging_file`
- `_flush_staged_items()`
- 临时 JSONL 的创建、二次读取和删除

### 5.2 终止流程（第三阶段已实现，Linux 关闭行为待部署验收）

修改 `product_consumer.py` 的超时与取消流程：

1. 如正在暂停，先恢复子进程，再调用 `proc.terminate()`；Linux 下可触发 Scrapy 关闭，Windows terminate 不保证执行 flush。
2. 最多等待 10 秒。
3. 仍未退出时再调用 `proc.kill()`。
4. 强制 kill 后任务必须为 `FAILED`，日志注明可能存在未提交批次。
5. 已经提交的 MySQL 批次不得回滚或删除。

Docker 生产环境是 Linux，需验证 SIGTERM 能触发 Scrapy 的 `spider_closed`。本地 Windows 测试需使用平台判断，不能假设 `SIGSTOP/SIGCONT` 始终可用。

### 5.3 超时与暂停（第三阶段已实现）

只保留一个超时所有者，由 `_exec_scrapy()` 的 `ActiveDeadline` 管理活动运行时间：

- 移除外层进度循环中的第二套绝对 deadline。
- 进入 `PAUSED` 时记录 `paused_at`。
- 恢复时将暂停时长加回 deadline，或直接累计 active elapsed。
- 暂停期间不增加运行超时。
- 进度基于结构化计数；没有计数时保持当前进度，不再每 3 秒机械增加到 94%。

## 6. Redis 降级

修改 `pipelines.py`：

- MySQL 连接失败：Spider 启动失败。
- Redis 连接或 `PING` 失败：记录 warning，设置 `self.r = None`，继续爬取。
- MySQL commit 成功后，仅当 Redis 可用时更新观察性指纹。
- `delete`、`scard`、pipeline execute 全部增加 Redis 可用性判断和异常保护。
- Redis 失败不得覆盖 MySQL 已成功提交的结果。
- 更新类注释，明确 Redis 是观察性缓存，不是商品真值来源或写库前置去重来源。

第三阶段已移除消费者和管理后端对 Redis healthy 的强启动依赖。Redis 保留为可选服务，Python 库依赖仍需安装。

## 7. 币种与质量过滤修复

### 7.1 币种解析优先级

统一币种解析顺序：

1. 与实际选中价格配对的 API/BCData/JSON-LD 币种；
2. BCData 店铺配置与页面明确币种；
3. 商品元数据中的币种；
4. 任务后备币种、域名配置、显式绑定模板，按顺序回退。系统通用模板的 USD 不作为证据。

禁止把无法确定的币种静默默认为 USD。无法确定时：

- 商品计入 `dropped_unknown_currency`；
- 当前实现遇到未知币种即设为 `FAILED`，提示检查配置；部分商品已经提交时保留统计；
- 日志记录解析来源，但不得记录凭据或完整敏感配置。

建议新增以下可选字段，保留来源价格审计能力：

```sql
source_price    DECIMAL(12, 2) NULL,
source_currency CHAR(3) NULL
```

`regular_price` 继续表示换算后的 USD 价格。字段通过新的增量迁移添加，同时同步 `schema.sql`。如果本轮不增加字段，至少必须在任务日志中保留原始金额和币种统计。

### 7.2 过滤规则配置化

将以下硬编码改为任务或站点配置：

- `max_product_price_usd`：默认 `null`，表示不按价格上限过滤；
- `require_description`：默认 `false`；
- `require_image`：默认 `true`，可覆盖；
- `allow_zero_price`：后续可选项，本轮不提供，零价仍不入库。

Spider 负责解析，Pipeline 负责验证和持久化，业务过滤必须产生分原因计数：

```text
dropped_missing_title
dropped_invalid_price
dropped_price_limit
dropped_missing_description
dropped_missing_image
dropped_unknown_currency
dropped_duplicate_in_task
```

“抓取不到”与“抓到后按配置过滤”必须在任务摘要中分别展示。

## 8. BigCommerce 与 Sitemap 加固

### 8.1 访问回退中间件

修改：

```text
crawler-service/app/crawler/ecommerce_spider/ecommerce_spider/middlewares.py
```

执行内容：

- HTTPS 证书校验默认开启，移除硬编码 `verify=False`。
- 支持自定义 CA；本轮不提供关闭校验的开关。
- 对 403、429、503 和连接异常执行有上限的回退。
- 识别 HTTP 200 的常见 WAF/验证码页面，不能当成正常商品页解析。
- 为回退请求复用连接池，并设置连接、读取总超时。
- 支持从安全配置读取代理；日志只输出代理标识，不输出账号密码。
- Scrapy 请求和回退请求共享一致的 UA、Accept-Language 和必要 Cookie。
- 每个 URL 设置总尝试上限，避免 Scrapy 与回退中间件互相重试形成请求放大。

### 8.2 Sitemap 发现

修改通用和 BigCommerce Spider：

- 支持 `robots.txt` 中声明的 Sitemap。
- 支持普通 XML Sitemap、Sitemap Index 和 `.xml.gz`。
- 不只按 URL 是否包含 `product` 判断分片；解析分片后结合 URL 模式和页面证据判断。
- 将 200 个 Sitemap 分片、20,000 个商品等上限改为显式配置，并在截断时记录 warning 和结构化统计。
- 域名校验使用规范化后的允许域名集合，允许 `www` 与 apex canonical 切换。
- 仍需限制在同一可注册域或站点显式允许的域名，防止 Sitemap 把爬虫引向任意外部地址。
- Sitemap 完全不可用时，BigCommerce 可按优先级回退至 Storefront 数据、分类导航或页面发现；如果没有可用回退，任务明确失败。

## 9. 平台支持范围治理

第三阶段前端、后端和消费者统一接受以下七个平台；新增平台的实际兼容范围见第三阶段表格：

- Shopify
- BigCommerce
- WooCommerce
- Magento
- Wix
- Ecwid
- Shopline

对于 OpenCart、PrestaShop、Squarespace 和 custom：

- 如果没有对应专用 Spider 或经过验证的 adapter，任务提交时返回“不支持或实验性支持”；
- 不得全部静默分发到 `selector_profile=woocommerce` 后再显示成功 0 条；
- 本轮均不开放商品采集；历史站点记录保留，提交不支持的平台任务时明确拒绝。以后开放 `custom` 必须先验证完整 selector 配置。

后续每增加一个平台，必须提供独立 fixture、至少一个成功样本、一个空目录样本和一个入口失败样本。

Shopify 已采用“一变体一行”，身份使用完整商品 ID 与变体 ID；不再受分类或原站重复 SKU 影响。旧版聚合行的升级注意事项见第三阶段。

## 10. 分阶段实施清单

### 阶段 A：状态与计数可信（必须先完成）

- [x] 新增 `crawl_result` Extension。
- [x] 为 Sitemap/API/HTTP/Pipeline 失败写入结构化 Stats。
- [x] Consumer 只接受结构化结果，不再扫描自然语言计数。
- [x] 空结果必须携带合法 `empty_reason`。
- [x] `rows_affected` 改为 `persisted`。
- [x] 增加结果协议单元测试和 Consumer 解析测试。

验收门槛：所有 Sitemap 返回 403 时，任务必须为 `FAILED`；正常空目录可明确为 `SUCCESS, 0`。

### 阶段 B：数据身份与持续入库

- [x] 新增并在隔离 MariaDB 演练商品唯一索引迁移；生产 MySQL 8 仍需验收。
- [x] 同步修改 `schema.sql` 与 `init_all_databases.sql`。
- [x] Upsert 改为 `(source_domain, sku)` 身份。
- [x] `dedupe_key` 改成普通索引，旧回填脚本不再删除商品。
- [x] 临时 JSONL 改为有界分类缓冲。
- [x] 每批 MySQL commit 后更新 `persisted`。
- [x] 超时/取消先恢复暂停进程，再 terminate，10 秒后必要时 kill；Linux flush 待部署验收。
- [x] 商品采集与后台商品清理中的 Redis 降级为可选缓存。
- [x] 暂停冻结活动 deadline，并悬挂本任务的 Scrapy 子进程。

验收门槛：不同域名相同 SKU 能保存两行；运行中强制终止后，已提交批次仍在数据库。

### 阶段 C：币种与过滤

- [x] 实现明确的币种来源优先级。
- [x] 禁止未知币种默认 USD。
- [x] 价格上限、描述、图片规则配置化。
- [x] 增加分原因 Drop Stats。
- [ ] 可选：新增来源金额与来源币种字段。

验收门槛：`150 AUD` 能按 AUD 换算，不能当成 `150 USD`；过滤 0 条与请求失败能被明确区分。

### 阶段 D：访问与发现加固

- [x] 重构 BigCommerce 请求回退。
- [x] 恢复 HTTPS 证书校验（生产镜像仍待部署验收）。
- [x] 增加 WAF 200 页面识别。
- [ ] 扩展 Sitemap/robots/gzip/canonical 支持（已实现 gzip、www/apex 和导航回退；robots 声明地图仍待补充）。
- [x] 支持受控代理配置（真实代理需部署后验收）。
- [ ] 将所有截断和回退情况写入结构化结果（已覆盖导航回退与导航上限；旧 Sitemap 全局上限仍待统一）。

验收门槛：Scrapy 403、回退成功时任务正常入库；Scrapy 和回退都失败时任务明确失败。

### 阶段 E：平台与变体

- [x] 收敛当前支持的平台列表为七种，其他平台明确拒绝。
- [x] Shopify 一变体一行及可选 Storefront 分页；历史聚合记录不自动删除。
- [x] Magento、Wix、Ecwid、Shopline 专用 adapter 与成功/空目录/失败 fixture。
- [x] 更新 `README.md` 与 `docs/PROJECT.md` 的支持范围和数据流说明。

## 11. 测试方案

### 11.1 单元测试

建议新增：

```text
crawler-service/tests/test_crawl_result.py
crawler-service/tests/test_pipeline_identity.py
crawler-service/tests/test_pipeline_filters.py
crawler-service/tests/test_currency_resolution.py
crawler-service/tests/test_sitemap_discovery.py
crawler-consumer/tests/test_product_consumer_result.py
crawler-consumer/tests/test_product_consumer_timeout.py
```

最低用例：

| 编号 | 场景 | 预期 |
|---|---|---|
| T01 | 三个 Sitemap 都返回 403 | `FAILED`，`persisted=0` |
| T02 | Scrapy 403，requests/代理回退成功 | `SUCCESS`，实际入库 N 条 |
| T03 | HTTP 200 但内容为验证码页 | `FAILED`，记录 WAF 原因 |
| T04 | 合法空商品目录 | `SUCCESS, 0`，存在 `confirmed_empty_catalog` |
| T05 | 页面生成 100 条，配置过滤 100 条 | `SUCCESS, 0`，存在 `all_items_filtered_by_config` 和分原因计数 |
| T06 | 结果标记缺失或格式错误 | `FAILED` |
| T07 | 同域名同 SKU 连续采集 | 数据库保持 1 行且字段更新 |
| T08 | 两个域名使用相同 SKU | 数据库存在 2 行，互不覆盖 |
| T09 | Redis 在启动前不可用 | MySQL 仍正常入库，任务成功并告警 |
| T10 | Redis 在 MySQL commit 后断开 | 已提交数据保留，任务不因 Redis 失败而失败 |
| T11 | 抓取超过一个批次后终止 | 已提交批次保留，任务 `FAILED` |
| T12 | 暂停时长超过配置超时 | 恢复后继续运行，不立即超时 |
| T13 | 150 AUD 商品 | 按 AUD 换算并按配置过滤 |
| T14 | 币种无法识别 | 不默认 USD，给出明确统计/失败原因 |
| T15 | `www` 与 apex 域名切换 | 合法商品 URL 不被误拒绝 |
| T16 | `.xml.gz` Sitemap | 能正确发现商品 URL |
| T17 | 同图同名但不同 SKU | 保存为两个商品，不被 `dedupe_key` 合并 |

### 11.2 集成测试

使用本地可控 HTTP fixture 服务模拟：

- Sitemap Index 和产品 Sitemap；
- 403 后成功；
- 429 + Retry-After；
- 503；
- HTTP 200 验证码页；
- 超时和断开连接；
- BigCommerce BCData、JSON-LD 和普通 HTML 三类页面；
- AUD、USD 和未知币种。

集成测试必须连接测试 MySQL 和 Redis，禁止连接生产库。测试结束后按测试 `source_domain` 精确清理，不能清空整张商品表。

### 11.3 线上候选站点验收

至少选择：

- 1 个现有 Shopify 站点；
- 1 个现有 WooCommerce 站点；
- 当前问题 BigCommerce 站点；
- 1 个明确返回 403 的测试站点；
- 1 个合法空目录 fixture。

每个站点核对：发现数、生成数、过滤数、入库数、数据库抽样内容、任务状态、任务日志摘要。

## 12. 发布步骤

### 12.1 发布前

1. 备份 `scraped_data.ecommerce_products` 表或创建可恢复快照。
2. 执行第 4.2 节迁移前检查 SQL，并保存结果。
3. 在测试数据库完整执行迁移和回归测试。
4. 确认工作区无意外修改，并记录待发布 commit。
5. 发布窗口内暂停新的商品任务，等待正在运行的任务结束。

### 12.2 构建与启动

从项目根目录执行：

```powershell
git pull --ff-only
docker compose build crawler-consumer
docker compose up -d db-migrate
docker compose up -d crawler-consumer
docker compose ps
docker compose logs --tail=200 db-migrate crawler-consumer
```

必须确认：

- `db-migrate` 显示新迁移执行成功；
- `crawler-consumer` 使用新构建镜像；
- Scrapy 启动日志包含新的结果 Extension 和 BigCommerce middleware；
- 启动日志没有 MySQL schema、collation 或 migration 错误。

### 12.3 灰度顺序

1. 先跑合法空目录 fixture，验证空结果协议。
2. 再跑一个小型 Shopify/WooCommerce 站点，验证持续入库和结果计数。
3. 再跑当前 BigCommerce 站点，验证 403 回退和币种。
4. 最后恢复其他站点定时任务。

不建议第一轮直接对全部站点全量重爬。

### 12.4 发布后查询

```sql
SELECT source_domain, sku, COUNT(*) AS cnt
FROM scraped_data.ecommerce_products
GROUP BY source_domain, sku
HAVING COUNT(*) > 1;

SELECT source_domain, COUNT(*) AS product_count,
       MIN(updated_at) AS first_updated,
       MAX(updated_at) AS last_updated
FROM scraped_data.ecommerce_products
GROUP BY source_domain
ORDER BY product_count DESC;
```

同时抽查任务：

- `SUCCESS` 任务是否都有结构化结果；
- `rows_affected` 是否等于 `persisted`；
- `FAILED` 是否包含可行动的失败原因；
- 是否出现连续 `SUCCESS, 0` 且没有合法 `empty_reason`。

## 13. 回滚方案

### 13.1 应用回滚

- 保留发布前镜像标签或 commit。
- 新 Consumer 出现严重问题时，停止接收新商品任务，恢复上一镜像。
- 数据库索引迁移优先保持前向兼容，不立即恢复全局 `uk_sku`。

原因：新版本运行后，不同域名可能已经写入相同 SKU。此时直接恢复 `UNIQUE (sku)` 会因合法数据冲突失败，或者迫使删除商品。

### 13.2 数据库回滚原则

- 新增来源币种字段属于加法迁移，可保留。
- `(source_domain, sku)` 唯一索引也可由旧应用继续使用，通常无需回退。
- 如果业务强制要求恢复全局 `uk_sku`，必须先执行：

```sql
SELECT sku, COUNT(*) AS cnt
FROM scraped_data.ecommerce_products
GROUP BY sku
HAVING COUNT(*) > 1;
```

存在结果时不得直接加回 `uk_sku`，必须先导出并由业务选择每个 SKU 的保留记录。

### 13.3 部分入库说明

持续入库后，失败任务可能已经提交部分商品。这是预期的数据保护行为：

- 任务状态仍为 `FAILED`；
- 日志显示 `persisted` 数量和失败位置；
- 重试依赖 `(source_domain, sku)` Upsert 安全刷新，不需要删除已提交商品；
- 禁止失败时按站点批量删除商品，否则会把历史有效数据一并删除。

## 14. 监控与告警

至少增加以下日志或指标：

- `crawl_discovered_total`
- `crawl_generated_total`
- `crawl_dropped_total{reason}`
- `crawl_persisted_total`
- `crawl_http_failure_total{status}`
- `crawl_fallback_total{result}`
- `crawl_duration_seconds`
- `crawl_result_missing_total`
- `redis_observability_failure_total`

建议告警条件：

- 任一任务缺少结构化结果；
- 同一站点连续 2 次入口发现失败；
- 同一站点连续 3 次 `persisted=0`；
- `generated > 0` 但 `persisted/generated < 20%`；
- 403/429/503 比率突增；
- MySQL 批次失败；
- 爬虫被强制 kill。

## 15. 完成定义（Definition of Done）

只有同时满足以下条件，本次修复才能关闭：

- [ ] T01-T17 全部通过。
- [ ] 当前 BigCommerce 目标站点完成一次真实采集，任务状态和数据库数量一致。
- [ ] 不同站点相同 SKU 的集成测试通过。
- [ ] Redis 断开测试通过，MySQL 数据不受影响。
- [ ] 超时和取消测试证明已提交批次不会丢失。
- [ ] 未知币种不会被静默按 USD 处理。
- [ ] `SUCCESS, 0` 只允许出现在有明确 `empty_reason` 的场景。
- [ ] 新迁移在空库、现有测试库和生产快照副本上均执行成功。
- [ ] `README.md`、`docs/PROJECT.md` 与代码中的平台支持范围、Redis角色和商品入库流程一致。
- [ ] 发布与回滚演练记录已保存。

## 16. 建议提交拆分

为降低审核和回滚风险，建议拆成以下提交：

1. `fix: add authoritative crawl result protocol`
2. `fix: scope product identity by source domain`
3. `fix: persist product batches during crawl`
4. `fix: make redis an optional observability cache`
5. `fix: resolve source currency and configurable filters`
6. `fix: harden bigcommerce fallback and sitemap discovery`
7. `test: cover product crawl failure and persistence scenarios`
8. `docs: align supported product crawler behavior`

每个提交都应包含对应测试；数据库迁移与使用新索引的应用代码必须在同一个发布批次中上线。
