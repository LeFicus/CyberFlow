-- Cursor pagination indexes. Online DDL; may still take time on large tables.
USE scraped_data;
SET NAMES utf8mb4;

SET @exists_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ecommerce_products' AND INDEX_NAME='idx_product_domain_id');
SET @ddl = IF(@exists_idx = 0, 'ALTER TABLE ecommerce_products ADD INDEX idx_product_domain_id (source_domain, id), ALGORITHM=INPLACE, LOCK=NONE', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ecommerce_products' AND INDEX_NAME='idx_product_category_id');
SET @ddl = IF(@exists_idx = 0, 'ALTER TABLE ecommerce_products ADD INDEX idx_product_category_id (custom_category, id), ALGORITHM=INPLACE, LOCK=NONE', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ecommerce_products' AND INDEX_NAME='idx_product_role_id');
SET @ddl = IF(@exists_idx = 0, 'ALTER TABLE ecommerce_products ADD INDEX idx_product_role_id (product_role, id), ALGORITHM=INPLACE, LOCK=NONE', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ecommerce_products' AND INDEX_NAME='idx_product_sku_id');
SET @ddl = IF(@exists_idx = 0, 'ALTER TABLE ecommerce_products ADD INDEX idx_product_sku_id (sku(100), id), ALGORITHM=INPLACE, LOCK=NONE', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
