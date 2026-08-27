# 商品质量、共享分类与收录导航（2026-08-27）

## 商品质量规则

- 所有爬虫经过同一 Pipeline：源价格先换算 USD，再处理高价，再应用用户选择的额外价格上限。
- USD 价格 `>150` 时，按 `120 + CRC32(lower(trim(source_domain)) + '|' + trim(sku)) % 3001 / 100` 调整到 120.00–150.00。相同站点/SKU 不随重复采集随机变化；150 及以下保持原价。
- `original_price_usd` 保留本次采集换汇后、调价前的价格。历史批量处理同时在 `scraped_data.product_policy_audit` 保留第一次修改前的价格、图片和可用性。
- 空图片、非 HTTP(S) 地址和明确的默认图片文件名被过滤，例如 ProductDefault、placeholder、no-image、default-product、logo、favicon 等。多图保留有效地址；有逗号的 CDN 地址不被错误截断。对 mcquillantools.ie 的 Hitachi_Spares_Logo 资产也有规则。
- 没有任何有效图片的新商品不入库；旧商品保留原记录并设 `image_usable=0`，列表、统计和导出共同排除。旧配置 `require_image=false` 不能绕过这条全局规则。
- **识别边界**：按 URL 规则识别，不下载所有图片、不检查远程 404、不做图像内容识别。无特征文件名的默认图需要补充明确规则，不能仅按重复次数判断。
- 规则启用前已经生成的导出压缩包不会被改写，请重新创建导出任务。

## 分类维护

- 独立菜单 `/categories`，后端 `/admin/custom-categories`；读取权限 `category:list` 或商品/数据源相关查看权限，写入权限 `category:manage`。
- 初始目录从现有分类生成 199 项，只初始化一次。删除的未使用分类不会在服务重启时重新出现。
- 支持两级、排序、启停和未使用分类的修改/删除。名称全局唯一；已被商品或数据源引用的分类禁止改名/删除，可停用。源站保留未改变的历史分类，不强制改写历史关联。
- 商品筛选与源站单选均使用 `CustomCategorySelect.vue` 和相同 API；停用分类仍可用于历史商品筛选，但不能分配给新的数据源。父级停用同时影响子分类。

## 收录和任务历史

- 独立菜单收录数据：站点明细 `/indexing/sites`，建站者汇总 `/indexing/builders`，服务器汇总 `/indexing/servers`。
- 汇总行可下钻站点，保留当前过滤条件，服务器按名称与 IP 精确匹配（包括未分配 IP）。旧 `/dashboard/indexing` 自动跳转站点明细。
- 商品任务结果只显示已入库数量；失败原因留在日志。失败且已有入库数据明确显示“失败 · 有数据入库”，不把部分提交算成任务成功。暂停/取消/失败不再显示“正在执行”。

## 部署与存量处理

1. 先备份 `cyberflow` 和 `scraped_data`，避免在运行中的采集任务期间重启消费者。
2. 运行迁移 `20260827_product_quality.sql`、`20260827_shared_categories_indexing.sql`，再更新后端和消费者镜像；新库初始化也包含共享目录与新增商品列。
3. 在具有 Scrapy/PyMySQL 依赖的环境运行 `script/apply_product_policy.py`，设置 `SCRAPED_DB_URL`、`PYTHONPATH` 指向爬虫包。默认仅预览；添加 `--apply` 才修改。
4. 脚本以固定 ID 上界按 500 条事务处理，使用行锁防止覆盖并发更新；可以重复执行。大库会产生审计数据，请预留存储空间并安排低峰执行。
5. 回滚应先停止写入并检查备份/审计，按需要恢复字段，不能盲目覆盖之后发生的合法更新。

本地执行：备份 `/tmp/cyberflow-before-product-policy-20260827.sql`（74 MB）；6,997 条，1,289 条调价、156 条标记不可用，最终可见 6,841 条。1,438 条记录被审计（部分商品同时调价与过滤）。

## 验证

- 前端 `npm test`、`npm run build`。
- 后端 `mvn -o test`。
- 爬虫 `python -m unittest discover -s tests -p 'test_product*.py'`；测试路径需包含爬虫包、consumer 和 consumer/scripts。
- 可选 `CYBERFLOW_PRODUCT_POLICY_DB_TEST=1` 验证存量脚本预览/审计/重复执行，使用连接私有临时表，不修改正式数据。
- 可选 `CYBERFLOW_PRODUCT_DB_TEST=1 mvn -o -Dtest=ProductQueryDatabaseTest test` 验证百万条临时数据的游标分页、范围限制和图片过滤后列表/统计/导出一致性。

- 可选 `CYBERFLOW_PRODUCT_DB_TEST=1 mvn -o -Dtest=SiteIndexingGroupingDatabaseTest test` 验证同名/未分配分组下钻与权限过滤；只写临时站点表，收录历史为只读。

## 生产分类乱码

若旧迁移由 latin1 客户端执行，见 [分类乱码诊断与恢复](CATEGORY_ENCODING_REPAIR.md)。新迁移会有条件地恢复已知乱码及关联字段，不会重新播种或清空分类。
