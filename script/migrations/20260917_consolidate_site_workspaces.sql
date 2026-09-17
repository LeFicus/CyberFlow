-- Merge duplicate site/index navigation and site/index crawler entry points.
-- Existing role assignments are copied to the surviving pages before the old
-- menus are hidden, so upgrades do not remove access.
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 12 FROM sys_role_menu WHERE menu_id IN (16, 68, 69);

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT role_id, 21 FROM sys_role_menu WHERE menu_id = 22;

UPDATE sys_menu
SET menu_name = '站点与收录', path = '/dashboard/sites', component = 'dashboard/SiteList', status = 1
WHERE id = 12;

UPDATE sys_menu SET status = 0 WHERE id IN (6, 16, 68, 69);

UPDATE sys_menu
SET menu_name = '站点与收录同步', path = '/crawler/site', component = 'crawler/SiteCrawler', status = 1
WHERE id = 21;

UPDATE sys_menu SET status = 0 WHERE id = 22;
UPDATE sys_menu SET parent_id = 21, sort_order = 2 WHERE id = 26;
