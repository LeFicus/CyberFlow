USE scraped_data;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS migrate_product_site_identity;
DELIMITER $$
CREATE PROCEDURE migrate_product_site_identity()
BEGIN
    DECLARE conflicts BIGINT DEFAULT 0;
    DECLARE existing_index BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO conflicts
    FROM (
        SELECT LOWER(COALESCE(NULLIF(TRIM(source_domain), ''), 'legacy-unknown')) AS domain_key,
               TRIM(sku) AS sku_key
        FROM ecommerce_products
        GROUP BY domain_key, sku_key
        HAVING COUNT(*) > 1
    ) AS duplicate_identities;
    IF conflicts > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Conflicting normalized domain/SKU identities; export and resolve before migration';
    END IF;

    SELECT COUNT(*) INTO existing_index
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ecommerce_products'
      AND INDEX_NAME = 'uk_product_domain_sku';
    IF existing_index = 0 THEN
        ALTER TABLE ecommerce_products ADD UNIQUE KEY uk_product_domain_sku (source_domain, sku);
    END IF;

    SELECT GROUP_CONCAT(CONCAT('DROP INDEX `', REPLACE(INDEX_NAME, '`', '``'), '`') SEPARATOR ', ')
    INTO @old_product_indexes
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ecommerce_products'
          AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY'
        GROUP BY INDEX_NAME
        HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) IN ('sku', 'dedupe_key')
    ) AS obsolete_indexes;
    IF @old_product_indexes IS NOT NULL THEN
        SET @product_index_sql = CONCAT('ALTER TABLE ecommerce_products ', @old_product_indexes);
        PREPARE product_index_stmt FROM @product_index_sql;
        EXECUTE product_index_stmt;
        DEALLOCATE PREPARE product_index_stmt;
    END IF;

    UPDATE ecommerce_products
    SET source_domain = LOWER(COALESCE(NULLIF(TRIM(source_domain), ''), 'legacy-unknown')),
        sku = TRIM(sku);

    SELECT CONCAT('ALTER TABLE ecommerce_products MODIFY source_domain VARCHAR(255) CHARACTER SET ',
                  CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, ' NOT NULL')
    INTO @product_domain_sql
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ecommerce_products'
      AND COLUMN_NAME = 'source_domain';
    PREPARE product_domain_stmt FROM @product_domain_sql;
    EXECUTE product_domain_stmt;
    DEALLOCATE PREPARE product_domain_stmt;

    SELECT COUNT(*) INTO existing_index
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ecommerce_products'
      AND INDEX_NAME = 'idx_product_dedupe';
    IF existing_index = 0 THEN
        ALTER TABLE ecommerce_products ADD INDEX idx_product_dedupe (dedupe_key);
    END IF;
END$$
DELIMITER ;

CALL migrate_product_site_identity();
DROP PROCEDURE migrate_product_site_identity;
