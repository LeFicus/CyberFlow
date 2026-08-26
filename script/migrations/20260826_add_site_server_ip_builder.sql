-- Persist the safe subset of nested admin_info/server_info returned by the site API.
USE cyberflow;
SET NAMES utf8mb4;

SET @builder_username_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_info' AND COLUMN_NAME='builder_username'
);
SET @builder_username_sql = IF(@builder_username_exists=0,
    'ALTER TABLE site_info ADD COLUMN builder_username VARCHAR(100) NULL COMMENT ''建站者账号'' AFTER username',
    'SELECT 1');
PREPARE builder_username_stmt FROM @builder_username_sql;
EXECUTE builder_username_stmt;
DEALLOCATE PREPARE builder_username_stmt;

SET @site_server_ip_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_info' AND COLUMN_NAME='server_ip'
);
SET @site_server_ip_sql = IF(@site_server_ip_exists=0,
    'ALTER TABLE site_info ADD COLUMN server_ip VARCHAR(45) NULL COMMENT ''站点服务器 IP'' AFTER server_name',
    'SELECT 1');
PREPARE site_server_ip_stmt FROM @site_server_ip_sql;
EXECUTE site_server_ip_stmt;
DEALLOCATE PREPARE site_server_ip_stmt;

SET @history_server_ip_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_indexing_history' AND COLUMN_NAME='server_ip'
);
SET @history_server_ip_sql = IF(@history_server_ip_exists=0,
    'ALTER TABLE site_indexing_history ADD COLUMN server_ip VARCHAR(45) NULL COMMENT ''采集时站点服务器 IP'' AFTER server_name',
    'SELECT 1');
PREPARE history_server_ip_stmt FROM @history_server_ip_sql;
EXECUTE history_server_ip_stmt;
DEALLOCATE PREPARE history_server_ip_stmt;

SET @builder_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_info' AND INDEX_NAME='idx_site_builder_username'
);
SET @builder_index_sql = IF(@builder_index_exists=0,
    'CREATE INDEX idx_site_builder_username ON site_info(builder_username)', 'SELECT 1');
PREPARE builder_index_stmt FROM @builder_index_sql;
EXECUTE builder_index_stmt;
DEALLOCATE PREPARE builder_index_stmt;
