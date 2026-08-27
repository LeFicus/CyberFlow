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
    product_role VARCHAR(20) NOT NULL DEFAULT 'main' COMMENT 'main-主产品 supplement-补充产品',
    category   VARCHAR(100),
    status     VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    builder_username VARCHAR(100),
    site_domain      VARCHAR(255) NOT NULL,
    server_name      VARCHAR(255),
    server_ip        VARCHAR(45),
    admin_name       VARCHAR(100),
    user_group       VARCHAR(1),
    theme_name       VARCHAR(100),
    product_category VARCHAR(100),
    last_submitted_at DATETIME,
    domain_applied_at DATETIME,
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_domain (site_domain),
    INDEX idx_site_user_group (user_group),
    INDEX idx_site_builder_username (builder_username),
    INDEX idx_site_domain_applied_at (domain_applied_at),
    INDEX idx_site_server (server_name, server_ip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
    id                  BIGINT NOT NULL,
    amount              DECIMAL(10, 2),
    currency            VARCHAR(10),
    create_time         DATETIME,
    product_host        VARCHAR(255),
    pay_status_text     VARCHAR(50),
    card_number         VARCHAR(100),
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
    server_name   VARCHAR(255),
    server_ip     VARCHAR(45),
    last_submitted_at DATETIME,
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
    original_price_usd DECIMAL(10, 2),
    image_usable    TINYINT NOT NULL DEFAULT 1,
    categories      VARCHAR(500),
    images          TEXT,
    cf_opingts      TEXT,
    custom_category VARCHAR(100),
    product_role    VARCHAR(20) NOT NULL DEFAULT 'main' COMMENT 'main-主产品 supplement-补充产品',
    source_domain   VARCHAR(255) NOT NULL,
    language        VARCHAR(10) DEFAULT 'en',
    dedupe_key      VARCHAR(768),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_created_id (created_at, id),
    INDEX idx_product_domain_created (source_domain, created_at, id),
    INDEX idx_product_category_created (custom_category, created_at, id),
    INDEX idx_product_role_created (product_role, created_at, id),
    INDEX idx_product_name_prefix (name(100)),
    INDEX idx_product_domain_id (source_domain, id),
    INDEX idx_product_category_id (custom_category, id),
    INDEX idx_product_role_id (product_role, id),
    INDEX idx_product_sku_id (sku(100), id),
    UNIQUE KEY uk_product_domain_sku (source_domain, sku),
    INDEX idx_product_dedupe (dedupe_key)
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

INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order, status)
VALUES
    (5, 0, '站点建设', 0, NULL, '/new-site', NULL, 'Shop', 4, 1),
    (63, 5, '新站点管理', 1, 'newsite:list', '/new-site', 'newsite/NewSiteList', 'Plus', 1, 1),
    (64, 63, '创建新站点', 2, 'newsite:create', NULL, NULL, NULL, 1, 1),
    (65, 63, '修改站点状态', 2, 'newsite:status', NULL, NULL, NULL, 2, 1),
    (66, 63, '配置 AI 服务', 2, 'newsite:config', NULL, NULL, NULL, 3, 1),
    (67, 63, '删除新站点', 2, 'newsite:delete', NULL, NULL, NULL, 4, 1)
ON DUPLICATE KEY UPDATE
    parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type),
    perms=VALUES(perms), path=VALUES(path), component=VALUES(component), icon=VALUES(icon),
    sort_order=VALUES(sort_order), status=VALUES(status);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 63), (1, 64), (1, 65), (1, 66), (1, 67), (2, 63), (2, 64), (2, 65), (2, 66), (2, 67);

