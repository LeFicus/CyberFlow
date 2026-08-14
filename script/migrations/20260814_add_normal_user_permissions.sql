INSERT INTO sys_role (id, role_name, role_code, description, status)
VALUES (3, '普通用户', 'ROLE_USER', '仅可查看经营数据和执行 A/B 订单爬取', 1)
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name), description=VALUES(description), status=1;
UPDATE sys_menu SET perms='crawler:order:view' WHERE id=23;
UPDATE sys_menu SET perms='crawler:history:view' WHERE id=24;
UPDATE sys_menu SET perms='crawler:order:start' WHERE id=27;
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, status, sort_order)
VALUES
(49, 24, '任务控制', 2, 'crawler:task:control', 1, 1),
(50, 24, '删除任务', 2, 'crawler:task:delete', 1, 2),
(51, 23, '修改订单配置', 2, 'crawler:order:config', 1, 2)
ON DUPLICATE KEY UPDATE perms=VALUES(perms), status=1;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 49), (1, 50), (1, 51), (2, 49), (2, 50), (2, 51);
DELETE FROM sys_role_menu WHERE role_id=3;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(3, 1), (3, 11), (3, 12), (3, 13), (3, 2), (3, 23), (3, 24), (3, 27);
INSERT INTO sys_user (id, username, password, nickname, status)
VALUES (2, 'normal_user', '$2b$12$VA8lCR9fDcXkscYPUls7.O6dGD67C1FKpx9HtTIwuX3nzq5fVJ7KC', '普通用户', 1)
ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), status=1;
DELETE FROM sys_user_role WHERE user_id=2;
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 3);
