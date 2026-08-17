-- Restore the product-collection navigation for existing databases that were
-- deployed without the product navigation migration.
USE cyberflow;
SET NAMES utf8mb4;

INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, status)
VALUES
    (4, 0, '商品采集', 0, '/product-crawler', NULL, NULL, 'Goods', 3, 1),
    (41, 4, '数据源站点', 1, '/crawler/site-config', 'crawler/SiteConfig', 'crawler:site:config:list', NULL, 1, 1),
    (42, 4, '选择器模板', 1, '/crawler/selector-template', 'crawler/SelectorTemplate', 'selector:template:list', NULL, 2, 1)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    path = VALUES(path),
    component = VALUES(component),
    perms = VALUES(perms),
    icon = VALUES(icon),
    sort_order = VALUES(sort_order),
    status = VALUES(status);

INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, status, sort_order)
VALUES
    (43, 41, '添加数据源', 2, 'crawler:site:config:create', 1, 1),
    (44, 41, '运行数据源', 2, 'crawler:site:config:crawl', 1, 2),
    (45, 41, '删除数据源', 2, 'crawler:site:config:delete', 1, 3),
    (46, 42, '新增选择器模板', 2, 'selector:template:create', 1, 1),
    (47, 42, '修改选择器模板', 2, 'selector:template:update', 1, 2),
    (48, 42, '删除选择器模板', 2, 'selector:template:delete', 1, 3)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    perms = VALUES(perms),
    status = VALUES(status),
    sort_order = VALUES(sort_order);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, menu_id
FROM (
    SELECT 1 AS role_id, id AS menu_id FROM sys_menu WHERE id IN (4, 41, 42, 43, 44, 45, 46, 47, 48)
    UNION ALL
    SELECT 2 AS role_id, id AS menu_id FROM sys_menu WHERE id IN (4, 41, 42, 43, 44, 45, 46, 47, 48)
) AS product_menu_roles;
