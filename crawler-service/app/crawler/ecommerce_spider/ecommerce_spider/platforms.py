"""Explicit platform dispatch; unsupported engines must never fall through to WooCommerce."""

SPIDERS = {
    "shopify": "shopify_crawl_fast",
    "woocommerce": "platform_crawl",
    "bigcommerce": "bigcommerce_crawl",
    "magento": "magento_crawl",
    "wix": "wix_crawl",
    "ecwid": "ecwid_crawl",
    "shopline": "shopline_crawl",
}
