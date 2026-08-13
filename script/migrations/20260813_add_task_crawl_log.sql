-- Store the complete combined stdout/stderr stream for crawler tasks.
SET @crawl_log_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'task_history'
      AND COLUMN_NAME = 'crawl_log'
);
SET @crawl_log_sql = IF(
    @crawl_log_exists = 0,
    'ALTER TABLE task_history ADD COLUMN crawl_log LONGTEXT NULL AFTER error_msg',
    'SELECT 1'
);
PREPARE crawl_log_stmt FROM @crawl_log_sql;
EXECUTE crawl_log_stmt;
DEALLOCATE PREPARE crawl_log_stmt;
