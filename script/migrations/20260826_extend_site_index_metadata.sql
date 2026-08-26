-- Add server and submission metadata required by the site/indexing reports.
USE cyberflow;
SET NAMES utf8mb4;

SET @site_server_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_info' AND COLUMN_NAME='server_name'
);
SET @site_server_sql = IF(@site_server_exists=0,
    'ALTER TABLE site_info ADD COLUMN server_name VARCHAR(255) NULL COMMENT ''站点所在服务器'' AFTER site_domain',
    'SELECT 1');
PREPARE site_server_stmt FROM @site_server_sql;
EXECUTE site_server_stmt;
DEALLOCATE PREPARE site_server_stmt;

SET @site_submit_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_info' AND COLUMN_NAME='last_submitted_at'
);
SET @site_submit_sql = IF(@site_submit_exists=0,
    'ALTER TABLE site_info ADD COLUMN last_submitted_at DATETIME NULL COMMENT ''最近提交收录时间'' AFTER product_category',
    'SELECT 1');
PREPARE site_submit_stmt FROM @site_submit_sql;
EXECUTE site_submit_stmt;
DEALLOCATE PREPARE site_submit_stmt;

SET @history_server_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_indexing_history' AND COLUMN_NAME='server_name'
);
SET @history_server_sql = IF(@history_server_exists=0,
    'ALTER TABLE site_indexing_history ADD COLUMN server_name VARCHAR(255) NULL COMMENT ''采集时站点所在服务器'' AFTER product_count',
    'SELECT 1');
PREPARE history_server_stmt FROM @history_server_sql;
EXECUTE history_server_stmt;
DEALLOCATE PREPARE history_server_stmt;

SET @history_submit_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_indexing_history' AND COLUMN_NAME='last_submitted_at'
);
SET @history_submit_sql = IF(@history_submit_exists=0,
    'ALTER TABLE site_indexing_history ADD COLUMN last_submitted_at DATETIME NULL COMMENT ''最近提交收录时间'' AFTER server_name',
    'SELECT 1');
PREPARE history_submit_stmt FROM @history_submit_sql;
EXECUTE history_submit_stmt;
DEALLOCATE PREPARE history_submit_stmt;

-- Until the next site/index crawl supplies the remote server name, show the
-- configured admin host instead of leaving existing sites blank.
SET @admin_base_url = (
    SELECT config_value FROM crawler_runtime_config
    WHERE config_group='adminApi' AND config_key='baseUrl' LIMIT 1
);
UPDATE site_info
SET server_name = SUBSTRING_INDEX(
        SUBSTRING_INDEX(REPLACE(REPLACE(COALESCE(@admin_base_url, ''), 'https://', ''), 'http://', ''), '/', 1),
        ':', 1)
WHERE TRIM(COALESCE(server_name, '')) = '' AND TRIM(COALESCE(@admin_base_url, '')) <> '';

INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order, status)
VALUES
    (16, 1, '收录数据列表', 1, 'dashboard:site:view', '/dashboard/indexing', 'dashboard/IndexingList', NULL, 5, 1)
ON DUPLICATE KEY UPDATE
    parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type),
    perms=VALUES(perms), path=VALUES(path), component=VALUES(component),
    sort_order=VALUES(sort_order), status=VALUES(status);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 16), (2, 16), (3, 16);
