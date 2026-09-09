-- Persist the original delivery address and the platform validity flag.
-- dedupe_key is maintained by the order consumer after grouping rows whose
-- normalized email OR normalized shipping address matches on the same day/site.
USE cyberflow;
SET NAMES utf8mb4;

SET @shipping_address_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'shipping_address'
);
SET @shipping_address_sql = IF(
    @shipping_address_exists = 0,
    'ALTER TABLE orders ADD COLUMN shipping_address JSON NULL COMMENT ''支付平台原始收货地址'' AFTER shipping_email',
    'SELECT 1'
);
PREPARE shipping_address_stmt FROM @shipping_address_sql;
EXECUTE shipping_address_stmt;
DEALLOCATE PREPARE shipping_address_stmt;

SET @is_valid_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'is_valid'
);
SET @is_valid_sql = IF(
    @is_valid_exists = 0,
    'ALTER TABLE orders ADD COLUMN is_valid TINYINT NULL COMMENT ''支付平台有效标记；0 为有效'' AFTER shipping_address',
    'SELECT 1'
);
PREPARE is_valid_stmt FROM @is_valid_sql;
EXECUTE is_valid_stmt;
DEALLOCATE PREPARE is_valid_stmt;

SET @dedupe_key_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'dedupe_key'
);
SET @dedupe_key_sql = IF(
    @dedupe_key_exists = 0,
    'ALTER TABLE orders ADD COLUMN dedupe_key CHAR(64) NULL COMMENT ''邮箱或收货地址关联后的去重键'' AFTER is_valid',
    'SELECT 1'
);
PREPARE dedupe_key_stmt FROM @dedupe_key_sql;
EXECUTE dedupe_key_stmt;
DEALLOCATE PREPARE dedupe_key_stmt;

-- Historical rows initially retain the previous email-only behavior. Re-crawled
-- rows are regrouped with their shipping address by the consumer.
UPDATE orders
SET dedupe_key = SHA2(
    CASE
        WHEN create_time IS NOT NULL
          AND LENGTH(TRIM(COALESCE(user_group, ''))) > 0
          AND LENGTH(TRIM(COALESCE(product_host, ''))) > 0
          AND LENGTH(TRIM(COALESCE(shipping_email, ''))) > 0
        THEN CONCAT(
            DATE_FORMAT(create_time, '%Y-%m-%d'), CHAR(31), UPPER(TRIM(user_group)), CHAR(31),
            LOWER(CASE WHEN LEFT(TRIM(product_host), 4) = 'www.'
                THEN SUBSTRING(TRIM(product_host), 5) ELSE TRIM(product_host) END),
            CHAR(31), LOWER(TRIM(shipping_email))
        )
        ELSE CONCAT('ORDER_ID', CHAR(31), COALESCE(user_group, ''), CHAR(31), CAST(id AS CHAR))
    END,
    256
)
WHERE dedupe_key IS NULL OR TRIM(dedupe_key) = '';

SET @dedupe_key_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_order_dedupe_key'
);
SET @dedupe_key_index_sql = IF(
    @dedupe_key_index_exists = 0,
    'ALTER TABLE orders ADD INDEX idx_order_dedupe_key (dedupe_key)',
    'SELECT 1'
);
PREPARE dedupe_key_index_stmt FROM @dedupe_key_index_sql;
EXECUTE dedupe_key_index_stmt;
DEALLOCATE PREPARE dedupe_key_index_stmt;
