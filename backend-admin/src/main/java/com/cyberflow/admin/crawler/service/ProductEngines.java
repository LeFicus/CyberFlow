package com.cyberflow.admin.crawler.service;

import java.util.Set;

/** Engines with a dedicated, tested dispatch path. */
public final class ProductEngines {
    private ProductEngines() {}
    public static final Set<String> SUPPORTED = Set.of(
        "shopify", "woocommerce", "bigcommerce", "magento", "wix", "ecwid", "shopline"
    );
}
