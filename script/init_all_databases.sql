SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================================
-- CyberFlow Docker 初始化脚本
-- ============================================================
-- 功能: 创建数据库、所有业务表及种子数据
-- 适用: Docker 容器首次启动时的数据库初始化
-- MySQL 版本要求: 5.7+ (支持 utf8mb4 字符集)
-- ============================================================

CREATE DATABASE IF NOT EXISTS cyberflow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS scraped_data DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================================
-- 数据库 1: cyberflow — 系统核心库
-- ============================================================
-- 包含以下模块:
--   1. RBAC 权限系统 (sys_user, sys_role, sys_menu, sys_user_role, sys_role_menu)
--   2. 操作审计日志 (sys_operation_log)
--   3. 爬虫调度管理 (task_history, crawl_cursor)
--   4. 爬虫配置 (selector_template, crawl_site_config, site_template_mapping)
--   5. 数据仓库 (site_info, orders, site_indexing_history)
-- ============================================================
USE cyberflow;

-- ============================================================
-- 1. RBAC 权限管理系统
-- ============================================================

-- ------------------------------------------------------------
-- 系统用户表 — 存储所有可登录系统的用户账号
-- ------------------------------------------------------------
-- 密码使用 BCrypt 加密存储，管理员默认账号 admin / admin123
-- status: 0=禁用（无法登录） 1=启用
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '登录用户名',
    password    VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密密码',
    nickname    VARCHAR(50)  COMMENT '显示昵称',
    email       VARCHAR(100) COMMENT '邮箱',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- ------------------------------------------------------------
-- 系统角色表 — 定义角色的权限边界
-- ------------------------------------------------------------
-- role_code 对应 Spring Security 的 GrantedAuthority（如 ROLE_ADMIN）
-- 与 sys_menu 通过 sys_role_menu 表多对多关联
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name   VARCHAR(50)  NOT NULL UNIQUE COMMENT '角色名称',
    role_code   VARCHAR(50)  NOT NULL UNIQUE COMMENT '角色编码 (ROLE_ADMIN, ROLE_OPERATOR)',
    description VARCHAR(200) COMMENT '角色描述',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色';

