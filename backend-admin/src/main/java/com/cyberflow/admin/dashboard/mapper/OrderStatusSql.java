package com.cyberflow.admin.dashboard.mapper;

/** Local is_fraud maps platform is_alarm != 0 to risk; platform is_valid 0=valid, 1=invalid. */
public final class OrderStatusSql {
    private OrderStatusSql() {}
    public static final String RISK_SQL = "is_fraud = 1";
    public static final String VALID_SQL = "(is_valid = 0 AND is_fraud = 0)";
    public static final String LIST_STATUS_SQL = "CASE WHEN o.is_fraud = 1 THEN 'RISK' " +
            "WHEN o.is_fraud = 0 THEN 'NORMAL' ELSE 'UNKNOWN' END";
}
