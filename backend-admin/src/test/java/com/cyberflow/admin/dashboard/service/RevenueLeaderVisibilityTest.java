package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.common.DataScope;
import com.cyberflow.admin.common.DataScopeService;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.dashboard.mapper.RevenueMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevenueLeaderVisibilityTest {
    @Test
    void operatorResponseDoesNotContainLeaderDataOrRules() {
        RevenueMapper mapper = mock(RevenueMapper.class);
        CrawlerConfigService configService = mock(CrawlerConfigService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        when(configService.getRevenueConfig()).thenReturn(Map.of("departedEmployees", List.of()));
        when(dataScopeService.current()).thenReturn(new DataScope(false, true, "alice", List.of(), java.util.Set.of()));
        when(mapper.adminOrderStats(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.adminOrderCountryStats(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.adminSiteStats(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.adminOrderCategoryStats(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.revenueOrdersByDomain(any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.revenueOrderCountriesByDomain(any(), any(), any(), any())).thenReturn(List.of());
        when(mapper.revenueSites(any(), any(), any(), any())).thenReturn(List.of());

        RevenueSummaryService service = new RevenueSummaryService(
                mapper, configService, dataScopeService, new ObjectMapper());
        Map<String, Object> result = service.summarize(null, "2026-09-01", "2026-09-23");

        assertFalse(result.containsKey("leader_summary"));
        assertFalse(result.containsKey("total_leader_group_commission_rmb"));
        assertFalse(result.containsKey("total_leader_commission_rmb"));
        Map<?, ?> parameters = (Map<?, ?>) result.get("parameters");
        assertFalse(parameters.containsKey("leader_commission_rate"));
        verify(mapper, never()).groupOrderStats(any(), any(), any());
        verify(mapper, never()).groupOrderCategoryStats(any(), any(), any());
        verify(mapper, never()).groupOrderCountryStats(any(), any(), any());
    }
}
