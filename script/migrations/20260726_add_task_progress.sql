-- Add task progress fields for existing CyberFlow databases.
USE cyberflow;
SET NAMES utf8mb4;

SET @progress_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_history'
      AND COLUMN_NAME = 'progress'
);
SET @progress_sql = IF(
    @progress_exists = 0,
    'ALTER TABLE task_history ADD COLUMN progress TINYINT NOT NULL DEFAULT 0 AFTER status',
    'SELECT 1'
);
PREPARE progress_stmt FROM @progress_sql;
EXECUTE progress_stmt;
DEALLOCATE PREPARE progress_stmt;

SET @progress_message_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_history'
      AND COLUMN_NAME = 'progress_message'
);
SET @progress_message_sql = IF(
    @progress_message_exists = 0,
    'ALTER TABLE task_history ADD COLUMN progress_message VARCHAR(255) NULL AFTER progress',
    'SELECT 1'
);
PREPARE progress_message_stmt FROM @progress_message_sql;
EXECUTE progress_message_stmt;
DEALLOCATE PREPARE progress_message_stmt;
