-- Store main and supplement product categories as JSON arrays so one new site
-- can use multiple categories for either product role.
USE cyberflow;
SET NAMES utf8mb4;

SET @main_categories_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'new_site'
      AND COLUMN_NAME = 'main_product_categories'
);
SET @main_categories_sql = IF(
    @main_categories_exists = 0,
    'ALTER TABLE new_site ADD COLUMN main_product_categories JSON NULL AFTER custom_category',
    'SELECT 1'
);
PREPARE main_categories_stmt FROM @main_categories_sql;
EXECUTE main_categories_stmt;
DEALLOCATE PREPARE main_categories_stmt;

SET @supplement_categories_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'new_site'
      AND COLUMN_NAME = 'supplement_product_categories'
);
SET @supplement_categories_sql = IF(
    @supplement_categories_exists = 0,
    'ALTER TABLE new_site ADD COLUMN supplement_product_categories JSON NULL AFTER main_product_categories',
    'SELECT 1'
);
PREPARE supplement_categories_stmt FROM @supplement_categories_sql;
EXECUTE supplement_categories_stmt;
DEALLOCATE PREPARE supplement_categories_stmt;

UPDATE new_site
SET main_product_categories = JSON_ARRAY(TRIM(main_product_category))
WHERE main_product_categories IS NULL;

UPDATE new_site
SET supplement_product_categories = JSON_ARRAY(TRIM(supplement_product_category))
WHERE supplement_product_categories IS NULL;

SET @supplement_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'new_site'
      AND INDEX_NAME = 'uk_new_site_supplement_category'
);
SET @supplement_index_sql = IF(
    @supplement_index_exists > 0,
    'ALTER TABLE new_site DROP INDEX uk_new_site_supplement_category',
    'SELECT 1'
);
PREPARE supplement_index_stmt FROM @supplement_index_sql;
EXECUTE supplement_index_stmt;
DEALLOCATE PREPARE supplement_index_stmt;

ALTER TABLE new_site
    MODIFY COLUMN main_product_categories JSON NOT NULL,
    MODIFY COLUMN supplement_product_categories JSON NOT NULL,
    MODIFY COLUMN main_product_category TEXT NOT NULL,
    MODIFY COLUMN supplement_product_category TEXT NOT NULL,
    MODIFY COLUMN supplement_product_category_key TEXT NOT NULL;
