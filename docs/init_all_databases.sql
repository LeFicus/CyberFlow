-- CyberFlow Docker 初始化脚本
CREATE DATABASE IF NOT EXISTS cyberflow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS scraped_data DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================================
-- cyberflow 库
-- ============================================================
USE cyberflow;

-- 任务历史表
CREATE TABLE IF NOT EXISTS task_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         VARCHAR(64) NOT NULL UNIQUE,
    type            VARCHAR(30) NOT NULL COMMENT 'site_crawl / order_crawl / product_crawl / site_index',
    trigger_type    VARCHAR(20) NOT NULL COMMENT 'cron / manual',
    triggered_by    VARCHAR(64),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    cursor_before   VARCHAR(255),
    cursor_after    VARCHAR(255),
    rows_affected   INT DEFAULT 0,
    error_msg       TEXT,
    duration_ms     BIGINT,
    started_at      DATETIME,
    finished_at     DATETIME,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 增量游标表
CREATE TABLE IF NOT EXISTS crawl_cursor (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    cursor_key   VARCHAR(100) NOT NULL UNIQUE,
    cursor_value VARCHAR(255) NOT NULL,
    last_sync_at DATETIME NOT NULL,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 选择器模板库
CREATE TABLE IF NOT EXISTS selector_template (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                      VARCHAR(100) NOT NULL,
    platform                  VARCHAR(20) NOT NULL,
    title_selector            VARCHAR(500),
    price_selector            VARCHAR(500),
    price_regex               VARCHAR(200),
    description_selector      VARCHAR(500),
    images_selector           VARCHAR(500),
    currency                  VARCHAR(10) DEFAULT 'USD',
    breadcrumb_links_selector VARCHAR(500),
    breadcrumb_last_selector  VARCHAR(500),
    site_map_selector         VARCHAR(500),
    is_system                 TINYINT DEFAULT 0,
    created_at                DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 商品爬取站点注册
CREATE TABLE IF NOT EXISTS crawl_site_config (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain     VARCHAR(255) NOT NULL,
    type       VARCHAR(20) NOT NULL,
    category   VARCHAR(100) DEFAULT '未知分类',
    status     VARCHAR(20) DEFAULT 'active',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 站点↔模板多对多关联
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

-- 预置模板数据
INSERT INTO selector_template (name, platform, title_selector, price_selector, price_regex, description_selector, images_selector, currency, breadcrumb_links_selector, breadcrumb_last_selector, site_map_selector, is_system) VALUES
('WooCommerce Default', 'woo',
 '//h1[@class=\'product_title entry-title\']/text() | //h1[contains(@class, \'product-title\')]/text() | //header//h1/text()',
 '//p[@class=\'price\']//bdi/text() | //meta[@itemprop=\'price\']/@content',
 '[\\d.,]+',
 '//div[contains(@class, \'woocommerce-product-details__short-description\')]//text() | //div[@id=\'tab-description\']//text()',
 '//div[contains(@class, \'woocommerce-product-gallery__image\')]/a/@href | //meta[@property=\'og:image\']/@content',
 'USD',
 '//nav[contains(@class, \'woocommerce-breadcrumb\')]//a//text() | //div[contains(@class, \'breadcrumbs\')]//a//text()',
 '//nav[contains(@class, \'woocommerce-breadcrumb\')]//a[last()]//text()',
 '//*[local-name()=\'sitemap\']/*[local-name()=\'loc\'][contains(text(), \'product-sitemap\')]/text()', 1)
ON DUPLICATE KEY UPDATE name=name;

INSERT INTO selector_template (name, platform, title_selector, price_selector, price_regex, description_selector, images_selector, currency, breadcrumb_links_selector, breadcrumb_last_selector, site_map_selector, is_system) VALUES
('Magnolia Theme', 'woo',
 '//h1/text()',
 '//div[contains(@class,\'prices\')]//div//div//span//span/@content',
 '[\\d.,]+',
 '//div[contains(@class, \'card-body collapsible-body pdp-feature-body\')]/text()',
 '//meta[@property=\'og:image\']/@content',
 'USD',
 '//ol[contains(@class, \'breadcrumb\')]//a/text()',
 '//ol[contains(@class, \'breadcrumb\')]//span/text()',
 '//*[local-name()=\'sitemap\']/*[local-name()=\'loc\'][contains(text(), \'/sitemap_products_\')]/text()', 1)
ON DUPLICATE KEY UPDATE name=name;

INSERT INTO selector_template (name, platform, is_system) VALUES ('Shopify Default', 'shopify', 1)
ON DUPLICATE KEY UPDATE name=name;

-- 初始化游标
INSERT IGNORE INTO crawl_cursor (cursor_key, cursor_value, last_sync_at) VALUES
('site_crawler', NOW(), NOW()),
('site_index_crawler', NOW(), NOW()),
('order_crawler', '0', NOW());

-- ============================================================
-- scraped_data 库
-- ============================================================
USE scraped_data;

CREATE TABLE IF NOT EXISTS ecommerce_products (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku             VARCHAR(255) NOT NULL UNIQUE,
    name            VARCHAR(500),
    description     TEXT,
    regular_price   DECIMAL(10, 2),
    categories      VARCHAR(500),
    images          TEXT,
    cf_opingts      VARCHAR(500),
    custom_category VARCHAR(100),
    source_domain   VARCHAR(255),
    language        VARCHAR(10) DEFAULT 'en',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
