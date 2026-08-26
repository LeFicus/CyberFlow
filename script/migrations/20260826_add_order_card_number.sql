-- Persist the payment card fingerprint required by the business order
-- deduplication rule. Values returned by the platform may be masked.
USE cyberflow;
SET NAMES utf8mb4;

SET @card_number_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'card_number'
);
SET @card_number_sql = IF(
    @card_number_exists = 0,
    'ALTER TABLE orders ADD COLUMN card_number VARCHAR(100) NULL COMMENT ''支付卡号（可能为掩码）'' AFTER pay_status_text',
    'SELECT 1'
);
PREPARE card_number_stmt FROM @card_number_sql;
EXECUTE card_number_stmt;
DEALLOCATE PREPARE card_number_stmt;