-- Editable shared catalog. Seed exactly once: deleted categories must not reappear at startup.
CREATE TABLE IF NOT EXISTS custom_category (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, parent_id BIGINT NOT NULL DEFAULT 0,
 name VARCHAR(100) NOT NULL, enabled TINYINT NOT NULL DEFAULT 1,
 sort_order INT NOT NULL DEFAULT 0, UNIQUE KEY uk_custom_category_name(name),
 KEY idx_custom_category_parent(parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS custom_category_seed (id INT PRIMARY KEY);
INSERT IGNORE INTO custom_category(id,parent_id,name,sort_order)
SELECT seed.id,seed.parent_id,seed.name,seed.id FROM (
SELECT 1 AS id,0 AS parent_id,'书籍' AS name
UNION ALL
SELECT 2 AS id,0 AS parent_id,'五金' AS name
UNION ALL
SELECT 3 AS id,0 AS parent_id,'五金/硬件' AS name
UNION ALL
SELECT 4 AS id,3 AS parent_id,'五金工具' AS name
UNION ALL
SELECT 5 AS id,3 AS parent_id,'五金泵' AS name
UNION ALL
SELECT 6 AS id,3 AS parent_id,'五金配件' AS name
UNION ALL
SELECT 7 AS id,3 AS parent_id,'供暖、通风及空调' AS name
UNION ALL
SELECT 8 AS id,3 AS parent_id,'储罐' AS name
UNION ALL
SELECT 9 AS id,3 AS parent_id,'小型发动机' AS name
UNION ALL
SELECT 10 AS id,3 AS parent_id,'工具' AS name
UNION ALL
SELECT 11 AS id,3 AS parent_id,'工具配件' AS name
UNION ALL
SELECT 12 AS id,3 AS parent_id,'建材' AS name
UNION ALL
SELECT 13 AS id,3 AS parent_id,'建筑耗材' AS name
UNION ALL
SELECT 14 AS id,3 AS parent_id,'栅栏/围栏' AS name
UNION ALL
SELECT 15 AS id,3 AS parent_id,'燃料' AS name
UNION ALL
SELECT 16 AS id,3 AS parent_id,'燃料罐/箱' AS name
UNION ALL
SELECT 17 AS id,3 AS parent_id,'电动/电气用品' AS name
UNION ALL
SELECT 18 AS id,3 AS parent_id,'管道' AS name
UNION ALL
SELECT 19 AS id,3 AS parent_id,'锁与钥匙' AS name
UNION ALL
SELECT 20 AS id,0 AS parent_id,'交通工具/汽车/飞机/船舶' AS name
UNION ALL
SELECT 21 AS id,20 AS parent_id,'交通工具' AS name
UNION ALL
SELECT 22 AS id,20 AS parent_id,'交通工具零配件' AS name
UNION ALL
SELECT 23 AS id,0 AS parent_id,'体育用品' AS name
UNION ALL
SELECT 24 AS id,23 AS parent_id,'室内游戏' AS name
UNION ALL
SELECT 25 AS id,23 AS parent_id,'户外休闲' AS name
UNION ALL
SELECT 26 AS id,23 AS parent_id,'田径' AS name
UNION ALL
SELECT 27 AS id,23 AS parent_id,'运动健身' AS name
UNION ALL
SELECT 28 AS id,0 AS parent_id,'办公用品' AS name
UNION ALL
SELECT 29 AS id,28 AS parent_id,'一般办公用品' AS name
UNION ALL
SELECT 30 AS id,28 AS parent_id,'书籍用具' AS name
UNION ALL
SELECT 31 AS id,28 AS parent_id,'办公室/椅子地垫' AS name
UNION ALL
SELECT 32 AS id,28 AS parent_id,'办公室手推车' AS name
UNION ALL
SELECT 33 AS id,28 AS parent_id,'办公文具' AS name
UNION ALL
SELECT 34 AS id,28 AS parent_id,'办公设备' AS name
UNION ALL
SELECT 35 AS id,28 AS parent_id,'包装快递用品' AS name
UNION ALL
SELECT 36 AS id,28 AS parent_id,'名牌' AS name
UNION ALL
SELECT 37 AS id,28 AS parent_id,'文件整理' AS name
UNION ALL
SELECT 38 AS id,28 AS parent_id,'桌垫' AS name
UNION ALL
SELECT 39 AS id,28 AS parent_id,'演示用品' AS name
UNION ALL
SELECT 40 AS id,28 AS parent_id,'笔记本电脑托架' AS name
UNION ALL
SELECT 41 AS id,28 AS parent_id,'纸张处理' AS name
UNION ALL
SELECT 42 AS id,28 AS parent_id,'脉冲热封机' AS name
UNION ALL
SELECT 43 AS id,0 AS parent_id,'动漫' AS name
UNION ALL
SELECT 44 AS id,0 AS parent_id,'动物' AS name
UNION ALL
SELECT 45 AS id,0 AS parent_id,'动物/宠物用品' AS name
UNION ALL
SELECT 46 AS id,45 AS parent_id,'宠物用品' AS name
UNION ALL
SELECT 47 AS id,45 AS parent_id,'活体动物' AS name
UNION ALL
SELECT 48 AS id,0 AS parent_id,'商业/工业' AS name
UNION ALL
SELECT 49 AS id,48 AS parent_id,'农/畜牧/渔业专用设备' AS name
UNION ALL
SELECT 50 AS id,48 AS parent_id,'制造业' AS name
UNION ALL
SELECT 51 AS id,48 AS parent_id,'劳保/防护用品' AS name
UNION ALL
SELECT 52 AS id,48 AS parent_id,'医疗' AS name
UNION ALL
SELECT 53 AS id,48 AS parent_id,'工业仓储' AS name
UNION ALL
SELECT 54 AS id,48 AS parent_id,'工业仓储配件' AS name
UNION ALL
SELECT 55 AS id,48 AS parent_id,'广告与营销' AS name
UNION ALL
SELECT 56 AS id,48 AS parent_id,'建筑用品' AS name
UNION ALL
SELECT 57 AS id,48 AS parent_id,'影视' AS name
UNION ALL
SELECT 58 AS id,48 AS parent_id,'执法' AS name
UNION ALL
SELECT 59 AS id,48 AS parent_id,'材料处理' AS name
UNION ALL
SELECT 60 AS id,48 AS parent_id,'林业与伐木业' AS name
UNION ALL
SELECT 61 AS id,48 AS parent_id,'标识牌' AS name
UNION ALL
SELECT 62 AS id,48 AS parent_id,'清洁车/杂物篮' AS name
UNION ALL
SELECT 63 AS id,48 AS parent_id,'牙科' AS name
UNION ALL
SELECT 64 AS id,48 AS parent_id,'科学与实验' AS name
UNION ALL
SELECT 65 AS id,48 AS parent_id,'穿刺与纹身' AS name
UNION ALL
SELECT 66 AS id,48 AS parent_id,'美容美发业' AS name
UNION ALL
SELECT 67 AS id,48 AS parent_id,'自动化控制组件' AS name
UNION ALL
SELECT 68 AS id,48 AS parent_id,'酒店与宾馆' AS name
UNION ALL
SELECT 69 AS id,48 AS parent_id,'采矿与采石' AS name
UNION ALL
SELECT 70 AS id,48 AS parent_id,'重型机械' AS name
UNION ALL
SELECT 71 AS id,48 AS parent_id,'金融与保险' AS name
UNION ALL
SELECT 72 AS id,48 AS parent_id,'零售业' AS name
UNION ALL
SELECT 73 AS id,48 AS parent_id,'餐饮服务' AS name
UNION ALL
SELECT 74 AS id,0 AS parent_id,'婴幼儿用品' AS name
UNION ALL
SELECT 75 AS id,74 AS parent_id,'哺乳与喂养' AS name
UNION ALL
SELECT 76 AS id,74 AS parent_id,'如厕训练器' AS name
UNION ALL
SELECT 77 AS id,74 AS parent_id,'婴儿出行用品' AS name
UNION ALL
SELECT 78 AS id,74 AS parent_id,'婴儿卫生' AS name
UNION ALL
SELECT 79 AS id,74 AS parent_id,'婴儿安全用品' AS name
UNION ALL
SELECT 80 AS id,74 AS parent_id,'婴儿洗浴用品' AS name
UNION ALL
SELECT 81 AS id,74 AS parent_id,'婴儿玩具/活动设备' AS name
UNION ALL
SELECT 82 AS id,74 AS parent_id,'婴儿礼品套装' AS name
UNION ALL
SELECT 83 AS id,74 AS parent_id,'婴幼儿出行用品配件' AS name
UNION ALL
SELECT 84 AS id,74 AS parent_id,'尿布相关用品' AS name
UNION ALL
SELECT 85 AS id,74 AS parent_id,'襁褓/婴儿包毯' AS name
UNION ALL
SELECT 86 AS id,0 AS parent_id,'媒体' AS name
UNION ALL
SELECT 87 AS id,86 AS parent_id,'DVD 和视频' AS name
UNION ALL
SELECT 88 AS id,86 AS parent_id,'乐谱' AS name
UNION ALL
SELECT 89 AS id,86 AS parent_id,'书' AS name
UNION ALL
SELECT 90 AS id,86 AS parent_id,'产品说明书' AS name
UNION ALL
SELECT 91 AS id,86 AS parent_id,'报纸/杂志' AS name
UNION ALL
SELECT 92 AS id,86 AS parent_id,'木工项目计划' AS name
UNION ALL
SELECT 93 AS id,86 AS parent_id,'音乐' AS name
UNION ALL
SELECT 94 AS id,0 AS parent_id,'宗教/仪式' AS name
UNION ALL
SELECT 95 AS id,94 AS parent_id,'婚庆用品' AS name
UNION ALL
SELECT 96 AS id,94 AS parent_id,'宗教用品' AS name
UNION ALL
SELECT 97 AS id,94 AS parent_id,'纪念仪式用品' AS name
UNION ALL
SELECT 98 AS id,0 AS parent_id,'家具' AS name
UNION ALL
SELECT 99 AS id,98 AS parent_id,'办公家具' AS name
UNION ALL
SELECT 100 AS id,98 AS parent_id,'办公家具配件' AS name
UNION ALL
SELECT 101 AS id,98 AS parent_id,'可移动置物架' AS name
UNION ALL
SELECT 102 AS id,98 AS parent_id,'娱乐中心/电视柜' AS name
UNION ALL
SELECT 103 AS id,98 AS parent_id,'婴幼儿家具' AS name
UNION ALL
SELECT 104 AS id,98 AS parent_id,'家具套装' AS name
UNION ALL
SELECT 105 AS id,98 AS parent_id,'屏风/隔屏' AS name
UNION ALL
SELECT 106 AS id,98 AS parent_id,'床具与配件' AS name
UNION ALL
SELECT 107 AS id,98 AS parent_id,'户外家具' AS name
UNION ALL
SELECT 108 AS id,98 AS parent_id,'户外家具配件' AS name
UNION ALL
SELECT 109 AS id,98 AS parent_id,'房间隔板配件' AS name
UNION ALL
SELECT 110 AS id,98 AS parent_id,'搁架' AS name
UNION ALL
SELECT 111 AS id,98 AS parent_id,'日式床垫/折叠沙发床' AS name
UNION ALL
SELECT 112 AS id,98 AS parent_id,'架子配件' AS name
UNION ALL
SELECT 113 AS id,98 AS parent_id,'柜子/储物' AS name
UNION ALL
SELECT 114 AS id,98 AS parent_id,'桌子' AS name
UNION ALL
SELECT 115 AS id,98 AS parent_id,'桌子配件' AS name
UNION ALL
SELECT 116 AS id,98 AS parent_id,'椅子' AS name
UNION ALL
SELECT 117 AS id,98 AS parent_id,'椅子配件' AS name
UNION ALL
SELECT 118 AS id,98 AS parent_id,'沙发' AS name
UNION ALL
SELECT 119 AS id,98 AS parent_id,'沙发凳' AS name
UNION ALL
SELECT 120 AS id,98 AS parent_id,'沙发配件' AS name
UNION ALL
SELECT 121 AS id,98 AS parent_id,'蒲团/榻榻米底架' AS name
UNION ALL
SELECT 122 AS id,98 AS parent_id,'蒲团垫' AS name
UNION ALL
SELECT 123 AS id,98 AS parent_id,'长椅' AS name
UNION ALL
SELECT 124 AS id,0 AS parent_id,'家居与园艺' AS name
UNION ALL
SELECT 125 AS id,124 AS parent_id,'保险柜/保险箱' AS name
UNION ALL
SELECT 126 AS id,124 AS parent_id,'厨房/餐厅' AS name
UNION ALL
SELECT 127 AS id,124 AS parent_id,'壁炉' AS name
UNION ALL
SELECT 128 AS id,124 AS parent_id,'壁炉与木炉配件' AS name
UNION ALL
SELECT 129 AS id,124 AS parent_id,'家居用品' AS name
UNION ALL
SELECT 130 AS id,124 AS parent_id,'家用电器' AS name
UNION ALL
SELECT 131 AS id,124 AS parent_id,'家电配件' AS name
UNION ALL
SELECT 132 AS id,124 AS parent_id,'床上用品' AS name
UNION ALL
SELECT 133 AS id,124 AS parent_id,'应急准备' AS name
UNION ALL
SELECT 134 AS id,124 AS parent_id,'柴火炉' AS name
UNION ALL
SELECT 135 AS id,124 AS parent_id,'植物' AS name
UNION ALL
SELECT 136 AS id,124 AS parent_id,'泳池/水疗' AS name
UNION ALL
SELECT 137 AS id,124 AS parent_id,'浴室配件' AS name
UNION ALL
SELECT 138 AS id,124 AS parent_id,'照明设备' AS name
UNION ALL
SELECT 139 AS id,124 AS parent_id,'照明配件' AS name
UNION ALL
SELECT 140 AS id,124 AS parent_id,'草坪与花园' AS name
UNION ALL
SELECT 141 AS id,124 AS parent_id,'装饰' AS name
UNION ALL
SELECT 142 AS id,124 AS parent_id,'防洪、消防与可燃气体安全设备' AS name
UNION ALL
SELECT 143 AS id,124 AS parent_id,'雨伞/遮阳伞' AS name
UNION ALL
SELECT 144 AS id,124 AS parent_id,'雨伞套/盒' AS name
UNION ALL
SELECT 145 AS id,0 AS parent_id,'户外用品' AS name
UNION ALL
SELECT 146 AS id,0 AS parent_id,'机械' AS name
UNION ALL
SELECT 147 AS id,0 AS parent_id,'玩具/游戏' AS name
UNION ALL
SELECT 148 AS id,147 AS parent_id,'室外玩具设备' AS name
UNION ALL
SELECT 149 AS id,147 AS parent_id,'游戏' AS name
UNION ALL
SELECT 150 AS id,147 AS parent_id,'游戏计时器' AS name
UNION ALL
SELECT 151 AS id,147 AS parent_id,'玩具' AS name
UNION ALL
SELECT 152 AS id,147 AS parent_id,'益智玩具/游戏' AS name
UNION ALL
SELECT 153 AS id,0 AS parent_id,'电子产品' AS name
UNION ALL
SELECT 154 AS id,153 AS parent_id,'GPS 导航系统' AS name
UNION ALL
SELECT 155 AS id,153 AS parent_id,'GPS 配件' AS name
UNION ALL
SELECT 156 AS id,153 AS parent_id,'GPS跟踪设备' AS name
UNION ALL
SELECT 157 AS id,153 AS parent_id,'大型游戏机/街机' AS name
UNION ALL
SELECT 158 AS id,153 AS parent_id,'手机配件' AS name
UNION ALL
SELECT 159 AS id,153 AS parent_id,'打印/复印/扫描/传真' AS name
UNION ALL
SELECT 160 AS id,153 AS parent_id,'收费装置' AS name
UNION ALL
SELECT 161 AS id,153 AS parent_id,'测速雷达' AS name
UNION ALL
SELECT 162 AS id,153 AS parent_id,'电子游戏机' AS name
UNION ALL
SELECT 163 AS id,153 AS parent_id,'电子游戏机配件' AS name
UNION ALL
SELECT 164 AS id,153 AS parent_id,'电子配件' AS name
UNION ALL
SELECT 165 AS id,153 AS parent_id,'电路板和组件' AS name
UNION ALL
SELECT 166 AS id,153 AS parent_id,'组件' AS name
UNION ALL
SELECT 167 AS id,153 AS parent_id,'网络' AS name
UNION ALL
SELECT 168 AS id,153 AS parent_id,'航海电子设备' AS name
UNION ALL
SELECT 169 AS id,153 AS parent_id,'视频' AS name
UNION ALL
SELECT 170 AS id,153 AS parent_id,'计算机' AS name
UNION ALL
SELECT 171 AS id,153 AS parent_id,'通讯' AS name
UNION ALL
SELECT 172 AS id,153 AS parent_id,'雷达探测器' AS name
UNION ALL
SELECT 173 AS id,153 AS parent_id,'音频' AS name
UNION ALL
SELECT 174 AS id,0 AS parent_id,'相机与光学器件' AS name
UNION ALL
SELECT 175 AS id,174 AS parent_id,'光学器件' AS name
UNION ALL
SELECT 176 AS id,174 AS parent_id,'照片冲印/摄影棚器材' AS name
UNION ALL
SELECT 177 AS id,174 AS parent_id,'相机' AS name
UNION ALL
SELECT 178 AS id,174 AS parent_id,'相机与光学器件配件' AS name
UNION ALL
SELECT 179 AS id,0 AS parent_id,'箱包' AS name
UNION ALL
SELECT 180 AS id,179 AS parent_id,'公文包' AS name
UNION ALL
SELECT 181 AS id,179 AS parent_id,'化妆箱' AS name
UNION ALL
SELECT 182 AS id,179 AS parent_id,'尿布包' AS name
UNION ALL
SELECT 183 AS id,179 AS parent_id,'手提旅行包/运动桶包' AS name
UNION ALL
SELECT 184 AS id,179 AS parent_id,'旅行箱/包' AS name
UNION ALL
SELECT 185 AS id,179 AS parent_id,'洗漱包/盥洗包' AS name
UNION ALL
SELECT 186 AS id,179 AS parent_id,'箱包配件' AS name
UNION ALL
SELECT 187 AS id,179 AS parent_id,'背包' AS name
UNION ALL
SELECT 188 AS id,179 AS parent_id,'腰包' AS name
UNION ALL
SELECT 189 AS id,179 AS parent_id,'购物袋' AS name
UNION ALL
SELECT 190 AS id,179 AS parent_id,'邮差包' AS name
UNION ALL
SELECT 191 AS id,179 AS parent_id,'防潮箱/盒' AS name
UNION ALL
SELECT 192 AS id,0 AS parent_id,'艺术与娱乐' AS name
UNION ALL
SELECT 193 AS id,192 AS parent_id,'活动门票' AS name
UNION ALL
SELECT 194 AS id,192 AS parent_id,'爱好/艺术创作' AS name
UNION ALL
SELECT 195 AS id,192 AS parent_id,'聚会/庆典' AS name
UNION ALL
SELECT 196 AS id,0 AS parent_id,'软件' AS name
UNION ALL
SELECT 197 AS id,196 AS parent_id,'数字商品与货币' AS name
UNION ALL
SELECT 198 AS id,196 AS parent_id,'电子游戏软件' AS name
UNION ALL
SELECT 199 AS id,196 AS parent_id,'电脑软件' AS name
) seed WHERE NOT EXISTS (SELECT 1 FROM custom_category_seed WHERE id=1);
INSERT IGNORE INTO custom_category_seed VALUES(1);
INSERT INTO sys_menu(id,parent_id,menu_name,menu_type,perms,path,component,icon,sort_order,status) VALUES
(6,0,'收录数据',0,NULL,NULL,NULL,'DataLine',2,1),
(16,6,'站点明细',1,'dashboard:site:view','/indexing/sites','dashboard/IndexingList','Document',1,1),
(68,6,'建站者汇总',1,'dashboard:site:view','/indexing/builders','dashboard/IndexingList','User',2,1),
(69,6,'服务器汇总',1,'dashboard:site:view','/indexing/servers','dashboard/IndexingList','Monitor',3,1),
(70,0,'自定义分类',1,'category:list','/categories','category/CategoryList','CollectionTag',6,1),
(71,70,'维护自定义分类',2,'category:manage',NULL,NULL,NULL,1,1)
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),menu_name=VALUES(menu_name),menu_type=VALUES(menu_type),perms=VALUES(perms),path=VALUES(path),component=VALUES(component),icon=VALUES(icon),sort_order=VALUES(sort_order),status=VALUES(status);
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,6 FROM sys_role_menu WHERE menu_id=16;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,68 FROM sys_role_menu WHERE menu_id=16;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) SELECT role_id,69 FROM sys_role_menu WHERE menu_id=16;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id) VALUES(1,70),(1,71),(2,70),(2,71);
