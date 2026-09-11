-- Persist site classification metadata and allow database-defined group names.
USE cyberflow;
SET NAMES utf8mb4;

SET @cat_names_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_info' AND COLUMN_NAME = 'cat_names'
);
SET @cat_names_sql = IF(@cat_names_exists = 0,
    'ALTER TABLE site_info ADD COLUMN cat_names JSON NULL AFTER product_category', 'SELECT 1');
PREPARE stmt FROM @cat_names_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @site_tag_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_info' AND COLUMN_NAME = 'site_tag'
);
SET @site_tag_sql = IF(@site_tag_exists = 0,
    'ALTER TABLE site_info ADD COLUMN site_tag TINYINT NOT NULL DEFAULT 0 COMMENT ''0单独建站 1批量建站 2复制站'' AFTER cat_names', 'SELECT 1');
PREPARE stmt FROM @site_tag_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @order_site_tag_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'site_tag'
);
SET @order_site_tag_sql = IF(@order_site_tag_exists = 0,
    'ALTER TABLE orders ADD COLUMN site_tag TINYINT NOT NULL DEFAULT 0 COMMENT ''订单所属站点标签'' AFTER product_category', 'SELECT 1');
PREPARE stmt FROM @order_site_tag_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE site_info MODIFY COLUMN user_group VARCHAR(32) NULL COMMENT '负责人用户组（来自站点数据）';
ALTER TABLE orders MODIFY COLUMN user_group VARCHAR(32) NOT NULL COMMENT '订单所属站点分组';

UPDATE orders o
JOIN site_info s
  ON LOWER(CASE WHEN LEFT(TRIM(s.site_domain), 4)='www.' THEN SUBSTRING(TRIM(s.site_domain), 5) ELSE TRIM(s.site_domain) END)
   = LOWER(CASE WHEN LEFT(TRIM(o.product_host), 4)='www.' THEN SUBSTRING(TRIM(o.product_host), 5) ELSE TRIM(o.product_host) END)
SET o.admin_name = s.admin_name,
    o.theme_name = s.theme_name,
    o.product_category = s.product_category,
    o.site_tag = s.site_tag;

INSERT INTO crawler_runtime_config (config_group, config_key, config_value, is_sensitive, remark)
VALUES ('revenue', 'batchSiteCommissionRate', '0.02', 0, 'Batch-built site member commission rate')
ON DUPLICATE KEY UPDATE config_value = config_value;
