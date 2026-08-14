-- Run after clearing orders. A/B platforms may reuse the same external order ID.
ALTER TABLE orders
    MODIFY COLUMN user_group VARCHAR(1) NOT NULL COMMENT '订单所属负责人用户组/来源平台: A/B',
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (user_group, id);

-- Preserve the former single payment account as the B-platform default.
INSERT INTO crawler_runtime_config
    (config_group, config_key, config_value, is_sensitive, remark)
SELECT 'paymentApiB', config_key, config_value, is_sensitive, 'Migrated B-group payment API'
FROM crawler_runtime_config
WHERE config_group = 'paymentApi'
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    is_sensitive = VALUES(is_sensitive);
