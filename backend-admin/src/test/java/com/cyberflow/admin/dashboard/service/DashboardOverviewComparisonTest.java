package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.common.DataScope;
import com.cyberflow.admin.common.DataScopeService;
import com.cyberflow.admin.dashboard.mapper.EcommerceProductMapper;
import com.cyberflow.admin.dashboard.mapper.OrderMapper;
import com.cyberflow.admin.dashboard.mapper.SiteIndexingHistoryMapper;
import com.cyberflow.admin.dashboard.mapper.SiteInfoMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class DashboardOverviewComparisonTest {
    @Test
    void comparesMonthToDateWithTheSameNumberOfDaysInThePreviousMonth() {
        SiteInfoMapper sites = mock(SiteInfoMapper.class);
        OrderMapper orders = mock(OrderMapper.class);
        EcommerceProductMapper products = mock(EcommerceProductMapper.class);
        DataScopeService scopes = mock(DataScopeService.class);
        when(scopes.current()).thenReturn(DataScope.all());
        when(products.countProductsByGroup(isNull(), isNull())).thenReturn(0L);
        when(orders.businessSummaryByGroup(any(), any(), isNull(), isNull()))
                .thenReturn(Map.of("deduplicated_orders", 4L))
                .thenReturn(Map.of("deduplicated_orders", 120L))
                .thenReturn(Map.of("deduplicated_orders", 100L));

        DashboardService service = new DashboardService(sites, orders,
                mock(SiteIndexingHistoryMapper.class), products,
                mock(StringRedisTemplate.class), scopes);

        Map<String, Object> overview = service.getOverview(null);

        assertEquals(120L, overview.get("month_deduplicated_orders"));
        assertEquals(100L, overview.get("previous_month_same_period_deduplicated_orders"));

        LocalDate currentEnd = LocalDate.parse((String) overview.get("month_same_period_end"));
        LocalDate previousStart = currentEnd.withDayOfMonth(1).minusMonths(1);
        LocalDate previousInclusiveEnd = previousStart.plusDays(
                Math.min(currentEnd.getDayOfMonth(), previousStart.lengthOfMonth()) - 1L);
        assertEquals(previousStart.toString(), overview.get("previous_month_same_period_start"));
        assertEquals(previousInclusiveEnd.toString(), overview.get("previous_month_same_period_end"));

        ArgumentCaptor<String> starts = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ends = ArgumentCaptor.forClass(String.class);
        verify(orders, times(3)).businessSummaryByGroup(starts.capture(), ends.capture(), isNull(), isNull());
        assertEquals(previousStart + " 00:00:00", starts.getAllValues().get(2));
        assertEquals(previousInclusiveEnd.plusDays(1) + " 00:00:00", ends.getAllValues().get(2));
    }
}
