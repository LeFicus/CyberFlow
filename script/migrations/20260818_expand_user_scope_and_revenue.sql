-- Support multiple data owners and expose revenue settings as a dedicated menu.
USE cyberflow;
SET NAMES utf8mb4;

ALTER TABLE sys_user MODIFY COLUMN data_owner VARCHAR(1000)
    COMMENT '外部站点/订单管理员名称列表，用逗号分隔';

INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order, status)
VALUES
    (35, 2, '收入参数', 1, 'crawler:revenue:view', '/crawler/revenue-config', 'crawler/RevenueConfig', 'Money', 6, 1),
    (36, 35, '修改收入参数', 2, 'crawler:revenue:update', NULL, NULL, NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type),
    perms = VALUES(perms),
    path = VALUES(path),
    component = VALUES(component),
    icon = VALUES(icon),
    sort_order = VALUES(sort_order),
    status = VALUES(status);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 35), (1, 36), (2, 35), (2, 36);
