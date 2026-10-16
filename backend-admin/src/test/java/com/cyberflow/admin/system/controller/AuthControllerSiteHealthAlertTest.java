package com.cyberflow.admin.system.controller;

import com.cyberflow.admin.common.JwtUtils;
import com.cyberflow.admin.crawler.health.SiteHealthPersonalAlertService;
import com.cyberflow.admin.system.entity.SysUser;
import com.cyberflow.admin.system.mapper.SysUserMapper;
import com.cyberflow.admin.system.service.SysMenuService;
import com.cyberflow.admin.system.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerSiteHealthAlertTest {
    private final SysUserService users = mock(SysUserService.class);
    private final SysUserMapper mapper = mock(SysUserMapper.class);
    private final JwtUtils tokens = mock(JwtUtils.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final SiteHealthPersonalAlertService alerts = mock(SiteHealthPersonalAlertService.class);
    private final SysUser user = new SysUser();
    private final AuthController controller = new AuthController(
            users, mock(SysMenuService.class), tokens, passwords, alerts);

    @BeforeEach
    void setUpLogin() {
        user.setId(7L);
        user.setUsername("employee");
        user.setPassword("encoded-password");
        user.setStatus(1);
        user.setDataOwner("B-员工");
        when(users.getByUsername("employee")).thenReturn(user);
        when(users.getBaseMapper()).thenReturn(mapper);
        when(passwords.matches("password", "encoded-password")).thenReturn(true);
        when(mapper.selectPermissionsByUserId(7L)).thenReturn(List.of("crawler:health:view"));
        when(tokens.generateToken(eq(7L), eq("employee"), anyList(), anyList())).thenReturn("test-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_OPERATOR"})
    void nonAdministratorsLogInWithoutClaimingOrReceivingAHealthReminder(String role) {
        when(mapper.selectRoleCodesByUserId(7L)).thenReturn(List.of(role));

        var response = controller.login(Map.of("username", "employee", "password", "password"));

        assertEquals(200, response.getCode());
        assertEquals("test-token", response.getData().get("token"));
        assertFalse(response.getData().containsKey("siteHealthAlert"));
        verifyNoInteractions(alerts);
    }

    @Test
    void administratorsCanStillReceiveTheirHealthReminder() {
        when(mapper.selectRoleCodesByUserId(7L)).thenReturn(List.of("ROLE_USER", "ROLE_ADMIN"));
        var alert = new SiteHealthPersonalAlertService.PersonalAlert("B-员工", 1L, List.of(), 1L);
        when(alerts.claimForLogin(user)).thenReturn(alert);

        var response = controller.login(Map.of("username", "employee", "password", "password"));

        assertEquals(alert, response.getData().get("siteHealthAlert"));
        verify(alerts).claimForLogin(user);
    }
}
