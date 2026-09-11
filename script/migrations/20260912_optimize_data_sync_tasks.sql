-- Speed up the data-sync console, active-task guards, and today's status counters.
USE cyberflow;

SET @task_scope_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'task_history'
      AND INDEX_NAME = 'idx_task_type_status_created'
);
SET @task_scope_index_sql = IF(
    @task_scope_index_exists = 0,
    'ALTER TABLE task_history ADD INDEX idx_task_type_status_created (type, status, created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @task_scope_index_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @task_today_index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'task_history'
      AND INDEX_NAME = 'idx_task_status_finished'
);
SET @task_today_index_sql = IF(
    @task_today_index_exists = 0,
    'ALTER TABLE task_history ADD INDEX idx_task_status_finished (status, finished_at)',
    'SELECT 1'
);
PREPARE stmt FROM @task_today_index_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
