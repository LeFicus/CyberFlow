-- Indexes for large product-list filters, deterministic pagination and exports.
USE scraped_data;

ALTER TABLE ecommerce_products
    ADD INDEX idx_product_created_id (created_at, id),
    ADD INDEX idx_product_domain_created (source_domain, created_at, id),
    ADD INDEX idx_product_category_created (custom_category, created_at, id),
    ADD INDEX idx_product_name_prefix (name(100));
