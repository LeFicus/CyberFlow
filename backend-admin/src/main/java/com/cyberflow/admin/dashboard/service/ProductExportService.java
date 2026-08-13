package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.EcommerceProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts normalized crawl records into import-compatible CSV columns. */
@Service
@RequiredArgsConstructor
public class ProductExportService {
    private final EcommerceProductMapper productMapper;

    public List<List<String>> buildRows(String engine, String domain) {
        List<Map<String, Object>> products = productMapper.listProductsForExport(domain);
        return switch (engine) {
            case "shopify" -> shopify(products);
            case "woocommerce" -> woo(products);
            case "bigcommerce" -> bigCommerce(products);
            default -> throw new IllegalArgumentException("Unsupported export engine: " + engine);
        };
    }

    public List<String> headers(String engine) {
        return switch (engine) {
            case "shopify" -> List.of("Handle", "Title", "Body (HTML)", "Vendor", "Type", "Tags", "Published", "Variant SKU", "Variant Price", "Image Src");
            case "woocommerce" -> List.of("Type", "SKU", "Name", "Published", "Regular price", "Categories", "Images", "Description");
            case "bigcommerce" -> List.of("Item Type", "Product Name", "Product Code/SKU", "Price", "Category", "Product Description", "Product Image File - 1");
            default -> throw new IllegalArgumentException("Unsupported export engine: " + engine);
        };
    }

    private List<List<String>> shopify(List<Map<String, Object>> products) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> p : products) rows.add(List.of(
                slug(value(p, "name"), value(p, "sku")), value(p, "name"), value(p, "description"),
                value(p, "source_domain"), value(p, "categories"), value(p, "categories"), "TRUE",
                value(p, "sku"), price(p.get("regular_price")), value(p, "images")));
        return rows;
    }

    private List<List<String>> woo(List<Map<String, Object>> products) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> p : products) rows.add(List.of("simple", value(p, "sku"), value(p, "name"), "1",
                price(p.get("regular_price")), value(p, "categories"), value(p, "images"), value(p, "description")));
        return rows;
    }

    private List<List<String>> bigCommerce(List<Map<String, Object>> products) {
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> p : products) rows.add(List.of("Product", value(p, "name"), value(p, "sku"),
                price(p.get("regular_price")), value(p, "categories"), value(p, "description"), value(p, "images")));
        return rows;
    }

    private static String value(Map<String, Object> p, String key) { return p.get(key) == null ? "" : String.valueOf(p.get(key)); }
    private static String price(Object value) { return value instanceof BigDecimal d ? d.toPlainString() : value == null ? "" : String.valueOf(value); }
    private static String slug(String name, String fallback) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? fallback : slug;
    }
}
