-- Keep order ownership after the built-site mirror drops an abandoned domain.
-- Credentials are deliberately excluded from this historical table.
CREATE TABLE IF NOT EXISTS retired_site_info (
    site_domain       VARCHAR(255) NOT NULL PRIMARY KEY,
    admin_name        VARCHAR(100),
    user_group        VARCHAR(32),
    theme_name        VARCHAR(100),
    product_category  VARCHAR(100),
    cat_names         JSON,
    site_tag          TINYINT NOT NULL DEFAULT 0,
    domain_applied_at DATETIME,
    created_at        DATETIME,
    retired_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_retired_site_owner (admin_name, user_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Recover retired domains when at least one existing order still has ownership.
-- Their original site creation month cannot be inferred from an order timestamp.
INSERT IGNORE INTO retired_site_info
    (site_domain, admin_name, user_group, theme_name, product_category, cat_names, site_tag)
SELECT ranked.site_domain, ranked.admin_name, ranked.user_group,
       ranked.theme_name, ranked.product_category,
       CASE WHEN TRIM(COALESCE(ranked.product_category, '')) <> ''
            THEN JSON_ARRAY(ranked.product_category) ELSE NULL END,
       ranked.site_tag
FROM (
    SELECT LOWER(CASE WHEN LEFT(TRIM(product_host), 4) = 'www.'
                      THEN SUBSTRING(TRIM(product_host), 5) ELSE TRIM(product_host) END) AS site_domain,
           admin_name, user_group, theme_name, product_category, site_tag,
           ROW_NUMBER() OVER (
               PARTITION BY LOWER(CASE WHEN LEFT(TRIM(product_host), 4) = 'www.'
                                       THEN SUBSTRING(TRIM(product_host), 5) ELSE TRIM(product_host) END)
               ORDER BY create_time DESC, id DESC
           ) AS rn
    FROM orders
    WHERE TRIM(COALESCE(product_host, '')) <> ''
      AND TRIM(COALESCE(admin_name, '')) <> ''
) ranked
WHERE ranked.rn = 1
  AND NOT EXISTS (
      SELECT 1 FROM site_info s
      WHERE LOWER(CASE WHEN LEFT(TRIM(s.site_domain), 4) = 'www.'
                       THEN SUBSTRING(TRIM(s.site_domain), 5) ELSE TRIM(s.site_domain) END)
            = ranked.site_domain
  );

-- Fill orders that lost their owner while other orders for that domain retained it.
UPDATE orders o
JOIN retired_site_info r
  ON LOWER(CASE WHEN LEFT(TRIM(o.product_host), 4) = 'www.'
                THEN SUBSTRING(TRIM(o.product_host), 5) ELSE TRIM(o.product_host) END)
     = r.site_domain
SET o.admin_name = r.admin_name,
    o.theme_name = COALESCE(NULLIF(o.theme_name, ''), r.theme_name),
    o.product_category = COALESCE(NULLIF(o.product_category, ''), r.product_category),
    o.site_tag = CASE WHEN o.site_tag = 0 THEN r.site_tag ELSE o.site_tag END
WHERE TRIM(COALESCE(o.admin_name, '')) = '';

-- Also repair orders whose live site still exists but whose owner was cleared.
UPDATE orders o
JOIN site_info s
  ON LOWER(CASE WHEN LEFT(TRIM(o.product_host), 4) = 'www.'
                THEN SUBSTRING(TRIM(o.product_host), 5) ELSE TRIM(o.product_host) END)
     = LOWER(CASE WHEN LEFT(TRIM(s.site_domain), 4) = 'www.'
                  THEN SUBSTRING(TRIM(s.site_domain), 5) ELSE TRIM(s.site_domain) END)
SET o.admin_name = s.admin_name,
    o.theme_name = COALESCE(NULLIF(o.theme_name, ''), s.theme_name),
    o.product_category = COALESCE(NULLIF(o.product_category, ''), s.product_category),
    o.site_tag = CASE WHEN o.site_tag = 0 THEN s.site_tag ELSE o.site_tag END
WHERE TRIM(COALESCE(o.admin_name, '')) = ''
  AND TRIM(COALESCE(s.admin_name, '')) <> '';
