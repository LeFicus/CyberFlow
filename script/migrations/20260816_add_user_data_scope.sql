SET @data_owner_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'data_owner'
);
SET @data_owner_sql = IF(
    @data_owner_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN data_owner VARCHAR(100) COMMENT ''外部数据中的管理员名称，用于普通用户数据隔离''',
    'SELECT 1'
);
PREPARE data_owner_stmt FROM @data_owner_sql;
EXECUTE data_owner_stmt;
DEALLOCATE PREPARE data_owner_stmt;
