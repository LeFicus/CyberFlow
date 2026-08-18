SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Keep existing installations compatible with the row-level data scope.
SET @data_owner_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'data_owner'
);
SET @data_owner_sql = IF(
    @data_owner_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN data_owner VARCHAR(1000) COMMENT ''外部站点/订单管理员名称列表，用逗号分隔''',
    'SELECT 1'
);
PREPARE data_owner_stmt FROM @data_owner_sql;
EXECUTE data_owner_stmt;
DEALLOCATE PREPARE data_owner_stmt;

CREATE DATABASE IF NOT EXISTS scraped_data
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crawler_runtime_config (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_group  VARCHAR(50) NOT NULL,
    config_key    VARCHAR(100) NOT NULL,
    config_value  TEXT,
    is_sensitive  TINYINT NOT NULL DEFAULT 0,
    remark        VARCHAR(255),
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_key (config_group, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crawler_schedule_config (
    task_type         VARCHAR(30) PRIMARY KEY,
    cron_expression   VARCHAR(100) NOT NULL,
    enabled           TINYINT NOT NULL DEFAULT 1,
    last_triggered_at DATETIME,
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS selector_template (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                      VARCHAR(100) NOT NULL,
    platform                  VARCHAR(32) NOT NULL,
    title_selector            VARCHAR(500),
    price_selector            VARCHAR(500),
    price_regex               VARCHAR(500),
    description_selector      VARCHAR(500),
    images_selector           VARCHAR(500),
    currency                  VARCHAR(10) DEFAULT 'USD',
    breadcrumb_links_selector VARCHAR(500),
    breadcrumb_last_selector  VARCHAR(500),
    site_map_selector         VARCHAR(500),
    is_system                 TINYINT NOT NULL DEFAULT 0,
    created_at                DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crawl_site_config (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain     VARCHAR(255) NOT NULL,
    type       VARCHAR(32) NOT NULL DEFAULT 'shopify',
    category   VARCHAR(100),
    status     VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS site_template_mapping (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_config_id  BIGINT NOT NULL,
    template_id     BIGINT NOT NULL,
    extra_selectors JSON,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (site_config_id) REFERENCES crawl_site_config(id) ON DELETE CASCADE,
    FOREIGN KEY (template_id) REFERENCES selector_template(id) ON DELETE CASCADE,
    UNIQUE KEY uk_site_template (site_config_id, template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS site_info (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    username         VARCHAR(100),
    site_domain      VARCHAR(255) NOT NULL,
    admin_name       VARCHAR(100),
    user_group       VARCHAR(1),
    theme_name       VARCHAR(100),
    product_category VARCHAR(100),
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_domain (site_domain),
    INDEX idx_site_user_group (user_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
    id                  BIGINT NOT NULL,
    amount              DECIMAL(10, 2),
    currency            VARCHAR(10),
    create_time         DATETIME,
    product_host        VARCHAR(255),
    pay_status_text     VARCHAR(50),
    customer_ip_country VARCHAR(100),
    shipping_email      VARCHAR(255),
    admin_name          VARCHAR(100),
    user_group          VARCHAR(1) NOT NULL,
    theme_name          VARCHAR(100),
    product_category    VARCHAR(100),
    product_info        JSON,
    PRIMARY KEY (user_group, id),
    INDEX idx_create_time (create_time),
    INDEX idx_product_host (product_host),
    INDEX idx_order_user_group (user_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS site_indexing_history (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_domain   VARCHAR(255) NOT NULL,
    index_count   INT NOT NULL DEFAULT 0,
    product_count INT NOT NULL DEFAULT 0,
    recorded_at   DATETIME NOT NULL,
    INDEX idx_recorded_at (recorded_at),
    INDEX idx_site_domain (site_domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scraped_data.ecommerce_products (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku             VARCHAR(255) NOT NULL,
    name            VARCHAR(500),
    description     TEXT,
    regular_price   DECIMAL(10, 2),
    categories      VARCHAR(500),
    images          TEXT,
    cf_opingts      TEXT,
    custom_category VARCHAR(100),
    source_domain   VARCHAR(255),
    language        VARCHAR(10) DEFAULT 'en',
    dedupe_key      VARCHAR(768),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_created_id (created_at, id),
    INDEX idx_product_domain_created (source_domain, created_at, id),
    INDEX idx_product_category_created (custom_category, created_at, id),
    INDEX idx_product_name_prefix (name(100)),
    UNIQUE KEY uk_sku (sku),
    UNIQUE KEY uk_product_dedupe (dedupe_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO crawler_schedule_config (task_type, cron_expression, enabled) VALUES
    ('site_crawl', '0 0 2 * * ?', 1),
    ('site_index', '0 30 2 * * ?', 1),
    ('order_crawl', '0 0 3 * * ?', 1);

INSERT INTO selector_template (
    name, platform, title_selector, price_selector, price_regex,
    description_selector, images_selector, currency,
    breadcrumb_links_selector, breadcrumb_last_selector, site_map_selector, is_system
) SELECT
    'WooCommerce Default', 'woocommerce',
    '//h1[contains(@class,''product_title'')]/text() | //h1[contains(@class,''product-title'')]/text() | //main//h1/text() | //meta[@property=''og:title'']/@content',
    '//p[contains(@class,''price'')]//*[contains(@class,''amount'')]/text() | //*[@itemprop=''price'']/@content | //meta[@property=''product:price:amount'']/@content',
    '[\\d.,]+',
    '//*[contains(@class,''product'') and contains(@class,''description'')]//text() | //*[@itemprop=''description'']//text() | //meta[@property=''og:description'']/@content',
    '//*[contains(@class,''product'') and contains(@class,''gallery'')]//img/@data-large_image | //*[contains(@class,''product'') and contains(@class,''gallery'')]//img/@src | //meta[@property=''og:image'']/@content',
    'USD',
    '//nav[contains(@class,''breadcrumb'')]//a//text() | //*[contains(@class,''breadcrumbs'')]//a//text() | //ul[contains(@class,''breadcrumb'')]//a//text()',
    '//nav[contains(@class,''breadcrumb'')]//*[last()]//text() | //*[contains(@class,''breadcrumbs'')]//*[last()]//text()',
    '//*[local-name()=''sitemap'']/*[local-name()=''loc'']/text()',
    1
WHERE NOT EXISTS (
    SELECT 1 FROM selector_template
    WHERE name = 'WooCommerce Default' OR (platform = 'woocommerce' AND is_system = 1)
);

DELETE FROM crawler_runtime_config
WHERE config_group = 'paymentApi' AND config_key = 'tenantId';

INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order, status)
VALUES
    (15, 14, '删除商品', 2, 'dashboard:product:delete', NULL, NULL, NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type),
    perms=VALUES(perms), status=VALUES(status);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 15), (2, 15);

INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order, status)
VALUES
    (35, 2, '收入参数', 1, 'crawler:revenue:view', '/crawler/revenue-config', 'crawler/RevenueConfig', 'Money', 6, 1),
    (36, 35, '修改收入参数', 2, 'crawler:revenue:update', NULL, NULL, NULL, 1, 1)
ON DUPLICATE KEY UPDATE
    parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type),
    perms=VALUES(perms), path=VALUES(path), component=VALUES(component), icon=VALUES(icon),
    sort_order=VALUES(sort_order), status=VALUES(status);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 35), (1, 36), (2, 35), (2, 36);

-- 系统管理操作权限。旧库可能只有页面查看权限，补齐后端 CRUD 接口所需权限。
INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order, status)
VALUES
    (52, 31, '新增用户', 2, 'system:user:create', NULL, NULL, NULL, 1, 1),
    (53, 31, '修改用户', 2, 'system:user:update', NULL, NULL, NULL, 2, 1),
    (54, 31, '删除用户', 2, 'system:user:delete', NULL, NULL, NULL, 3, 1),
    (55, 31, '分配用户角色', 2, 'system:user:assign', NULL, NULL, NULL, 4, 1),
    (56, 32, '新增角色', 2, 'system:role:create', NULL, NULL, NULL, 1, 1),
    (57, 32, '修改角色', 2, 'system:role:update', NULL, NULL, NULL, 2, 1),
    (58, 32, '删除角色', 2, 'system:role:delete', NULL, NULL, NULL, 3, 1),
    (59, 32, '分配角色菜单', 2, 'system:role:assign', NULL, NULL, NULL, 4, 1),
    (60, 33, '新增菜单', 2, 'system:menu:create', NULL, NULL, NULL, 1, 1),
    (61, 33, '修改菜单', 2, 'system:menu:update', NULL, NULL, NULL, 2, 1),
    (62, 33, '删除菜单', 2, 'system:menu:delete', NULL, NULL, NULL, 3, 1)
ON DUPLICATE KEY UPDATE
    parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type),
    perms=VALUES(perms), status=VALUES(status);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 52 AND 62;
