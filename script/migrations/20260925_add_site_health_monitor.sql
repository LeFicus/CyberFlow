CREATE TABLE IF NOT EXISTS site_health_status (
    site_domain           VARCHAR(255) PRIMARY KEY,
    status                VARCHAR(16) NOT NULL,
    reason                VARCHAR(500) NOT NULL,
    http_status           INT,
    final_url             VARCHAR(1000),
    server_name           VARCHAR(255),
    server_ip             VARCHAR(45),
    admin_name            VARCHAR(100),
    user_group            VARCHAR(32),
    latency_ms            BIGINT NOT NULL DEFAULT 0,
    consecutive_failures  INT NOT NULL DEFAULT 0,
    failure_since         DATETIME,
    checked_at            DATETIME NOT NULL,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_health_status_checked (status, checked_at),
    INDEX idx_health_group_status (user_group, status),
    INDEX idx_health_server (server_ip, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点最新健康检查状态';

INSERT INTO crawler_schedule_config(task_type, cron_expression, enabled)
VALUES ('site_health', '0 0/30 * * * ?', 1)
ON DUPLICATE KEY UPDATE task_type=VALUES(task_type);

INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order, status)
VALUES
    (76, 2, '站点健康检查', 1, 'crawler:health:view', '/crawler/site-health', 'crawler/SiteHealth', 'Monitor', 3, 1),
    (77, 76, '执行站点健康检查', 2, 'crawler:health:trigger', NULL, NULL, NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type),
    perms=VALUES(perms), path=VALUES(path), component=VALUES(component), icon=VALUES(icon),
    sort_order=VALUES(sort_order), status=VALUES(status);

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_code IN ('ROLE_ADMIN', 'ROLE_OPERATOR') AND m.id IN (76, 77);
