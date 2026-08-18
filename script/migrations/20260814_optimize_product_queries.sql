-- Indexes for large product-list filters, deterministic pagination and exports.
-- Each index is guarded so a partial or repeated deployment is safe.
USE scraped_data;
SET NAMES utf8mb4;

SET @idx_created_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ecommerce_products'
      AND INDEX_NAME = 'idx_product_created_id'
);
SET @idx_created_sql = IF(
    @idx_created_exists = 0,
    'ALTER TABLE ecommerce_products ADD INDEX idx_product_created_id (created_at, id)',
    'SELECT 1'
);
PREPARE idx_created_stmt FROM @idx_created_sql;
EXECUTE idx_created_stmt;
DEALLOCATE PREPARE idx_created_stmt;

SET @idx_domain_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ecommerce_products'
      AND INDEX_NAME = 'idx_product_domain_created'
);
SET @idx_domain_sql = IF(
    @idx_domain_exists = 0,
    'ALTER TABLE ecommerce_products ADD INDEX idx_product_domain_created (source_domain, created_at, id)',
    'SELECT 1'
);
PREPARE idx_domain_stmt FROM @idx_domain_sql;
EXECUTE idx_domain_stmt;
DEALLOCATE PREPARE idx_domain_stmt;

SET @idx_category_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ecommerce_products'
      AND INDEX_NAME = 'idx_product_category_created'
);
SET @idx_category_sql = IF(
    @idx_category_exists = 0,
    'ALTER TABLE ecommerce_products ADD INDEX idx_product_category_created (custom_category, created_at, id)',
    'SELECT 1'
);
PREPARE idx_category_stmt FROM @idx_category_sql;
EXECUTE idx_category_stmt;
DEALLOCATE PREPARE idx_category_stmt;

SET @idx_name_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ecommerce_products'
      AND INDEX_NAME = 'idx_product_name_prefix'
);
SET @idx_name_sql = IF(
    @idx_name_exists = 0,
    'ALTER TABLE ecommerce_products ADD INDEX idx_product_name_prefix (name(100))',
    'SELECT 1'
);
PREPARE idx_name_stmt FROM @idx_name_sql;
EXECUTE idx_name_stmt;
DEALLOCATE PREPARE idx_name_stmt;
