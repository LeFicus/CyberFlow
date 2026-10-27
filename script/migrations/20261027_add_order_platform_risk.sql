SET @risk_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders' AND COLUMN_NAME='is_fraud') = 0,
    'ALTER TABLE orders ADD COLUMN is_fraud TINYINT NULL COMMENT "支付平台风控标记：1风控，0非风控，NULL未同步" AFTER is_valid',
    'SELECT 1'
);
PREPARE risk_column_stmt FROM @risk_column_sql;
EXECUTE risk_column_stmt;
DEALLOCATE PREPARE risk_column_stmt;
