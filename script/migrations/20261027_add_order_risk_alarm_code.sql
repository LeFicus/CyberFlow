SET @alarm_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='orders' AND COLUMN_NAME='is_alarm') = 0,
    'ALTER TABLE orders ADD COLUMN is_alarm INT NULL COMMENT "支付平台原始风控返回码；0正常，非0风控" AFTER is_fraud',
    'SELECT 1'
);
PREPARE alarm_column_stmt FROM @alarm_column_sql;
EXECUTE alarm_column_stmt;
DEALLOCATE PREPARE alarm_column_stmt;
