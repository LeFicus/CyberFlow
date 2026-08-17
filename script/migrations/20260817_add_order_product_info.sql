-- Preserve the productInfo array returned by the payment platform order crawler.
ALTER TABLE orders
    ADD COLUMN product_info JSON NULL COMMENT '订单爬取结果中的商品详情数组' AFTER product_category;
