-- Add the AI configuration button for installations that already applied
-- 20260819_add_new_site_module.sql before this setting was introduced.

INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order, status)
VALUES
    (66, 63, '配置 AI 服务', 2, 'newsite:config', NULL, NULL, NULL, 3, 1)
ON DUPLICATE KEY UPDATE
    parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type),
    perms=VALUES(perms), sort_order=VALUES(sort_order), status=VALUES(status);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 66), (2, 66);
