-- Existing installations: run against the cyberflow management database.
-- The backend also applies this permission through schema.sql on startup.
INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order, status)
VALUES
    (67, 63, '删除新站点', 2, 'newsite:delete', NULL, NULL, NULL, 4, 1)
ON DUPLICATE KEY UPDATE
    parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type),
    perms=VALUES(perms), sort_order=VALUES(sort_order), status=VALUES(status);

-- Match the existing default new-site management roles.
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 67), (2, 67);
