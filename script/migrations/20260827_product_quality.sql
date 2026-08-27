SET NAMES utf8mb4;
SET @has_column = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='scraped_data' AND TABLE_NAME='ecommerce_products' AND COLUMN_NAME='original_price_usd');
SET @ddl = IF(@has_column=0, 'ALTER TABLE scraped_data.ecommerce_products ADD COLUMN original_price_usd DECIMAL(10,2) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @has_column = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='scraped_data' AND TABLE_NAME='ecommerce_products' AND COLUMN_NAME='image_usable');
SET @ddl = IF(@has_column=0, 'ALTER TABLE scraped_data.ecommerce_products ADD COLUMN image_usable TINYINT NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE TABLE IF NOT EXISTS scraped_data.product_policy_audit (
 product_id BIGINT PRIMARY KEY, regular_price DECIMAL(10,2), images LONGTEXT,
 original_price_usd DECIMAL(10,2), image_usable TINYINT,
 changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
