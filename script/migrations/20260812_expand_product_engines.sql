-- Expand product-engine registration and provide the selector profile shared
-- by every non-Shopify storefront engine.
ALTER TABLE crawl_site_config MODIFY COLUMN type VARCHAR(32) NOT NULL DEFAULT 'shopify';
ALTER TABLE selector_template MODIFY COLUMN platform VARCHAR(32) NOT NULL;

INSERT INTO selector_template (
    name, platform, title_selector, price_selector, price_regex,
    description_selector, images_selector, currency,
    breadcrumb_links_selector, breadcrumb_last_selector, site_map_selector, is_system
) SELECT
    'WooCommerce Default', 'woocommerce',
    '//h1[contains(@class,''product_title'')]/text() | //h1[contains(@class,''product-title'')]/text() | //main//h1/text() | //meta[@property=''og:title'']/@content',
    '//p[contains(@class,''price'')]//*[contains(@class,''amount'')]/text() | //*[@itemprop=''price'']/@content | //meta[@property=''product:price:amount'']/@content',
    '[\\d.,]+',
    '//*[contains(@class,''product'') and contains(@class,''description'')]//text() | //*[@itemprop=''description'']//text() | //meta[@property=''og:description'']/@content',
    '//*[contains(@class,''product'') and contains(@class,''gallery'')]//img/@data-large_image | //*[contains(@class,''product'') and contains(@class,''gallery'')]//img/@src | //meta[@property=''og:image'']/@content',
    'USD',
    '//nav[contains(@class,''breadcrumb'')]//a//text() | //*[contains(@class,''breadcrumbs'')]//a//text() | //ul[contains(@class,''breadcrumb'')]//a//text()',
    '//nav[contains(@class,''breadcrumb'')]//*[last()]//text() | //*[contains(@class,''breadcrumbs'')]//*[last()]//text()',
    '//*[local-name()=''sitemap'']/*[local-name()=''loc'']/text()',
    1
WHERE NOT EXISTS (
    SELECT 1 FROM selector_template
    WHERE name = 'WooCommerce Default' OR (platform = 'woocommerce' AND is_system = 1)
);
