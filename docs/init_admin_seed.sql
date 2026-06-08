-- ============================================================
-- CyberFlow 后台管理系统 — 种子数据
-- 默认管理员: admin / admin123
-- ============================================================

USE cyberflow;

-- 管理员角色
INSERT INTO sys_role (id, role_name, role_code, description) VALUES
(1, '超级管理员', 'ROLE_ADMIN', '拥有所有权限'),
(2, '运营人员', 'ROLE_OPERATOR', '可查看数据看板、触发爬虫');

-- 默认管理员用户 (密码: admin123, BCrypt 加密)
INSERT INTO sys_user (id, username, password, nickname, status) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5Eh', '系统管理员', 1);

-- 管理员拥有两个角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1), (1, 2);

-- 菜单树 (id 自定，父级在前)
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort_order) VALUES
-- 目录
(1,  0, '数据看板', 0, '/dashboard', NULL, 'DataBoard', 1),
(2,  0, '爬虫管理', 0, '/crawler', NULL, 'Cpu', 2),
(3,  0, '系统管理', 0, '/system', NULL, 'Setting', 3),
-- 看板子菜单
(11, 1, '概览', 1, '/dashboard/overview', 'dashboard/Overview', NULL, 1),
(12, 1, '站点列表', 1, '/dashboard/sites', 'dashboard/SiteList', NULL, 2),
(13, 1, '订单列表', 1, '/dashboard/orders', 'dashboard/OrderList', NULL, 3),
(14, 1, '商品列表', 1, '/dashboard/products', 'dashboard/ProductList', NULL, 4),
-- 爬虫子菜单
(21, 2, '站点爬虫', 1, '/crawler/site', 'crawler/SiteCrawler', NULL, 1),
(22, 2, '收录统计', 1, '/crawler/collect', 'crawler/CollectCrawler', NULL, 2),
(23, 2, '订单爬虫', 1, '/crawler/order', 'crawler/OrderCrawler', NULL, 3),
(24, 2, '任务历史', 1, '/crawler/history', 'crawler/TaskHistory', NULL, 4),
-- 爬虫按钮权限
(25, 21, '触发站点爬虫', 2, NULL, NULL, NULL, 1),
(26, 22, '触发收录统计', 2, NULL, NULL, NULL, 1),
(27, 23, '触发订单爬虫', 2, NULL, NULL, NULL, 1),
-- 系统管理子菜单
(31, 3, '用户管理', 1, '/system/user', 'system/UserList', NULL, 1),
(32, 3, '角色管理', 1, '/system/role', 'system/RoleList', NULL, 2),
(33, 3, '菜单管理', 1, '/system/menu', 'system/MenuTree', NULL, 3),
(34, 3, '操作日志', 1, '/system/log', 'system/OperationLog', NULL, 4);

-- 设置权限标识
UPDATE sys_menu SET perms = 'dashboard:overview' WHERE id = 11;
UPDATE sys_menu SET perms = 'dashboard:site:view' WHERE id = 12;
UPDATE sys_menu SET perms = 'dashboard:order:view' WHERE id = 13;
UPDATE sys_menu SET perms = 'dashboard:product:view' WHERE id = 14;
UPDATE sys_menu SET perms = 'crawler:site:start' WHERE id = 25;
UPDATE sys_menu SET perms = 'crawler:collect:start' WHERE id = 26;
UPDATE sys_menu SET perms = 'crawler:order:start' WHERE id = 27;
UPDATE sys_menu SET perms = 'system:user:list' WHERE id = 31;
UPDATE sys_menu SET perms = 'system:role:list' WHERE id = 32;
UPDATE sys_menu SET perms = 'system:menu:list' WHERE id = 33;
UPDATE sys_menu SET perms = 'system:log:view' WHERE id = 34;

-- 为管理员角色分配所有菜单权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 为运营角色分配看板和爬虫权限（不包括系统管理）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE menu_type = 1 AND parent_id IN (1, 2);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, id FROM sys_menu WHERE id IN (25, 26, 27);
