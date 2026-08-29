package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.common.*;
import com.cyberflow.admin.dashboard.mapper.ProductQueryMapper;
import com.cyberflow.admin.dashboard.model.ProductFilter;
import com.cyberflow.admin.system.entity.SysUser;
import com.cyberflow.admin.system.mapper.SysUserMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductWorkspaceTest {
    @Test void validationAndLiteralPatterns() {
        ProductFilter raw = new ProductFilter();
        raw.setDomains(List.of(" HTTPS://Example.COM/path ", "example.com"));
        raw.setName("  20%_off! "); raw.setEndDate(LocalDate.of(2026, 8, 27));
        ProductFilter f = raw.normalized();
        assertEquals(List.of("example.com"), f.getDomains());
        assertEquals("20!%!_off!!%", f.getNamePattern());
        assertEquals("2026-08-28T00:00", f.getEndExclusive().toString());
        raw.setMinPrice(BigDecimal.TEN); raw.setMaxPrice(BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, raw::normalized);
        raw.setMinPrice(null); raw.setMaxPrice(null); raw.setProductRoles(List.of("bad"));
        assertThrows(IllegalArgumentException.class, raw::normalized);
    }

    @Test void allQueriesUseSharedFiltersAndEmptyScopeDeniesAll() throws Exception {
        Configuration config = new Configuration();
        try (var input = getClass().getResourceAsStream("/mapper/ProductQueryMapper.xml")) {
            new XMLMapperBuilder(input, config, "mapper/ProductQueryMapper.xml", config.getSqlFragments()).parse();
        }
        ProductFilter f = new ProductFilter(); f.setDomains(List.of("shop.test")); f.setSku("ab%");
        f.setProductCategories(List.of("Tools")); f.setMinPrice(BigDecimal.ONE);
        Map<String, Object> params = new HashMap<>();
        params.put("f", f.normalized()); params.put("allowed", List.of()); params.put("snapshot", 100L);
        params.put("before", 50L); params.put("after", 0L); params.put("limit", 20);
        for (String method : List.of("search", "count", "exportBatch")) {
            var sql = config.getMappedStatement(ProductQueryMapper.class.getName() + "." + method).getBoundSql(params).getSql();
            assertTrue(sql.contains("1 = 0")); assertTrue(sql.contains("p.source_domain IN"));
            assertTrue(sql.contains("p.sku LIKE")); assertTrue(sql.contains("p.regular_price >="));
            assertFalse(sql.contains("OFFSET")); assertFalse(sql.contains("shop.test"));
        }
    }

    @Test void cursorPaginationDoesNotCountAndUsesLastReturnedId() {
        ProductQueryMapper mapper = mock(ProductQueryMapper.class);
        var queries = spy(new ProductQueryService(mapper, mock(DataScopeService.class), mock(SysUserMapper.class)));
        doReturn("alice").when(queries).username(); doReturn(null).when(queries).allowedDomains("alice");
        when(mapper.maxId()).thenReturn(100L);
        when(mapper.search(any(), isNull(), eq(100L), isNull(), eq(3)))
                .thenReturn(List.of(Map.of("id", 100L), Map.of("id", 98L), Map.of("id", 97L)));
        var result = queries.search(new ProductFilter(), null, null, 2);
        assertEquals("98", result.get("nextCursor")); assertEquals(true, result.get("hasMore"));
        assertEquals(2, ((List<?>)result.get("list")).size());
        verify(mapper, never()).count(any(), any(), anyLong());
        assertThrows(IllegalArgumentException.class, () -> queries.search(new ProductFilter(), null, null, 201));
    }

    @Test void bulkDeletionCandidatesReuseWorkspaceFiltersScopeAndSnapshot() {
        ProductQueryMapper mapper = mock(ProductQueryMapper.class);
        var queries = spy(new ProductQueryService(mapper, mock(DataScopeService.class), mock(SysUserMapper.class)));
        doReturn("alice").when(queries).username(); doReturn(List.of("shop.test")).when(queries).allowedDomains("alice");
        when(mapper.maxId()).thenReturn(1_000L);
        when(mapper.search(any(), eq(List.of("shop.test")), eq(800L), isNull(), eq(500)))
                .thenReturn(List.of(Map.of("id", 800L), Map.of("id", 799L)));

        assertEquals(List.of(800L, 799L), queries.deletionCandidates(new ProductFilter(), 800L, 500));
        assertThrows(IllegalArgumentException.class,
                () -> queries.deletionCandidates(new ProductFilter(), 800L, 501));
    }

    @Test void scopeIsResolvedByTheBackendAndDisabledAccountsAreDenied() {
        var mapper = mock(ProductQueryMapper.class); var scopes = mock(DataScopeService.class); var users = mock(SysUserMapper.class);
        var service = new ProductQueryService(mapper, scopes, users);
        SysUser user = new SysUser(); user.setId(1L); user.setStatus(1);
        when(users.selectByUsername("alice")).thenReturn(user);
        when(users.selectPermissionsByUserId(1L)).thenReturn(List.of("dashboard:product:view"));
        when(scopes.forUsername("alice")).thenReturn(new DataScope(false, false, "Owner A"));
        when(mapper.scopeDomains(List.of("Owner A"))).thenReturn(List.of("www.shop.test"));
        assertEquals(List.of("shop.test", "www.shop.test"), service.allowedDomains("alice"));
        user.setStatus(0);
        assertThrows(AccessDeniedException.class, () -> service.allowedDomains("alice"));
    }
}
