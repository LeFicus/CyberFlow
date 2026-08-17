-- Enable the three automatic crawler jobs and align them to the fixed schedule.
USE cyberflow;
SET NAMES utf8mb4;

INSERT INTO crawler_schedule_config (task_type, cron_expression, enabled)
VALUES
    ('site_crawl', '0 0 */6 * * ?', 1),
    ('site_index', '0 0 0 * * ?', 1),
    ('order_crawl', '0 0 */6 * * ?', 1)
ON DUPLICATE KEY UPDATE
    cron_expression = VALUES(cron_expression),
    enabled = VALUES(enabled);
