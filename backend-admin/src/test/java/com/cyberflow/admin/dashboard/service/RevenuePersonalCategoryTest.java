package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.common.DataScope;
import com.cyberflow.admin.common.DataScopeService;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.dashboard.mapper.RevenueMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RevenuePersonalCategoryTest {
    @Test
    @SuppressWarnings("unchecked")
    void personalCategoriesIncludeOrderedSitesAndUsePersonalTotalForOverlappingTypes() {
        RevenueMapper mapper = mock(RevenueMapper.class);
        CrawlerConfigService config = mock(CrawlerConfigService.class);
        DataScopeService scopes = mock(DataScopeService.class);
        when(config.getRevenueConfig()).thenReturn(Map.of("departedEmployees", List.of()));
        when(scopes.current()).thenReturn(DataScope.all());
        when(mapper.adminOrderStats(eq("A"), any(), any(), eq("2026-10-01"), eq("2026-10-16")))
                .thenReturn(List.of(Map.of("admin_name", "alice", "user_group", "A", "total_orders", 4L)));
        when(mapper.adminOrderCategoryStats(eq("A"), any(), any(), eq("2026-10-01"), eq("2026-10-16")))
                .thenReturn(List.of(
                        Map.of("admin_name", "alice", "user_group", "A", "category_name", "家居",
                                "order_count", 3L, "ordered_site_count", 1L),
                        Map.of("admin_name", "alice", "user_group", "A", "category_name", "户外",
                                "order_count", 3L, "ordered_site_count", 2L)));

        var service = new RevenueSummaryService(mapper, config, scopes, new ObjectMapper());
        var summary = service.summarize("A", "2026-10-01", "2026-10-16");
        var person = ((List<Map<String, Object>>) summary.get("personal_performance")).get(0);
        var categories = (List<Map<String, Object>>) person.get("category_breakdown");

        assertEquals(4L, person.get("deduplicated_orders"));
        assertEquals(List.of("家居", "户外"), categories.stream().map(row -> row.get("category")).toList());
        assertEquals(List.of(1L, 2L), categories.stream().map(row -> row.get("ordered_site_count")).toList());
        // Six category memberships still represent four personal orders, so each type is 75%, not 50%.
        assertEquals(List.of(new BigDecimal("75.00"), new BigDecimal("75.00")),
                categories.stream().map(row -> row.get("ratio")).toList());
    }

    @Test
    void emptyPersonalOrdersDoNotProduceAnInvalidPercentage() {
        assertEquals(List.of(), RevenueSummaryService.categoryOrderBreakdown(Map.of(), Map.of(), 0));
        var category = RevenueSummaryService.categoryOrderBreakdown(Map.of("未分类", 1L), Map.of(), 0).get(0);
        assertEquals(BigDecimal.ZERO, category.get("ratio"));
        assertEquals(0L, category.get("ordered_site_count"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void leaderCategoriesUseWholeGroupOrdersAndDistinctGroupSiteCounts() {
        RevenueMapper mapper = mock(RevenueMapper.class);
        CrawlerConfigService config = mock(CrawlerConfigService.class);
        DataScopeService scopes = mock(DataScopeService.class);
        when(config.getRevenueConfig()).thenReturn(Map.of("departedEmployees", List.of()));
        when(scopes.current()).thenReturn(DataScope.all());
        when(mapper.groupOrderStats(eq("A"), any(), any())).thenReturn(List.of(
                Map.of("user_group", "A", "total_orders", 4L)));
        when(mapper.groupOrderCategoryStats(eq("A"), any(), any())).thenReturn(List.of(
                Map.of("user_group", "A", "category_name", "家居", "order_count", 3L, "ordered_site_count", 1L),
                Map.of("user_group", "A", "category_name", "户外", "order_count", 3L, "ordered_site_count", 2L)));

        var service = new RevenueSummaryService(mapper, config, scopes, new ObjectMapper());
        var result = service.summarize("A", "2026-10-01", "2026-10-16");
        var group = ((List<Map<String, Object>>) result.get("leader_summary")).get(0);
        var categories = (List<Map<String, Object>>) group.get("category_breakdown");

        assertEquals(List.of(1L, 2L), categories.stream().map(row -> row.get("ordered_site_count")).toList());
        assertEquals(List.of(new BigDecimal("75.00"), new BigDecimal("75.00")),
                categories.stream().map(row -> row.get("ratio")).toList());
    }
}
