package com.cyberflow.admin.crawler.health;

import com.cyberflow.admin.crawler.health.mapper.SiteHealthMapper;
import com.cyberflow.admin.system.entity.SysUser;
import com.cyberflow.admin.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiteHealthPersonalAlertServiceTest {

    @Test
    void returnsEmployeeScopedFailuresOnlyOnTheFirstLoginOfTheDay() {
        SiteHealthMapper healthMapper = mock(SiteHealthMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SiteHealthPersonalAlertService service = new SiteHealthPersonalAlertService(healthMapper, userMapper);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setDataOwner("B-员工");

        when(healthMapper.countUnhealthyByAdmin("B-员工")).thenReturn(2L);
        when(userMapper.markSiteHealthNoticeShown(any(), any(LocalDate.class))).thenReturn(1);
        when(healthMapper.listUnhealthyByAdmin("B-员工", 20)).thenReturn(List.of(Map.of(
                "site_domain", "broken.example",
                "reason", "响应超时",
                "server_name", "站群服务器-01",
                "consecutive_failures", 3,
                "failure_since", LocalDateTime.of(2026, 9, 25, 8, 0),
                "checked_at", LocalDateTime.of(2026, 9, 25, 9, 0))));

        var alert = service.claimForLogin(user);

        assertEquals(2, alert.total());
        assertEquals(1, alert.sites().size());
        assertEquals(1, alert.remaining());
        verify(userMapper).markSiteHealthNoticeShown(any(), any(LocalDate.class));
    }

    @Test
    void doesNotRepeatTheReminderAfterItWasClaimedToday() {
        SiteHealthMapper healthMapper = mock(SiteHealthMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SiteHealthPersonalAlertService service = new SiteHealthPersonalAlertService(healthMapper, userMapper);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setDataOwner("B-员工");

        when(healthMapper.countUnhealthyByAdmin("B-员工")).thenReturn(1L);
        when(userMapper.markSiteHealthNoticeShown(any(), any(LocalDate.class))).thenReturn(0);

        assertNull(service.claimForLogin(user));
    }
}
