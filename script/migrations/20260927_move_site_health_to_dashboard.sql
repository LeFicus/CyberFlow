-- Place the read-only health status page with other dashboard views.
-- Keep the separate trigger permission on menu 77 for existing operators.
UPDATE sys_menu
SET parent_id = 1,
    menu_name = '站点健康检查',
    path = '/dashboard/site-health',
    sort_order = 5,
    status = 1
WHERE id = 76;

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT r.id, 76
FROM sys_role r
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_USER');
