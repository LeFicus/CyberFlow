-- Add product tags to crawl sources and persisted products.
-- Existing rows are treated as main products for backward compatibility.

SET @db_name = DATABASE();

SET @has_site_role = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'crawl_site_config' AND COLUMN_NAME = 'product_role'
);
SET @sql = IF(@has_site_role = 0,
    'ALTER TABLE crawl_site_config ADD COLUMN product_role VARCHAR(20) NOT NULL DEFAULT ''main'' COMMENT ''main-主产品 supplement-补充产品'' AFTER type',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE crawl_site_config SET product_role = 'main' WHERE product_role IS NULL OR TRIM(product_role) = '';

SET @has_product_role = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'scraped_data' AND TABLE_NAME = 'ecommerce_products' AND COLUMN_NAME = 'product_role'
);
SET @sql = IF(@has_product_role = 0,
    'ALTER TABLE scraped_data.ecommerce_products ADD COLUMN product_role VARCHAR(20) NOT NULL DEFAULT ''main'' COMMENT ''main-主产品 supplement-补充产品'' AFTER custom_category',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE scraped_data.ecommerce_products SET product_role = 'main' WHERE product_role IS NULL OR TRIM(product_role) = '';

SET @has_role_index = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'scraped_data' AND TABLE_NAME = 'ecommerce_products' AND INDEX_NAME = 'idx_product_role_created'
);
SET @sql = IF(@has_role_index = 0,
    'CREATE INDEX idx_product_role_created ON scraped_data.ecommerce_products (product_role, created_at, id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
