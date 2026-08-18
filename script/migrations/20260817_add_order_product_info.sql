-- Preserve the productInfo array returned by the payment platform order crawler.
USE cyberflow;
SET NAMES utf8mb4;

SET @product_info_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'product_info'
);
SET @product_info_sql = IF(
    @product_info_exists = 0,
    'ALTER TABLE orders ADD COLUMN product_info JSON NULL COMMENT ''订单爬取结果中的商品详情数组'' AFTER product_category',
    'SELECT 1'
);
PREPARE product_info_stmt FROM @product_info_sql;
EXECUTE product_info_stmt;
DEALLOCATE PREPARE product_info_stmt;
