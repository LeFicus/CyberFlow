-- AI-generated brand assets for new sites. Binary image data stays in the
-- configured asset directory; this table stores lifecycle and audit metadata.
CREATE TABLE IF NOT EXISTS new_site_asset (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id             BIGINT NOT NULL,
    generation_group    CHAR(36) NOT NULL,
    asset_type          VARCHAR(32) NOT NULL COMMENT 'logo / banner / icon',
    variant             VARCHAR(32) NOT NULL COMMENT 'original / desktop / mobile / 512x512 / 192x192 / 64x64 / 32x32',
    storage_key         VARCHAR(500),
    mime_type           VARCHAR(64),
    width               INT,
    height              INT,
    provider            VARCHAR(64),
    model               VARCHAR(128),
    prompt              TEXT,
    status              VARCHAR(32) NOT NULL DEFAULT 'queued' COMMENT 'queued / generating / ready / failed',
    is_selected         TINYINT NOT NULL DEFAULT 0,
    error_message       VARCHAR(500),
    created_by          BIGINT,
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_new_site_asset_site_created (site_id, created_at),
    INDEX idx_new_site_asset_group (generation_group),
    INDEX idx_new_site_asset_status (status),
    CONSTRAINT fk_new_site_asset_site FOREIGN KEY (site_id) REFERENCES new_site(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI-generated new-site brand assets';

INSERT INTO sys_menu
    (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order, status)
VALUES
    (68, 63, '管理品牌素材', 2, 'newsite:asset', NULL, NULL, NULL, 5, 1)
ON DUPLICATE KEY UPDATE
    parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type),
    perms=VALUES(perms), sort_order=VALUES(sort_order), status=VALUES(status);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 68), (2, 68);
