-- Align existing scraped_data.ecommerce_products tables with the crawler's
-- stable product identity field. Safe to run more than once.
USE scraped_data;
SET NAMES utf8mb4;

SET @dedupe_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ecommerce_products'
      AND COLUMN_NAME = 'dedupe_key'
);
SET @dedupe_column_sql = IF(
    @dedupe_column_exists = 0,
    'ALTER TABLE ecommerce_products ADD COLUMN dedupe_key VARCHAR(768) NULL AFTER language',
    'SELECT 1'
);
PREPARE dedupe_column_stmt FROM @dedupe_column_sql;
EXECUTE dedupe_column_stmt;
DEALLOCATE PREPARE dedupe_column_stmt;

SET @dedupe_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ecommerce_products'
      AND INDEX_NAME = 'uk_product_dedupe'
);
SET @dedupe_index_sql = IF(
    @dedupe_index_exists = 0,
    'ALTER TABLE ecommerce_products ADD UNIQUE KEY uk_product_dedupe (dedupe_key)',
    'SELECT 1'
);
PREPARE dedupe_index_stmt FROM @dedupe_index_sql;
EXECUTE dedupe_index_stmt;
DEALLOCATE PREPARE dedupe_index_stmt;
