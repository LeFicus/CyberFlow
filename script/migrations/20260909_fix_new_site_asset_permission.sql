-- Menu id 68 is already used by the indexing builder summary on upgraded
-- installations. Keep the existing navigation item and assign brand assets
-- a dedicated id that is consistent with fresh database initialization.
INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order, status)
VALUES
    (72, 63, '管理品牌素材', 2, 'newsite:asset', NULL, NULL, NULL, 5, 1)
ON DUPLICATE KEY UPDATE
    parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type),
    perms=VALUES(perms), path=VALUES(path), component=VALUES(component), icon=VALUES(icon),
    sort_order=VALUES(sort_order), status=VALUES(status);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 72), (2, 72);
