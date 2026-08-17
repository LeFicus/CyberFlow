-- Ensure operator and normal-user roles retain the data-dashboard navigation
-- when an existing database was deployed with incomplete role-menu rows.
USE cyberflow;
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT roles.role_id, menus.id
FROM (SELECT 2 AS role_id UNION ALL SELECT 3) roles
CROSS JOIN sys_menu menus
WHERE menus.id IN (1, 11, 12, 13)
  AND menus.status = 1;
