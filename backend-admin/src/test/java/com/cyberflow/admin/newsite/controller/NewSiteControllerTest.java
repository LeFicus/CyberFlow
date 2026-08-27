package com.cyberflow.admin.newsite.controller;

import com.cyberflow.admin.common.GlobalExceptionHandler;
import com.cyberflow.admin.newsite.service.NewSiteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NewSiteControllerTest.Config.class)
class NewSiteControllerTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean NewSiteService service() { return mock(NewSiteService.class); }
        @Bean NewSiteController controller(NewSiteService service) { return new NewSiteController(service); }
    }

    @Autowired private NewSiteController controller;
    @Autowired private NewSiteService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test-user", null,
                        AuthorityUtils.createAuthorityList(authority)));
    }

    @Test
    void deleteEndpointReturnsTheStandardSuccessEnvelope() throws Exception {
        authenticate("newsite:delete");
        mvc.perform(delete("/admin/new-site/42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).delete(42L);
    }

    @Test
    void listPermissionDoesNotAllowDeletion() throws Exception {
        authenticate("newsite:list");
        mvc.perform(delete("/admin/new-site/42"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(service);
    }

    @Test
    void statusPermissionDoesNotAllowDeletion() throws Exception {
        authenticate("newsite:status");
        mvc.perform(delete("/admin/new-site/42")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void missingRecordReturnsAnActionableError() throws Exception {
        authenticate("newsite:delete");
        doThrow(new IllegalArgumentException("新站点不存在或已删除：42")).when(service).delete(42L);
        mvc.perform(delete("/admin/new-site/42"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("新站点不存在或已删除：42"));
    }
}
