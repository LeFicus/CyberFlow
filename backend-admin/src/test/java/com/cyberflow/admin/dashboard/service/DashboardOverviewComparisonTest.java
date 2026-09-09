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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class DashboardOverviewComparisonTest {
    @Test
    void includesYesterdayAndProjectsMonthToDateAtTheCurrentRunRate() {
        SiteInfoMapper sites = mock(SiteInfoMapper.class);
        OrderMapper orders = mock(OrderMapper.class);
        EcommerceProductMapper products = mock(EcommerceProductMapper.class);
        DataScopeService scopes = mock(DataScopeService.class);
        when(scopes.current()).thenReturn(DataScope.all());
        when(products.countProductsByGroup(isNull(), isNull())).thenReturn(0L);
        when(orders.businessSummaryByGroup(any(), any(), isNull(), isNull()))
                .thenReturn(Map.of("deduplicated_orders", 4L, "valid_deduplicated_orders", 3L, "successful_orders", 3L, "successful_amount", new BigDecimal("40")))
                .thenReturn(Map.of("deduplicated_orders", 6L, "valid_deduplicated_orders", 5L, "successful_orders", 5L, "successful_amount", new BigDecimal("60")))
                .thenReturn(Map.of("deduplicated_orders", 120L, "valid_deduplicated_orders", 110L, "successful_amount", new BigDecimal("300")))
                .thenReturn(Map.of("deduplicated_orders", 100L));

        DashboardService service = new DashboardService(sites, orders,
                mock(SiteIndexingHistoryMapper.class), products,
                mock(StringRedisTemplate.class), scopes);

        Map<String, Object> overview = service.getOverview(null);

        assertEquals(6L, overview.get("yesterday_deduplicated_orders"));
        assertEquals(5L, overview.get("yesterday_valid_deduplicated_orders"));
        assertEquals(5L, overview.get("yesterday_successful_orders"));
        assertEquals(new BigDecimal("60"), overview.get("yesterday_successful_amount"));
        assertEquals(120L, overview.get("month_deduplicated_orders"));
        assertEquals(110L, overview.get("month_valid_deduplicated_orders"));
        assertEquals(100L, overview.get("previous_month_same_period_deduplicated_orders"));

        LocalDate currentEnd = LocalDate.parse((String) overview.get("month_same_period_end"));
        int elapsedDays = currentEnd.getDayOfMonth();
        int daysInMonth = currentEnd.lengthOfMonth();
        long expectedOrders = new BigDecimal("120").multiply(BigDecimal.valueOf(daysInMonth))
                .divide(BigDecimal.valueOf(elapsedDays), 0, RoundingMode.HALF_UP).longValue();
        BigDecimal expectedAmount = new BigDecimal("300").multiply(BigDecimal.valueOf(daysInMonth))
                .divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP);
        assertEquals(expectedOrders, overview.get("month_forecast_deduplicated_orders"));
        assertEquals(expectedAmount, overview.get("month_forecast_successful_amount"));
        assertEquals(elapsedDays, overview.get("month_forecast_elapsed_days"));
        assertEquals(daysInMonth, overview.get("month_forecast_days_in_month"));

        LocalDate previousStart = currentEnd.withDayOfMonth(1).minusMonths(1);
        LocalDate previousInclusiveEnd = previousStart.plusDays(
                Math.min(currentEnd.getDayOfMonth(), previousStart.lengthOfMonth()) - 1L);
        assertEquals(previousStart.toString(), overview.get("previous_month_same_period_start"));
        assertEquals(previousInclusiveEnd.toString(), overview.get("previous_month_same_period_end"));

        ArgumentCaptor<String> starts = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ends = ArgumentCaptor.forClass(String.class);
        verify(orders, times(4)).businessSummaryByGroup(starts.capture(), ends.capture(), isNull(), isNull());
        assertEquals(currentEnd.minusDays(1) + " 00:00:00", starts.getAllValues().get(1));
        assertEquals(currentEnd + " 00:00:00", ends.getAllValues().get(1));
        assertEquals(previousStart + " 00:00:00", starts.getAllValues().get(3));
        assertEquals(previousInclusiveEnd.plusDays(1) + " 00:00:00", ends.getAllValues().get(3));
    }
}
