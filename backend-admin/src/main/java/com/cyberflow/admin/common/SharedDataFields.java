package com.cyberflow.admin.common;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Shared-row field codes and their concrete API keys. */
public final class SharedDataFields {
    private SharedDataFields() {}

    private static final Map<String, Set<String>> PERFORMANCE = fields(
            entry("performance.user_group", "user_group"),
            entry("performance.real_name", "real_name", "accounts", "admin_name"),
            entry("performance.site_month", "site_month"),
            entry("performance.site_count", "site_count", "batch_site_count"),
            entry("performance.valid_deduplicated_orders", "total_orders", "successful_orders",
                    "deduplicated_orders", "valid_deduplicated_orders", "valid_ordered_site_count",
                    "ordered_site_count"),
            entry("performance.order_conversion_rate", "order_conversion_rate", "conversion_rate"),
            entry("performance.site_conversion_rate", "site_conversion_rate"),
            entry("performance.hundred_site_conversion_rate", "hundred_site_conversion_rate"),
            entry("performance.successful_amount", "original_amount", "synced_amount", "successful_amount",
                    "batch_site_amount"),
            entry("performance.commission", "batch_site_commission_base_rmb", "batch_site_commission_rate",
                    "regular_commission_rmb", "batch_site_commission_rmb", "commission_rmb",
                    "total_member_commission_rmb"),
            entry("performance.breakdown", "classification_breakdown", "category_breakdown",
                    "customer_country_breakdown")
    );

    private static final Map<String, Set<String>> ORDER = fields(
            entry("order.id", "id"),
            entry("order.product_info", "product_info"),
            entry("order.amount", "amount"),
            entry("order.currency", "currency"),
            entry("order.product_host", "product_host"),
            entry("order.category", "product_category", "cat_names", "theme_name"),
            entry("order.site_tag", "site_tag"),
            entry("order.pay_status", "pay_status_text"),
            entry("order.fraud_status", "risk_status", "is_fraud", "is_valid", "effective_valid"),
            entry("order.country", "customer_ip_country"),
            entry("order.shipping_email", "shipping_email"),
            entry("order.shipping_address", "shipping_address"),
            entry("order.admin_name", "admin_name"),
            entry("order.user_group", "user_group"),
            entry("order.create_time", "create_time")
    );

    public static Set<String> all() {
        var result = new LinkedHashSet<String>();
        result.addAll(PERFORMANCE.keySet());
        result.addAll(ORDER.keySet());
        return Set.copyOf(result);
    }

    public static void retainPerformanceFields(Map<String, Object> row, DataScope scope) {
        retain(row, permittedKeys(PERFORMANCE, scope.sharedFields()));
    }

    public static void retainOrderFields(Map<String, Object> row, DataScope scope) {
        retain(row, permittedKeys(ORDER, scope.sharedFields()));
    }

    private static void retain(Map<String, Object> row, Set<String> permitted) {
        row.keySet().removeIf(key -> !permitted.contains(key));
    }

    private static Set<String> permittedKeys(Map<String, Set<String>> definitions, Collection<String> granted) {
        var result = new LinkedHashSet<String>();
        for (String field : granted) result.addAll(definitions.getOrDefault(field, Set.of()));
        return result;
    }

    @SafeVarargs
    private static Map<String, Set<String>> fields(Map.Entry<String, Set<String>>... entries) {
        var result = new LinkedHashMap<String, Set<String>>();
        for (var entry : entries) result.put(entry.getKey(), entry.getValue());
        return Map.copyOf(result);
    }

    private static Map.Entry<String, Set<String>> entry(String code, String... keys) {
        return Map.entry(code, Set.of(keys));
    }
}
