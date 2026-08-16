-- 补齐系统管理 CRUD 权限及管理员角色关联。
-- 可重复执行，适用于已存在的 CyberFlow 数据库。
USE cyberflow;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, status, sort_order) VALUES
(52, 31, '新增用户', 2, 'system:user:create', 1, 1),
(53, 31, '修改用户', 2, 'system:user:update', 1, 2),
(54, 31, '删除用户', 2, 'system:user:delete', 1, 3),
(55, 31, '分配用户角色', 2, 'system:user:assign', 1, 4),
(56, 32, '新增角色', 2, 'system:role:create', 1, 1),
(57, 32, '修改角色', 2, 'system:role:update', 1, 2),
(58, 32, '删除角色', 2, 'system:role:delete', 1, 3),
(59, 32, '分配角色菜单', 2, 'system:role:assign', 1, 4),
(60, 33, '新增菜单', 2, 'system:menu:create', 1, 1),
(61, 33, '修改菜单', 2, 'system:menu:update', 1, 2),
(62, 33, '删除菜单', 2, 'system:menu:delete', 1, 3)
ON DUPLICATE KEY UPDATE
  parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type),
  perms=VALUES(perms), status=VALUES(status), sort_order=VALUES(sort_order);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 52 AND 62;
