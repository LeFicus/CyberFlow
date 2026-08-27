-- Keep domain application time separate from the later site build time.
USE cyberflow;
SET NAMES utf8mb4;

SET @domain_applied_at_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_info' AND COLUMN_NAME='domain_applied_at'
);
SET @domain_applied_at_sql = IF(@domain_applied_at_exists=0,
    'ALTER TABLE site_info ADD COLUMN domain_applied_at DATETIME NULL COMMENT ''域名申请时间，用于站点月份归属'' AFTER product_category',
    'SELECT 1');
PREPARE domain_applied_at_stmt FROM @domain_applied_at_sql;
EXECUTE domain_applied_at_stmt;
DEALLOCATE PREPARE domain_applied_at_stmt;

SET @domain_applied_at_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_info' AND INDEX_NAME='idx_site_domain_applied_at'
);
SET @domain_applied_at_index_sql = IF(@domain_applied_at_index_exists=0,
    'CREATE INDEX idx_site_domain_applied_at ON site_info(domain_applied_at)',
    'SELECT 1');
PREPARE domain_applied_at_index_stmt FROM @domain_applied_at_index_sql;
EXECUTE domain_applied_at_index_stmt;
DEALLOCATE PREPARE domain_applied_at_index_stmt;
