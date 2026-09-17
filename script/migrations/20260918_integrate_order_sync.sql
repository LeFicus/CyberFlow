-- Move order synchronization into the unified site/index synchronization page.
-- Menu 23 becomes a button permission instead of being removed, preserving
-- crawler:order:view for every existing role.
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 21 FROM sys_role_menu WHERE menu_id = 23;

UPDATE sys_menu
SET menu_name = '站点、收录与订单同步', path = '/crawler/site',
    component = 'crawler/SiteCrawler', status = 1
WHERE id = 21;

UPDATE sys_menu
SET parent_id = 21, menu_name = '查看订单同步', menu_type = 2,
    perms = 'crawler:order:view', path = NULL, component = NULL,
    sort_order = 3, status = 1
WHERE id = 23;

UPDATE sys_menu SET parent_id = 21, sort_order = 4 WHERE id = 27;
UPDATE sys_menu SET parent_id = 21, sort_order = 5 WHERE id = 51;
