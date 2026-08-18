-- A/B user grouping follows the reference report scripts:
-- administrators beginning with A- belong to group A; B- belongs to group B.
-- The DDL is guarded because this migration is also used for partial upgrades.
USE cyberflow;
SET NAMES utf8mb4;

SET @site_group_column_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_info'
      AND COLUMN_NAME = 'user_group'
);
SET @site_group_column_sql = IF(
    @site_group_column_exists = 0,
    'ALTER TABLE site_info ADD COLUMN user_group VARCHAR(1) NULL COMMENT ''负责人用户组: A/B'' AFTER admin_name',
    'SELECT 1'
);
PREPARE site_group_column_stmt FROM @site_group_column_sql;
EXECUTE site_group_column_stmt;
DEALLOCATE PREPARE site_group_column_stmt;

SET @site_group_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_info'
      AND INDEX_NAME = 'idx_site_user_group'
);
SET @site_group_index_sql = IF(
    @site_group_index_exists = 0,
    'ALTER TABLE site_info ADD INDEX idx_site_user_group (user_group)',
    'SELECT 1'
);
PREPARE site_group_index_stmt FROM @site_group_index_sql;
EXECUTE site_group_index_stmt;
DEALLOCATE PREPARE site_group_index_stmt;

SET @order_group_column_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'user_group'
);
SET @order_group_column_sql = IF(
    @order_group_column_exists = 0,
    'ALTER TABLE orders ADD COLUMN user_group VARCHAR(1) NULL COMMENT ''订单所属负责人用户组: A/B'' AFTER admin_name',
    'SELECT 1'
);
PREPARE order_group_column_stmt FROM @order_group_column_sql;
EXECUTE order_group_column_stmt;
DEALLOCATE PREPARE order_group_column_stmt;

SET @order_group_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'idx_order_user_group'
);
SET @order_group_index_sql = IF(
    @order_group_index_exists = 0,
    'ALTER TABLE orders ADD INDEX idx_order_user_group (user_group)',
    'SELECT 1'
);
PREPARE order_group_index_stmt FROM @order_group_index_sql;
EXECUTE order_group_index_stmt;
DEALLOCATE PREPARE order_group_index_stmt;

UPDATE site_info
SET user_group = CASE
    WHEN UPPER(TRIM(admin_name)) LIKE 'A-%' THEN 'A'
    WHEN UPPER(TRIM(admin_name)) LIKE 'B-%' THEN 'B'
    ELSE NULL
END;

UPDATE orders o
LEFT JOIN site_info s
    ON LOWER(TRIM(LEADING 'www.' FROM o.product_host)) =
       LOWER(TRIM(LEADING 'www.' FROM s.site_domain))
SET o.admin_name = COALESCE(NULLIF(s.admin_name, ''), o.admin_name),
    o.theme_name = COALESCE(NULLIF(s.theme_name, ''), o.theme_name),
    o.product_category = COALESCE(NULLIF(s.product_category, ''), o.product_category),
    o.user_group = COALESCE(
        o.user_group,
        s.user_group,
        CASE
            WHEN UPPER(TRIM(o.admin_name)) LIKE 'A-%' THEN 'A'
            WHEN UPPER(TRIM(o.admin_name)) LIKE 'B-%' THEN 'B'
            ELSE NULL
        END
    );
