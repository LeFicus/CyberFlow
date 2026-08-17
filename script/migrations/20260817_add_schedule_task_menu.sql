-- Add a dedicated plan-task management menu under the data synchronization section.
USE cyberflow;
SET NAMES utf8mb4;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, status)
VALUES (28, 2, '计划任务', 1, '/crawler/schedule', 'crawler/ScheduleTask', 'crawler:schedule:view', 'Timer', 5, 1)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    path = VALUES(path),
    component = VALUES(component),
    perms = VALUES(perms),
    icon = VALUES(icon),
    sort_order = VALUES(sort_order),
    status = VALUES(status);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, status, sort_order)
VALUES
    (29, 28, '修改计划任务', 2, 'crawler:schedule:update', 1, 1),
    (30, 28, '手动触发计划任务', 2, 'crawler:schedule:trigger', 1, 2)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    perms = VALUES(perms),
    status = VALUES(status),
    sort_order = VALUES(sort_order);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 28), (1, 29), (1, 30), (2, 28), (2, 29), (2, 30);
