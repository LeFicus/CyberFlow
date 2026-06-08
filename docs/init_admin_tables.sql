-- ============================================================
-- CyberFlow 后台管理系统 — 数据库初始化脚本
-- 执行方式: mysql -u root -p cyberflow < init_admin_tables.sql
-- ============================================================

USE cyberflow;

-- 用户表
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

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name   VARCHAR(50)  NOT NULL UNIQUE COMMENT '角色名称',
    role_code   VARCHAR(50)  NOT NULL UNIQUE COMMENT '角色编码 (ROLE_ADMIN, ROLE_OPERATOR)',
    description VARCHAR(200) COMMENT '角色描述',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色';

-- 菜单/权限表
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

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- 角色-菜单关联表
CREATE TABLE IF NOT EXISTS sys_role_menu (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_menu (role_id, menu_id),
    INDEX idx_role_id (role_id),
    INDEX idx_menu_id (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联';

-- 操作日志表
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
