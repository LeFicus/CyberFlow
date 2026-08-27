package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.common.DataScopeService;
import com.cyberflow.admin.dashboard.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductRedisFallbackTest {
    @Test
    void offlineRedisDoesNotThrowIntoMysqlTransaction() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForSet()).thenThrow(new IllegalStateException("offline"));
        DashboardService service = new DashboardService(mock(SiteInfoMapper.class), mock(OrderMapper.class),
            mock(SiteIndexingHistoryMapper.class), mock(EcommerceProductMapper.class), redis, mock(DataScopeService.class));
        Boolean cacheAvailable = ReflectionTestUtils.invokeMethod(service, "removeProductFingerprint",
            Map.of("sku", "A", "source_domain", "shop.test"));
        assertEquals(Boolean.FALSE, cacheAvailable);
    }
}
