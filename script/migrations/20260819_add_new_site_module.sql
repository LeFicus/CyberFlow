-- AI-assisted new-site creation and management.

CREATE TABLE IF NOT EXISTS new_site (
    id                                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain                              VARCHAR(255) NOT NULL,
    custom_category                    VARCHAR(255) NOT NULL,
    main_product_categories            JSON NOT NULL,
    supplement_product_categories      JSON NOT NULL,
    main_product_category              TEXT NOT NULL,
    supplement_product_category        TEXT NOT NULL,
    supplement_product_category_key    TEXT NOT NULL,
    source_domains                     JSON NOT NULL,
    site_title                         VARCHAR(255) NOT NULL,
    tag_line                           VARCHAR(500) NOT NULL,
    status                              VARCHAR(32) NOT NULL DEFAULT 'pending_review',
    domain_check_status                VARCHAR(32) NOT NULL DEFAULT 'available',
    domain_check_provider              VARCHAR(64) NOT NULL DEFAULT 'rdap',
    generation_attempts                INT NOT NULL DEFAULT 1,
    created_by                         BIGINT,
    created_at                         DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at                         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_new_site_domain (domain),
    INDEX idx_new_site_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order, status)
VALUES
    (5, 0, '站点建设', 0, NULL, '/new-site', NULL, 'Shop', 4, 1),
    (63, 5, '新站点管理', 1, 'newsite:list', '/new-site', 'newsite/NewSiteList', 'Plus', 1, 1),
    (64, 63, '创建新站点', 2, 'newsite:create', NULL, NULL, NULL, 1, 1),
    (65, 63, '修改站点状态', 2, 'newsite:status', NULL, NULL, NULL, 2, 1),
    (66, 63, '配置 AI 服务', 2, 'newsite:config', NULL, NULL, NULL, 3, 1)
ON DUPLICATE KEY UPDATE
    parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type),
    perms=VALUES(perms), path=VALUES(path), component=VALUES(component), icon=VALUES(icon),
    sort_order=VALUES(sort_order), status=VALUES(status);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, menu_id FROM (
    SELECT 1 AS role_id, 63 AS menu_id UNION ALL SELECT 1, 64 UNION ALL SELECT 1, 65 UNION ALL SELECT 1, 66
    UNION ALL SELECT 2, 63 UNION ALL SELECT 2, 64 UNION ALL SELECT 2, 65 UNION ALL SELECT 2, 66
) permissions;
