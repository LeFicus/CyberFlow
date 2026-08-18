-- Split order identity by user group so A/B platforms may reuse an external ID.
-- The primary-key change is guarded for repeated or partial deployments.
USE cyberflow;
SET NAMES utf8mb4;

ALTER TABLE orders
    MODIFY COLUMN user_group VARCHAR(1) NOT NULL COMMENT '订单所属负责人用户组/来源平台: A/B';

SET @order_pk_columns = (
    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'PRIMARY'
);
SET @order_pk_sql = IF(
    COALESCE(@order_pk_columns, '') <> 'user_group,id',
    'ALTER TABLE orders DROP PRIMARY KEY, ADD PRIMARY KEY (user_group, id)',
    'SELECT 1'
);
PREPARE order_pk_stmt FROM @order_pk_sql;
EXECUTE order_pk_stmt;
DEALLOCATE PREPARE order_pk_stmt;

-- Preserve the former single payment account as the B-platform default.
INSERT INTO crawler_runtime_config
    (config_group, config_key, config_value, is_sensitive, remark)
SELECT 'paymentApiB', config_key, config_value, is_sensitive, 'Migrated B-group payment API'
FROM crawler_runtime_config
WHERE config_group = 'paymentApi'
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    is_sensitive = VALUES(is_sensitive);