-- ------------------------------------------------------------
-- 菜单/权限表 — 前端路由 + 后端 API 权限的树形定义
-- ------------------------------------------------------------
-- menu_type 类型说明:
--   0=目录    — 仅用于前端导航分组，无实际页面
--   1=菜单    — 对应一个 Vue 路由页面（path + component 组合）
--   2=按钮/权限— 对应一个 API 操作权限（如触发爬虫、导出数据）
--
-- perms 字段格式: 模块:子模块:操作
--   例: crawler:site:start  (爬虫模块 → 站点 → 启动)
--       dashboard:order:view (看板模块 → 订单 → 查看)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_menu (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0=顶级',
    menu_name   VARCHAR(50)  NOT NULL COMMENT '菜单名称',
    menu_type   TINYINT      NOT NULL COMMENT '0=目录 1=菜单 2=按钮/权限',
    perms       VARCHAR(100) COMMENT '权限标识，如 crawler:site:start',
    path        VARCHAR(200) COMMENT '前端路由路径',
    component   VARCHAR(200) COMMENT '前端组件路径',
    icon        VARCHAR(50)  COMMENT 'Element Plus 图标名',
    sort_order  INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限';

-- ------------------------------------------------------------
-- 用户-角色关联表 — 一个用户可拥有多个角色
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_role (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- ------------------------------------------------------------
-- 角色-菜单关联表 — 一个角色可拥有多个菜单权限
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role_menu (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_menu (role_id, menu_id),
    INDEX idx_role_id (role_id),
    INDEX idx_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联';

-- ------------------------------------------------------------
-- 操作审计日志表 — 记录所有用户操作行为
-- ------------------------------------------------------------
-- 记录粒度: 每次 HTTP 请求生成一条日志
-- operation 类型:
--   QUERY          — 数据查询
--   CREATE/UPDATE/DELETE — 数据增删改
--   TRIGGER_CRAWLER — 触发爬虫任务
--   EXPORT          — 数据导出
--
-- module 模块:
--   SYSTEM   — 系统管理（用户/角色/菜单）
--   CRAWLER  — 爬虫管理（站点/订单/收录）
--   DASHBOARD— 数据看板
--
-- cost_time 以毫秒为单位，记录后端处理耗时
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_operation_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       COMMENT '操作用户ID',
    username        VARCHAR(50)  COMMENT '操作用户名（冗余）',
    operation       VARCHAR(50)  NOT NULL COMMENT '操作类型: QUERY/CREATE/UPDATE/DELETE/TRIGGER_CRAWLER/EXPORT',
    module          VARCHAR(50)  COMMENT '模块: SYSTEM/CRAWLER/DASHBOARD',
    target          VARCHAR(200) COMMENT '操作对象',
    request_method  VARCHAR(10)  COMMENT 'HTTP 方法',
    request_url     VARCHAR(500) COMMENT '请求 URL',
    request_params  TEXT         COMMENT '请求参数(JSON)',
    ip              VARCHAR(50)  COMMENT '客户端 IP',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0=失败 1=成功',
    error_msg       TEXT         COMMENT '错误信息',
    cost_time       BIGINT       COMMENT '执行耗时(ms)',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    INDEX idx_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

-- ============================================================
-- 2. 爬虫调度与配置管理
-- ============================================================

-- ------------------------------------------------------------
-- 任务历史表 — 记录每次爬虫任务的执行情况
-- ------------------------------------------------------------
-- task_id: UUID 格式，用于追踪单次任务的生命周期
-- type 任务类型:
--   site_crawl     — 站点信息爬虫
--   order_crawl    — 订单数据爬虫
--   product_crawl  — 商品数据爬虫
--   site_index     — 站点收录统计爬虫
--
-- trigger_type:
--   cron   — 定时自动触发
--   manual — 用户手动触发
--
-- status 状态流转: PENDING → RUNNING → SUCCESS / FAILED / CANCELLED
-- cursor_before/cursor_after: 用于增量爬取，记录处理游标
-- duration_ms: 任务总耗时（毫秒）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS task_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         VARCHAR(64) NOT NULL UNIQUE,
    type            VARCHAR(30) NOT NULL COMMENT 'site_crawl / order_crawl / product_crawl / site_index',
    trigger_type    VARCHAR(20) NOT NULL COMMENT 'cron / manual',
    triggered_by    VARCHAR(64),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress        TINYINT NOT NULL DEFAULT 0 COMMENT '0-100',
    progress_message VARCHAR(255),
    cursor_before   VARCHAR(255),
    cursor_after    VARCHAR(255),
    rows_affected   INT DEFAULT 0,
    error_msg       TEXT,
    crawl_log       LONGTEXT COMMENT '完整爬虫 stdout/stderr 日志',
    duration_ms     BIGINT,
    started_at      DATETIME,
    finished_at     DATETIME,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 增量游标表 — 记录各爬虫任务的处理进度
-- ------------------------------------------------------------
-- cursor_key 对应的游标:
--   site_crawler       — 站点爬虫最后一次处理的时间点
--   site_index_crawler — 收录统计爬虫游标
--   order_crawler      — 订单爬虫游标（记录最后处理的订单 ID）
--
-- 增量爬取策略: 每次任务启动时读取 cursor_value，仅处理之后的新数据
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crawl_cursor (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    cursor_key   VARCHAR(100) NOT NULL UNIQUE,
    cursor_value VARCHAR(255) NOT NULL,
    last_sync_at DATETIME NOT NULL,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 爬虫运行配置表 — 维护平台凭据、采集策略和收入计算参数
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crawler_runtime_config (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_group  VARCHAR(50)  NOT NULL COMMENT '配置分组',
    config_key    VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value  TEXT COMMENT '配置值，复杂结构使用 JSON',
    is_sensitive  TINYINT      NOT NULL DEFAULT 0 COMMENT '1=敏感字段，接口返回时脱敏',
    remark        VARCHAR(255),
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_key (config_group, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫运行配置';

-- ------------------------------------------------------------
-- 爬虫定时配置表 — 后台可控的 Quartz 调度开关和 cron
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crawler_schedule_config (
    task_type         VARCHAR(30) PRIMARY KEY COMMENT 'site_crawl / order_crawl',
    cron_expression   VARCHAR(100) NOT NULL,
    enabled           TINYINT      NOT NULL DEFAULT 1,
    last_triggered_at DATETIME,
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫定时配置';

-- ------------------------------------------------------------
-- 选择器模板库 — 存储不同电商平台/主题的 XPath 选择器配置
-- ------------------------------------------------------------
-- platform 平台:
--   shopify — Shopify 站点（使用 JSON API 采集，选择器通常为空）
--
-- is_system: 1=系统预设模板（不可删除），0=用户自定义模板
--
-- 选择器字段说明:
--   title_selector           — 商品标题 XPath（支持 | 多备选）
--   price_selector           — 价格 XPath
--   price_regex              — 价格文本的正则提取规则
--   description_selector     — 描述内容 XPath
--   images_selector          — 商品图片 XPath
--   breadcrumb_links_selector— 面包屑导航中的分类链接 XPath
--   breadcrumb_last_selector — 面包屑最后一层 XPath（通常为当前商品名，用于过滤）
--   site_map_selector        — 站点地图索引中的子站点地图链接 XPath
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 商品爬取站点注册表 — 管理需要爬取的目标站点
-- ------------------------------------------------------------
-- type 站点类型:
--   shopify  — Shopify 平台站点
--
-- status 状态:
--   active     — 正常采集
--   inactive   — 暂停采集
--   error      — 采集异常
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 站点↔模板多对多关联表 — 一个站点可使用多个选择器模板
-- ------------------------------------------------------------
-- extra_selectors: JSON 格式，可覆盖/补充模板中的默认选择器
-- CASCADE 删除: 站点或模板被删除时，自动清理关联记录
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 站点信息表 — 存储电商站点的基本维度信息
-- ------------------------------------------------------------
-- 数据来源: 由站点爬虫从远程管理系统采集后写入
-- site_domain: 唯一约束，带索引以便快速查询
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS site_info (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    username         VARCHAR(100) COMMENT '电商平台用户名',
    site_domain      VARCHAR(255) NOT NULL UNIQUE COMMENT '站点域名',
    admin_name       VARCHAR(100) COMMENT '管理员名称',
    user_group       VARCHAR(1) COMMENT '负责人用户组: A/B',
    theme_name       VARCHAR(100) COMMENT '主题名称',
    product_category VARCHAR(100) COMMENT '商品分类',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_domain (site_domain),
    INDEX idx_site_user_group (user_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点信息';

-- ------------------------------------------------------------
-- 订单表 — 存储电商平台支付系统的订单流水
-- ------------------------------------------------------------
-- id: 直接使用外部电商平台的订单 ID（非自增，防止重复导入）
-- amount: 使用 DECIMAL(10,2) 精确存储，避免浮点数精度问题
-- admin_name / theme_name / product_category: 冗余自 site_info 表，加速查询
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id                    BIGINT NOT NULL COMMENT '订单ID (来自电商平台)',
    amount                DECIMAL(10, 2) COMMENT '订单金额',
    currency              VARCHAR(10) COMMENT '货币',
    create_time           DATETIME COMMENT '下单时间',
    product_host          VARCHAR(255) COMMENT '商品站点域名',
    pay_status_text       VARCHAR(50) COMMENT '支付状态',
    customer_ip_country   VARCHAR(100) COMMENT '客户IP国家',
    shipping_email        VARCHAR(255) COMMENT '收货邮箱',
    admin_name            VARCHAR(100) COMMENT '店铺管理员',
    user_group            VARCHAR(1) NOT NULL COMMENT '订单所属负责人用户组/来源平台: A/B',
    theme_name            VARCHAR(100) COMMENT '主题',
    product_category      VARCHAR(100) COMMENT '商品分类',
    PRIMARY KEY (user_group, id),
    INDEX idx_create_time (create_time),
    INDEX idx_product_host (product_host),
    INDEX idx_order_user_group (user_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单信息';

-- ------------------------------------------------------------
-- 站点索引历史表 — 记录站点在 Google 搜索引擎中的收录趋势
-- ------------------------------------------------------------
-- 数据来源: 由站点收录统计爬虫定期采集
-- 同一站点每天仅有一条记录（后续采集更新同日记录的数据）
-- index_count: Google 收录的索引数量
-- product_count: 站点上的商品总数
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS site_indexing_history (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_domain    VARCHAR(255) NOT NULL COMMENT '站点域名',
    index_count    INT NOT NULL DEFAULT 0 COMMENT '索引数量',
    product_count  INT NOT NULL DEFAULT 0 COMMENT '商品数量',
    recorded_at    DATETIME NOT NULL COMMENT '记录时间',
    INDEX idx_recorded_at (recorded_at),
    INDEX idx_site_domain (site_domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点索引历史';

-- ============================================================
-- 3. 种子数据 — 预置角色、管理员、菜单、选择器模板
-- ============================================================

-- ------------------------------------------------------------
-- 预置角色: 超级管理员（全部权限）和运营人员（查看触发权限）
-- ------------------------------------------------------------
INSERT INTO sys_role (id, role_name, role_code, description) VALUES
(1, '超级管理员', 'ROLE_ADMIN', '拥有所有权限'),
(2, '运营人员', 'ROLE_OPERATOR', '可查看数据看板、触发爬虫'),
(3, '普通用户', 'ROLE_USER', '仅可查看经营数据和执行 A/B 订单爬取')
ON DUPLICATE KEY UPDATE
    role_name=VALUES(role_name),
    role_code=VALUES(role_code),
    description=VALUES(description),
    status=1;

-- ------------------------------------------------------------
-- 默认管理员账号
-- 用户名: admin  密码: admin123 (BCrypt 加密)
-- ------------------------------------------------------------
INSERT INTO sys_user (id, username, password, nickname, status) VALUES
(1, 'admin', '$2a$10$dTgm6d5qREmehuLVt8T7oe.XUuwjlSbCR4bTEuM1Mc5D9ROTdfw0W', '系统管理员', 1)
ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), status=VALUES(status);

-- ------------------------------------------------------------
-- 管理员仅拥有超级管理员角色，避免重复角色造成权限展示混乱
-- ------------------------------------------------------------
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1)
ON DUPLICATE KEY UPDATE user_id=user_id;
DELETE FROM sys_user_role WHERE user_id = 1 AND role_id = 2;

-- ------------------------------------------------------------
-- 菜单树 — 三大板块的完整导航和权限配置
-- ------------------------------------------------------------
-- 菜单树结构:
--   /dashboard (数据看板)
--     ├── 概览
--     ├── 站点列表
--     ├── 订单列表
--     └── 商品列表
--   /crawler (爬虫管理)
--     ├── 站点爬虫
--     │   └── [按钮] 触发站点爬虫
--     ├── 收录统计
--     │   └── [按钮] 触发收录统计
--     ├── 订单爬虫
--     │   └── [按钮] 触发订单爬虫
--     └── 任务历史
--   /system (系统管理)
--     ├── 用户管理
--     ├── 角色管理
--     ├── 菜单管理
--     └── 操作日志
-- ------------------------------------------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort_order) VALUES
-- 目录
(1,  0, '数据看板', 0, '/dashboard', NULL, 'DataBoard', 1),
(2,  0, '爬虫管理', 0, '/crawler', NULL, 'Cpu', 2),
(3,  0, '系统管理', 0, '/system', NULL, 'Setting', 3),
-- 看板子菜单
(11, 1, '概览', 1, '/dashboard/overview', 'dashboard/Overview', NULL, 1),
(12, 1, '站点列表', 1, '/dashboard/sites', 'dashboard/SiteList', NULL, 2),
(13, 1, '订单列表', 1, '/dashboard/orders', 'dashboard/OrderList', NULL, 3),
(14, 1, '商品列表', 1, '/dashboard/products', 'dashboard/ProductList', NULL, 4),
(15, 14, '删除商品', 2, NULL, NULL, NULL, 1),
-- 爬虫子菜单
(21, 2, '站点爬虫', 1, '/crawler/site', 'crawler/SiteCrawler', NULL, 1),
(22, 2, '收录统计', 1, '/crawler/collect', 'crawler/CollectCrawler', NULL, 2),
(23, 2, '订单爬虫', 1, '/crawler/order', 'crawler/OrderCrawler', NULL, 3),
(24, 2, '任务历史', 1, '/crawler/history', 'crawler/TaskHistory', NULL, 4),
-- 爬虫按钮权限
(25, 21, '触发站点爬虫', 2, NULL, NULL, NULL, 1),
(26, 22, '触发收录统计', 2, NULL, NULL, NULL, 1),
(27, 23, '触发订单爬虫', 2, NULL, NULL, NULL, 1),
-- 系统管理子菜单
(31, 3, '用户管理', 1, '/system/user', 'system/UserList', NULL, 1),
(32, 3, '角色管理', 1, '/system/role', 'system/RoleList', NULL, 2),
(33, 3, '菜单管理', 1, '/system/menu', 'system/MenuTree', NULL, 3),
(34, 3, '操作日志', 1, '/system/log', 'system/OperationLog', NULL, 4)
ON DUPLICATE KEY UPDATE
    parent_id=VALUES(parent_id),
    menu_name=VALUES(menu_name),
    menu_type=VALUES(menu_type),
    path=VALUES(path),
    component=VALUES(component),
    icon=VALUES(icon),
    sort_order=VALUES(sort_order);

-- ------------------------------------------------------------
-- 权限标识分配 — 为每个菜单项绑定对应的后端 API 权限标识
-- ------------------------------------------------------------
UPDATE sys_menu SET perms = 'dashboard:overview'      WHERE id = 11;
UPDATE sys_menu SET perms = 'dashboard:site:view'     WHERE id = 12;
UPDATE sys_menu SET perms = 'dashboard:order:view'    WHERE id = 13;
UPDATE sys_menu SET perms = 'dashboard:product:view'  WHERE id = 14;
UPDATE sys_menu SET perms = 'dashboard:product:delete' WHERE id = 15;
UPDATE sys_menu SET perms = 'crawler:site:start'      WHERE id = 25;
UPDATE sys_menu SET perms = 'crawler:collect:start'   WHERE id = 26;
UPDATE sys_menu SET perms = 'crawler:order:start'     WHERE id = 27;
UPDATE sys_menu SET perms = 'crawler:order:view'      WHERE id = 23;
UPDATE sys_menu SET perms = 'crawler:history:view'    WHERE id = 24;
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, status, sort_order) VALUES
(49, 24, '任务控制', 2, 'crawler:task:control', 1, 1),
(50, 24, '删除任务', 2, 'crawler:task:delete', 1, 2),
(51, 23, '修改订单配置', 2, 'crawler:order:config', 1, 2)
ON DUPLICATE KEY UPDATE perms=VALUES(perms), status=1;
UPDATE sys_menu SET perms = 'system:user:list'        WHERE id = 31;
UPDATE sys_menu SET perms = 'system:role:list'        WHERE id = 32;
UPDATE sys_menu SET perms = 'system:menu:list'        WHERE id = 33;
UPDATE sys_menu SET perms = 'system:log:view'         WHERE id = 34;

-- ------------------------------------------------------------
-- 角色权限分配
-- ------------------------------------------------------------
-- 超级管理员 (role_id=1): 拥有所有菜单权限
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu;
-- 运营人员 (role_id=2): 拥有看板、爬虫目录及其页面/操作权限（排除系统管理模块）
DELETE FROM sys_role_menu WHERE role_id = 2 AND menu_id IN (3, 31, 32, 33, 34);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (2, 1), (2, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) SELECT 2, id FROM sys_menu WHERE menu_type = 1 AND parent_id IN (1, 2);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) SELECT 2, id FROM sys_menu WHERE id IN (25, 26, 27);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (2, 15);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(1, 49), (1, 50), (1, 51), (2, 49), (2, 50), (2, 51);

DELETE FROM sys_role_menu WHERE role_id = 3;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
(3, 1), (3, 11), (3, 12), (3, 13), (3, 2), (3, 23), (3, 24), (3, 27);

INSERT INTO sys_user (id, username, password, nickname, status) VALUES
(2, 'normal_user', '$2b$12$VA8lCR9fDcXkscYPUls7.O6dGD67C1FKpx9HtTIwuX3nzq5fVJ7KC', '普通用户', 1)
ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), status=1;
DELETE FROM sys_user_role WHERE user_id = 2;
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 3);

-- ------------------------------------------------------------
-- 预置选择器模板: Shopify 平台（标记为系统模板，无需配置选择器）
-- ------------------------------------------------------------
-- Shopify 站点无需 HTML 选择器，通过 products.json API 直接获取 JSON 数据
-- 此模板仅用于在管理界面中标识 Shopify 爬虫类型
-- ------------------------------------------------------------
INSERT INTO selector_template (name, platform, is_system) VALUES ('Shopify Default', 'shopify', 1)
ON DUPLICATE KEY UPDATE name=name;

-- ------------------------------------------------------------
-- 初始化增量爬虫游标 — 首次启动时从当前时间开始
-- ------------------------------------------------------------
INSERT IGNORE INTO crawl_cursor (cursor_key, cursor_value, last_sync_at) VALUES
('site_crawler', NOW(), NOW()),
('site_index_crawler', NOW(), NOW()),
('order_crawler', '0', NOW());

-- ------------------------------------------------------------
-- 初始化平台、策略和收入配置
-- ------------------------------------------------------------
INSERT INTO crawler_runtime_config (config_group, config_key, config_value, is_sensitive, remark) VALUES
('adminApi', 'baseUrl', '', 0, 'Admin API Base URL (configure in the admin UI or environment)'),
('adminApi', 'username', '', 0, 'Admin API username (configure in the admin UI or environment)'),
('adminApi', 'password', '', 1, 'Admin API password (configure in the admin UI or environment)'),
('adminApi', 'verifySsl', 'true', 0, 'Verify SSL certificates'),
('paymentApi', 'baseUrl', '', 0, 'Payment API Base URL (configure in the admin UI or environment)'),
('paymentApi', 'account', '', 0, 'Payment API account (configure in the admin UI or environment)'),
('paymentApi', 'password', '', 1, 'Payment API password (configure in the admin UI or environment)'),
('paymentApi', 'verifySsl', 'true', 0, 'Verify SSL certificates'),
('siteStrategy', 'skipSiteCheck', 'true', 0, 'Skip site availability check'),
('siteStrategy', 'fetchAdminLoginUrl', 'false', 0, 'Fetch admin login URL'),
('siteStrategy', 'filterBuiltOnly', 'false', 0, 'Only keep built sites'),
('siteStrategy', 'pageSize', '100', 0, 'Admin API page size'),
('orderStrategy', 'filterCardNumberExclude', '["400000******0000","411111******1111","411111111111"]', 0, 'Excluded card numbers'),
('orderStrategy', 'pageSize', '100', 0, 'Payment API page size'),
('orderStrategy', 'initialOrderId', '0', 0, 'Initial max order ID for incremental crawl'),
('revenue', 'exchangeRate', '6.73', 0, 'Realtime exchange rate'),
('revenue', 'rateFactor', '0.42', 0, 'Rate factor'),
('revenue', 'leaderCommissionRate', '0.02', 0, 'Leader commission rate'),
('revenue', 'commissionTiers', '[{"threshold":30000,"rate":0.03},{"threshold":80000,"rate":0.05},{"threshold":"","rate":0.08}]', 0, 'Commission tiers')
ON DUPLICATE KEY UPDATE config_value=VALUES(config_value);

INSERT INTO crawler_schedule_config (task_type, cron_expression, enabled) VALUES
    ('site_crawl', '0 0 2 * * ?', 1),
    ('site_index', '0 30 2 * * ?', 1),
    ('order_crawl', '0 0 3 * * ?', 1)
ON DUPLICATE KEY UPDATE task_type=task_type;

-- ============================================================
-- 数据库 2: scraped_data — 爬取数据存储库
-- ============================================================
-- 存储由 Scrapy 商品爬虫采集到的结构化商品数据
-- 与 cyberflow 库中的 system 管理数据完全分离
-- ============================================================
USE scraped_data;

-- ------------------------------------------------------------
-- 商品表 — 存储所有爬取的电商商品数据
-- ------------------------------------------------------------
-- sku: 全局唯一库存编码（由爬虫动态生成，如 ELEC-A1B2C3）
--      使用 UNIQUE 约束确保去重，ON DUPLICATE KEY UPDATE 自动更新已有记录
--
-- 字段说明:
--   sku              — 商品唯一 SKU 编码
--   name             — 商品名称
--   description      — 商品描述（清洗后的 HTML）
--   regular_price    — 商品价格（已转换为 USD）
--   categories       — 商品分类（面包屑提取，格式: Cat1|||Cat2）
--   images           — 首图 URL
--   cf_opingts       — 商品属性选项（格式: Size^S#M#L|||Color^Red#Blue）
--   custom_category  — 业务自定义分类
--   source_domain    — 原始站点域名
--   language         — 语言代码（默认 "en"）
--   created_at       — 首次爬取时间
--   updated_at       — 最后更新时间（ON UPDATE 自动维护）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ecommerce_products (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku             VARCHAR(255) NOT NULL UNIQUE,
    name            VARCHAR(500),
    description     TEXT,
    regular_price   DECIMAL(10, 2),
    categories      VARCHAR(500),
    images          TEXT,
    cf_opingts      TEXT,
    custom_category VARCHAR(100),
    source_domain   VARCHAR(255),
    language        VARCHAR(10) DEFAULT 'en',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_created_id (created_at, id),
    INDEX idx_product_domain_created (source_domain, created_at, id),
    INDEX idx_product_category_created (custom_category, created_at, id),
    INDEX idx_product_name_prefix (name(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
