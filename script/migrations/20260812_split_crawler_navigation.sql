-- Split crawler navigation into data synchronization and product collection.
-- Safe to run repeatedly on an existing CyberFlow database.
USE cyberflow;
SET NAMES utf8mb4;

UPDATE sys_menu
SET menu_name = '数据同步', icon = 'RefreshRight', sort_order = 2
WHERE id = 2;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, status) VALUES
(4,  0,  '商品采集',   0, '/product-crawler', NULL,                       NULL,                         'Goods', 3, 1),
(41, 4,  '采集站点',   1, '/crawler/site-config', 'crawler/SiteConfig',   'crawler:site:config:list',   NULL, 1, 1),
(42, 4,  '选择器模板', 1, '/crawler/selector-template', 'crawler/SelectorTemplate', 'selector:template:list', NULL, 2, 1),
(43, 41, '新增采集站点', 2, NULL, NULL, 'crawler:site:config:create', NULL, 1, 1),
(44, 41, '执行商品采集', 2, NULL, NULL, 'crawler:site:config:crawl',  NULL, 2, 1),
(45, 41, '删除采集站点', 2, NULL, NULL, 'crawler:site:config:delete', NULL, 3, 1),
(46, 42, '新增选择器模板', 2, NULL, NULL, 'selector:template:create', NULL, 1, 1),
(47, 42, '修改选择器模板', 2, NULL, NULL, 'selector:template:update', NULL, 2, 1),
(48, 42, '删除选择器模板', 2, NULL, NULL, 'selector:template:delete', NULL, 3, 1)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  path = VALUES(path), component = VALUES(component), perms = VALUES(perms),
  icon = VALUES(icon), sort_order = VALUES(sort_order), status = VALUES(status);

-- Keep the system section after the new product collection section.
UPDATE sys_menu SET sort_order = 4 WHERE id = 3;

-- Administrators receive every new menu/operation; operators receive crawler-related entries.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (4, 41, 42, 43, 44, 45, 46, 47, 48);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE id IN (4, 41, 42, 43, 44, 45, 46, 47, 48);
