package com.cyberflow.admin.dashboard.mapper;

/** One policy for historical rows, incoming orders, lists and performance reports. */
public final class OrderFraudSql {
    private OrderFraudSql() {}

    public static final String EMAIL_PATTERN = "^[a-z]+[.][a-z]+[.][0-9]{5}@gmail[.]com$";
    public static final String SUSPECT_SQL = "REGEXP_LIKE(LOWER(TRIM(COALESCE(shipping_email, ''))), '" + EMAIL_PATTERN + "', 'c')";
    public static final String REVIEW_SQL = "COALESCE((SELECT fr.decision FROM order_fraud_reviews fr " +
            "WHERE fr.user_group = orders.user_group COLLATE utf8mb4_unicode_ci AND fr.order_id = orders.id " +
            "AND fr.email = LOWER(TRIM(orders.shipping_email)) COLLATE utf8mb4_unicode_ci ORDER BY fr.id DESC LIMIT 1), 'PENDING')";
    public static final String VALID_SQL = "(is_valid = 0 AND (NOT " + SUSPECT_SQL + " OR " + REVIEW_SQL + " = 'APPROVED'))";

    /** The list and all of its filters use the same alias and latest review. */
    public static final String LIST_STATUS_SQL = "CASE WHEN REGEXP_LIKE(LOWER(TRIM(COALESCE(o.shipping_email, ''))), '" +
            EMAIL_PATTERN + "', 'c') THEN COALESCE((SELECT fr.decision FROM order_fraud_reviews fr " +
            "WHERE fr.user_group = o.user_group COLLATE utf8mb4_unicode_ci AND fr.order_id = o.id " +
            "AND fr.email = LOWER(TRIM(o.shipping_email)) COLLATE utf8mb4_unicode_ci ORDER BY fr.id DESC LIMIT 1), 'PENDING') ELSE 'NORMAL' END";
}
