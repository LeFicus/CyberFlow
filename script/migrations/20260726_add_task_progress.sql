-- Run once on existing CyberFlow databases before deploying the progress UI.
ALTER TABLE task_history ADD COLUMN IF NOT EXISTS progress TINYINT NOT NULL DEFAULT 0 AFTER status;
ALTER TABLE task_history ADD COLUMN IF NOT EXISTS progress_message VARCHAR(255) NULL AFTER progress;
