-- Repair menu labels that were inserted through a latin1 client and became UTF-8 mojibake.
USE cyberflow;
SET NAMES utf8mb4;

UPDATE sys_menu SET menu_name = '数据源站点' WHERE id = 41;
UPDATE sys_menu SET menu_name = '添加数据源' WHERE id = 43;
UPDATE sys_menu SET menu_name = '运行数据源' WHERE id = 44;
UPDATE sys_menu SET menu_name = '删除数据源' WHERE id = 45;
UPDATE sys_menu SET menu_name = '任务控制' WHERE id = 49;
UPDATE sys_menu SET menu_name = '删除任务' WHERE id = 50;
UPDATE sys_menu SET menu_name = '修改订单配置' WHERE id = 51;
