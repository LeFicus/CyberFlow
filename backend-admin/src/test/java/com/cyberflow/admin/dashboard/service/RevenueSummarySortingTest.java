package com.cyberflow.admin.dashboard.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RevenueSummarySortingTest {
    @Test
    void batchSiteCommissionUsesTheConfiguredBusinessBoundaries() {
        assertEquals(new BigDecimal("0.02"),
                RevenueSummaryService.batchSiteCommissionRate(new BigDecimal("49999.99")));
        assertEquals(new BigDecimal("0.04"),
                RevenueSummaryService.batchSiteCommissionRate(new BigDecimal("50000")));
        assertEquals(new BigDecimal("0.04"),
                RevenueSummaryService.batchSiteCommissionRate(new BigDecimal("150000")));
        assertEquals(new BigDecimal("0.06"),
                RevenueSummaryService.batchSiteCommissionRate(new BigDecimal("150000.01")));
    }

    @Test
    void personalAndMonthlyRowsUseDeduplicatedOrderDescendingOrder() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("A", "three", 3),
                row("B", "twelve", 12),
                row("A", "seven", 7)
        ));

        RevenueSummaryService.sortByDeduplicatedOrders(rows);

        assertEquals(List.of("twelve", "seven", "three"),
                rows.stream().map(value -> value.get("real_name")).toList());
    }

    private static Map<String, Object> row(String group, String name, int orders) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_group", group);
        row.put("real_name", name);
        row.put("deduplicated_orders", orders);
        return row;
    }
}
