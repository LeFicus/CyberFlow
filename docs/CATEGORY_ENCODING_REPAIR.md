# 生产环境分类乱码修复

## 原因和适用范围

`20260827_shared_categories_indexing.sql` 原来缺少 `SET NAMES utf8mb4`，迁移执行器也没有指定客户端字符集。如果生产 MySQL 客户端使用 latin1，UTF-8 中文字节会被错误解码后存入 UTF-8 列，例如 `书籍` 变成 `ä¹¦ç±…`。仅修改连接配置不会自动恢复已经写坏的名称，初始化标记也会阻止重复播种。

此补丁：

- 迁移命令强制 `--default-character-set=utf8mb4`；分类初始化脚本显式设置 UTF-8；Spring SQL 初始化明确使用 UTF-8。
- 新增独立迁移 `20260827_utf8_category_repair.sql`，因此已部署过旧分类迁移的环境也能收到修复。
- 只识别原始 199 个分类和 6 个菜单的已知 latin1 错读结果，支持错读一次或两次。分类必须仍对应原始 ID，名称必须精确匹配原始名称或已知乱码；自定义改名、删除、新增、层级、排序、停用状态不受影响。
- 同步修复 `crawl_site_config.category` 和 `scraped_data.ecommerce_products.custom_category` 中的对应乱码，保持筛选与数据源关联。不会整体解码所有中文字段，也不修改价格或图片。
- 修改前的字段值写入 `cyberflow.category_encoding_repair_backup`，数据备份和更新在同一个事务内完成；唯一名称冲突会使事务失败，不能使用 mysql 的 `--force` 选项继续提交。重复执行正常情况下为零修改。

**限制：** 此修复不猜测 `???`、`�` 等已丢失字符的名称，也不覆盖未知编码或用户自定义的乱码。若执行后仍乱码，请提供 `id,name,HEX(name)` 的样例；不要通过清空分类表来重建。

## 先诊断（只读）

在生产项目目录执行。以下命令使用 Compose 的 MySQL 容器环境变量，不把密码写入命令历史。

```sh
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot cyberflow -e "SELECT @@character_set_client,@@character_set_connection,@@character_set_results; SELECT id,name,HEX(name) AS name_hex FROM custom_category ORDER BY id LIMIT 10;"'
```

`书籍` 的正确十六进制为 `E4B9A6E7B18D`。如果数据库值和 HEX 都正常，问题可能在 HTTP 返回、网关或浏览器解码，应保留接口响应继续排查；本迁移会跳过正常名称。

## 执行

先等正在运行的商品采集完成，暂停采集调度并停止分类编辑，安排维护窗口。不要直接停止承载未完成采集任务的消费者。商品分类列已有索引；影响很多商品时，更新事务和备份会占用空间并持有行锁，应先确认可用空间和维护时间。

```sh
# 获取包含本修复的代码，不覆盖未提交的生产改动。
git pull --ff-only

# 额外备份目录、源站配置和菜单；商品字段另由迁移在同一事务中逐条备份。
umask 077
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --default-character-set=utf8mb4 --single-transaction -uroot cyberflow custom_category custom_category_seed crawl_site_config sys_menu' > /tmp/cyberflow-category-before-utf8-repair.sql

# 确认备份命令成功且文件非空，再执行未应用的迁移。
docker compose run --rm --no-deps db-migrate
```

迁移输出 `repaired_categories / repaired_source_sites / repaired_products / repaired_menus`。完成后刷新页面；不需要删除 `schema_migrations` 或 `custom_category_seed`，也不需要清空商品。新的 Spring 配置可随下一次正常后端构建发布，数据库乱码恢复本身不依赖后端重建。

## 核验与回退

```sh
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot cyberflow -e "SELECT id,name,HEX(name) FROM custom_category ORDER BY id LIMIT 10; SELECT target_table,COUNT(*) AS backed_up_rows FROM category_encoding_repair_backup GROUP BY target_table;"'
```

核验分类维护、商品筛选和数据源分类三个页面。若有同名冲突导致迁移失败，先查清冲突记录再重试，不要删除已有分类或强行覆盖。回退应在停止写入后，根据外部备份和 `category_encoding_repair_backup` 的 `target_table,row_id,old_value,repaired_value` 按字段恢复，并保留修复后合法发生的修改。

## 回归测试

```sh
python3 -m unittest discover -s tests -p test_category_encoding.py
# 完整 MySQL 检查使用连接私有临时表，不修改正式数据。
docker compose run --rm --no-deps -e CYBERFLOW_CATEGORY_ENCODING_DB_TEST=1 \
  -v "$PWD:/workspace:ro" -w /workspace crawler-consumer \
  python -m unittest discover -s tests -p test_category_encoding.py
```

6 项检查覆盖迁移字符集、种子映射一致性、原始乱码复现、一/二次乱码恢复、商品/源站关联、自定义数据保留、无损重复执行及冲突回滚。
