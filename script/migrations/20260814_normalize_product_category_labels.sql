-- Product custom categories store only the selected label, not the tree path.
USE scraped_data;

UPDATE ecommerce_products
SET custom_category = SUBSTRING_INDEX(custom_category, '|||', -1)
WHERE custom_category LIKE '%|||%';
