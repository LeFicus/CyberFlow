-- Rename product collection site entries as data-source management.
-- Safe to run repeatedly on an existing CyberFlow database.
USE cyberflow;

UPDATE sys_menu SET menu_name = '数据源站点' WHERE id = 41;
UPDATE sys_menu SET menu_name = '添加数据源' WHERE id = 43;
UPDATE sys_menu SET menu_name = '运行数据源' WHERE id = 44;
UPDATE sys_menu SET menu_name = '删除数据源' WHERE id = 45;
