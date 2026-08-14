-- A/B user grouping follows the reference report scripts:
-- administrators beginning with A- belong to group A; B- belongs to group B.
ALTER TABLE site_info
    ADD COLUMN user_group VARCHAR(1) NULL COMMENT '负责人用户组: A/B' AFTER admin_name,
    ADD INDEX idx_site_user_group (user_group);

ALTER TABLE orders
    ADD COLUMN user_group VARCHAR(1) NULL COMMENT '订单所属负责人用户组: A/B' AFTER admin_name,
    ADD INDEX idx_order_user_group (user_group);

UPDATE site_info
SET user_group = CASE
    WHEN UPPER(TRIM(admin_name)) LIKE 'A-%' THEN 'A'
    WHEN UPPER(TRIM(admin_name)) LIKE 'B-%' THEN 'B'
    ELSE NULL
END;

UPDATE orders o
LEFT JOIN site_info s
    ON LOWER(TRIM(LEADING 'www.' FROM o.product_host)) =
       LOWER(TRIM(LEADING 'www.' FROM s.site_domain))
SET o.admin_name = COALESCE(NULLIF(s.admin_name, ''), o.admin_name),
    o.theme_name = COALESCE(NULLIF(s.theme_name, ''), o.theme_name),
    o.product_category = COALESCE(NULLIF(s.product_category, ''), o.product_category),
    o.user_group = COALESCE(
        s.user_group,
        CASE
            WHEN UPPER(TRIM(o.admin_name)) LIKE 'A-%' THEN 'A'
            WHEN UPPER(TRIM(o.admin_name)) LIKE 'B-%' THEN 'B'
            ELSE NULL
        END
    );
