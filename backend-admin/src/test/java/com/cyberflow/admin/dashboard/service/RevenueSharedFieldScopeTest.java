package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.common.DataScope;
import com.cyberflow.admin.common.DataScopeService;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.dashboard.mapper.RevenueMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RevenueSharedFieldScopeTest {
    @Test
    @SuppressWarnings("unchecked")
    void retiredSiteWithoutApplicationDateIsExcludedFromMonthlyConversion() {
        RevenueMapper mapper = mock(RevenueMapper.class);
        CrawlerConfigService config = mock(CrawlerConfigService.class);
        DataScopeService scopes = mock(DataScopeService.class);
        when(config.getRevenueConfig()).thenReturn(Map.of("departedEmployees", List.of()));
        when(scopes.current()).thenReturn(DataScope.all());

        Map<String, Object> site = new LinkedHashMap<>();
        site.put("site_domain", "retired.example");
        site.put("admin_name", "A-成员");
        site.put("user_group", "A");
        site.put("site_month", null);
        when(mapper.revenueSites(any(), any(), any(), eq("2026-09"))).thenReturn(List.of(site));
        when(mapper.revenueOrdersByDomain(any(), any(), any(), any())).thenReturn(List.of(Map.of(
                "product_host", "retired.example", "total_orders", 2L,
                "valid_orders", 2L, "successful_orders", 1L, "successful_amount", 10.0)));

        var service = new RevenueSummaryService(mapper, config, scopes, new ObjectMapper());
        Map<String, Object> result = service.summarize("A", "2026-09-01", "2026-09-30", "2026-09");
        assertEquals(List.of(), result.get("monthly_conversion"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sharedPerformanceRowsOnlyContainGrantedFields() {
        RevenueMapper mapper = mock(RevenueMapper.class);
        CrawlerConfigService config = mock(CrawlerConfigService.class);
        DataScopeService scopes = mock(DataScopeService.class);
        when(config.getRevenueConfig()).thenReturn(Map.of("departedEmployees", List.of()));
        when(scopes.current()).thenReturn(new DataScope(false, false, "A-本人", List.of("A-成员"),
                Set.of("performance.site_count")));
        Map<String, Object> orders = new LinkedHashMap<>();
        orders.put("admin_name", "A-成员");
        orders.put("user_group", "A");
        orders.put("total_orders", 8L);
        orders.put("valid_orders", 6L);
        orders.put("successful_orders", 4L);
        orders.put("original_amount", 120.0);
        when(mapper.adminOrderStats(any(), eq("A-本人,A-成员"), any(), any(), any()))
                .thenReturn(List.of(orders));
        Map<String, Object> sites = new LinkedHashMap<>();
        sites.put("admin_name", "A-成员");
        sites.put("user_group", "A");
        sites.put("site_count", 20L);
        when(mapper.adminSiteStats(any(), eq("A-本人,A-成员"), any(), any(), any()))
                .thenReturn(List.of(sites));
        when(mapper.adminOrderCountryStats(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.adminOrderCategoryStats(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.revenueOrdersByDomain(any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.revenueOrderCountriesByDomain(any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.revenueSites(any(), any(), any(), any())).thenReturn(List.of());

        var service = new RevenueSummaryService(mapper, config, scopes, new ObjectMapper());
        Map<String, Object> result = service.summarize(null, "2026-09-01", "2026-09-24");
        Map<String, Object> sharedRow = ((List<Map<String, Object>>) result.get("personal_performance")).get(0);

        assertEquals(Map.of("site_count", 20L, "batch_site_count", 0L), sharedRow);
    }

    @Test
    @SuppressWarnings("unchecked")
    void departedEmployeesAreHiddenOnlyFromPersonalPerformance() {
        RevenueMapper mapper = mock(RevenueMapper.class);
        CrawlerConfigService config = mock(CrawlerConfigService.class);
        DataScopeService scopes = mock(DataScopeService.class);
        when(config.getRevenueConfig()).thenReturn(Map.of(
                "exchangeRate", 6.73,
                "rateFactor", 0.42,
                "departedEmployees", List.of("A-离职")));
        when(scopes.current()).thenReturn(new DataScope(false, false, "A-离职", List.of(), Set.of()));

        Map<String, Object> orders = new LinkedHashMap<>();
        orders.put("admin_name", "A-离职");
        orders.put("user_group", "A");
        orders.put("total_orders", 8L);
        orders.put("valid_orders", 6L);
        orders.put("successful_orders", 4L);
        orders.put("original_amount", 120.0);
        when(mapper.adminOrderStats(any(), eq("A-离职"), any(), any(), any()))
                .thenReturn(List.of(orders));

        Map<String, Object> sites = new LinkedHashMap<>();
        sites.put("admin_name", "A-离职");
        sites.put("user_group", "A");
        sites.put("site_count", 20L);
        when(mapper.adminSiteStats(any(), eq("A-离职"), any(), any(), any()))
                .thenReturn(List.of(sites));
        when(mapper.adminOrderCountryStats(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.adminOrderCategoryStats(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.revenueOrdersByDomain(any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.revenueOrderCountriesByDomain(any(), any(), any(), any())).thenReturn(List.of());

        Map<String, Object> revenueSite = new LinkedHashMap<>();
        revenueSite.put("admin_name", "A-离职");
        revenueSite.put("user_group", "A");
        revenueSite.put("site_month", "2026-09");
        revenueSite.put("site_domain", "departed.example");
        when(mapper.revenueSites(any(), eq("A-离职"), any(), eq("2026-09")))
                .thenReturn(List.of(revenueSite));

        var service = new RevenueSummaryService(mapper, config, scopes, new ObjectMapper());
        Map<String, Object> result = service.summarize(null, "2026-09-01", "2026-09-24", "2026-09");

        assertEquals(List.of(), result.get("personal_performance"));
        assertEquals(1, ((List<Map<String, Object>>) result.get("monthly_conversion")).size());
        assertEquals(new BigDecimal("10.18"), result.get("total_member_commission_rmb"));
    }
}
