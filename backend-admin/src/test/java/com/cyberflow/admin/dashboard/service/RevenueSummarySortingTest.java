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
    void batchSiteCommissionUsesTheFixedBusinessBoundaries() {
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
    void regularCommissionUsesTheFixedBusinessBoundaries() {
        assertEquals(new BigDecimal("0.03"),
                RevenueSummaryService.regularCommissionRate(new BigDecimal("30000")));
        assertEquals(new BigDecimal("0.05"),
                RevenueSummaryService.regularCommissionRate(new BigDecimal("30000.01")));
        assertEquals(new BigDecimal("0.05"),
                RevenueSummaryService.regularCommissionRate(new BigDecimal("80000")));
        assertEquals(new BigDecimal("0.08"),
                RevenueSummaryService.regularCommissionRate(new BigDecimal("80000.01")));
    }

    @Test
    void classificationBreakdownUsesDeduplicatedOrderRatios() {
        List<Map<String, Object>> breakdown = RevenueSummaryService.classificationBreakdown(6, 3, 1);

        assertEquals(List.of("单独建站", "批量建站", "复制站"),
                breakdown.stream().map(item -> item.get("label")).toList());
        assertEquals(List.of(6L, 3L, 1L),
                breakdown.stream().map(item -> item.get("orders")).toList());
        assertEquals(List.of(new BigDecimal("60.00"), new BigDecimal("30.00"), new BigDecimal("10.00")),
                breakdown.stream().map(item -> item.get("ratio")).toList());
    }

    @Test
    void categoryBreakdownSortsCategoriesAndCalculatesSiteLabelRatios() {
        Map<String, Long> categories = new LinkedHashMap<>();
        categories.put("户外用品", 2L);
        categories.put("家居用品", 3L);
        categories.put("玩具", 1L);

        List<Map<String, Object>> breakdown = RevenueSummaryService.categoryBreakdown(categories);

        assertEquals(List.of("家居用品", "户外用品", "玩具"),
                breakdown.stream().map(item -> item.get("category")).toList());
        assertEquals(List.of(new BigDecimal("50.00"), new BigDecimal("33.33"), new BigDecimal("16.67")),
                breakdown.stream().map(item -> item.get("ratio")).toList());
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
