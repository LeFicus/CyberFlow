package com.cyberflow.admin.newsite.service;

import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.dashboard.mapper.EcommerceProductMapper;
import com.cyberflow.admin.newsite.entity.NewSite;
import com.cyberflow.admin.newsite.mapper.NewSiteMapper;
import com.cyberflow.admin.system.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NewSiteServiceTest {
    private NewSiteMapper mapper;
    private EcommerceProductMapper products;
    private NewSiteService service;

    @BeforeEach
    void setUp() {
        mapper = mock(NewSiteMapper.class);
        products = mock(EcommerceProductMapper.class);
        service = new NewSiteService(mapper, products, mock(DeepSeekSiteGenerationService.class),
                mock(DomainAvailabilityService.class), mock(SysUserMapper.class),
                new ObjectMapper(), mock(CrawlerConfigService.class));
    }

    @Test
    void deletesOnlyTheRequestedGeneratedRecord() {
        when(mapper.deleteById(42L)).thenReturn(1);
        service.delete(42L);
        verify(mapper).deleteById(42L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(products);
    }

    @Test
    void missingOrAlreadyDeletedRecordIsNotReportedAsSuccess() {
        when(mapper.deleteById(42L)).thenReturn(0);
        var error = assertThrows(IllegalArgumentException.class, () -> service.delete(42L));
        assertEquals("新站点不存在或已删除：42", error.getMessage());
    }

    @Test
    void rejectsInvalidIdsWithoutTouchingTheDatabase() {
        assertThrows(IllegalArgumentException.class, () -> service.delete(null));
        assertThrows(IllegalArgumentException.class, () -> service.delete(0L));
        assertThrows(IllegalArgumentException.class, () -> service.delete(-1L));
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending_review", "enabled", "disabled"})
    void acceptsEachDisplayedStatus(String status) {
        NewSite site = new NewSite();
        site.setId(42L);
        site.setStatus("pending_review");
        when(mapper.selectById(42L)).thenReturn(site);
        assertEquals(status, service.updateStatus(42L, status).getStatus());
        verify(mapper).updateById(site);
    }

    @Test
    void rejectsUnknownStatusWithoutWriting() {
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(42L, "deleted"));
        verifyNoInteractions(mapper);
    }

    @Test
    void cannotUpdateADeletedRecord() {
        when(mapper.selectById(42L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(42L, "enabled"));
        verify(mapper, never()).updateById(any(NewSite.class));
    }
}
