-- Move crawler output out of task_history.crawl_log. New writes are immutable
-- chunks, so appending no longer copies and rewrites an ever-growing LONGTEXT.
CREATE TABLE IF NOT EXISTS task_crawl_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         VARCHAR(64) NOT NULL,
    content         MEDIUMTEXT NOT NULL,
    content_length  INT UNSIGNED NOT NULL COMMENT 'Unicode character count for incremental offsets',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_task_crawl_log_task_id_id (task_id, id),
    CONSTRAINT fk_task_crawl_log_task
        FOREIGN KEY (task_id) REFERENCES task_history(task_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Append-only crawler log chunks';

-- Preserve logs produced before this migration. The NOT EXISTS guard makes a
-- partially retried migration idempotent.
INSERT INTO task_crawl_log (task_id, content, content_length, created_at)
SELECT legacy.task_id, legacy.migrated_content, CHAR_LENGTH(legacy.migrated_content),
       legacy.log_created_at
FROM (
    SELECT h.task_id,
           CASE WHEN CHAR_LENGTH(h.crawl_log) > 2000000
                THEN CONCAT('[CyberFlow] 旧版日志超过 2,000,000 字符，迁移时仅保留末尾内容。\n',
                            RIGHT(h.crawl_log, 2000000))
                ELSE h.crawl_log END AS migrated_content,
           COALESCE(h.started_at, h.created_at, NOW(3)) AS log_created_at
    FROM task_history h
    WHERE h.crawl_log IS NOT NULL AND h.crawl_log <> ''
) legacy
WHERE NOT EXISTS (
      SELECT 1 FROM task_crawl_log l WHERE l.task_id = legacy.task_id
  );

-- Keep the column for rollback compatibility but reclaim its payload and stop
-- serving it from the hot task_history row.
UPDATE task_history h
SET h.crawl_log = NULL
WHERE h.crawl_log IS NOT NULL
  AND EXISTS (SELECT 1 FROM task_crawl_log l WHERE l.task_id = h.task_id);
