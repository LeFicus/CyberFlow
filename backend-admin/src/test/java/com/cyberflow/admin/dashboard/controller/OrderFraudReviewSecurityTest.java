package com.cyberflow.admin.dashboard.controller;

import com.cyberflow.admin.dashboard.mapper.OrderFraudReviewMapper;
import com.cyberflow.admin.dashboard.service.OrderFraudReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = OrderFraudReviewSecurityTest.Config.class)
class OrderFraudReviewSecurityTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean OrderFraudReviewMapper mapper() { return mock(OrderFraudReviewMapper.class); }
        @Bean OrderFraudReviewService service() { return mock(OrderFraudReviewService.class); }
        @Bean OrderFraudReviewController controller(OrderFraudReviewService service, OrderFraudReviewMapper mapper) {
            return new OrderFraudReviewController(service, mapper);
        }
    }
    @Autowired OrderFraudReviewController controller;
    @Autowired OrderFraudReviewService service;
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); reset(service); }

    @Test void ordinaryUsersCannotReadOrWriteReviewsEvenWithOrderViewAccess() {
        var auth = new TestingAuthenticationToken("employee", "unused", "ROLE_USER", "dashboard:order:view");
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThrows(AccessDeniedException.class, () -> controller.history("A", 42));
        assertThrows(AccessDeniedException.class, () -> controller.review("A", 42,
                new OrderFraudReviewController.ReviewRequest("a", "APPROVED", "核实"), auth));
        verifyNoInteractions(service);
    }
    @Test void administratorWithoutOrderViewCannotReview() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "unused", "ROLE_ADMIN"));
        assertThrows(AccessDeniedException.class, () -> controller.history("A", 42));
    }
    @Test void administratorIdentityComesFromAuthenticatedSession() {
        var auth = new TestingAuthenticationToken("reviewer-admin", "unused", "ROLE_ADMIN", "dashboard:order:view");
        SecurityContextHolder.getContext().setAuthentication(auth);
        controller.review("A", 42, new OrderFraudReviewController.ReviewRequest("a", "REJECTED", "核实"), auth);
        verify(service).review("A", 42, "a", "REJECTED", "核实", "reviewer-admin");
        assertEquals(200, controller.history("A", 42).getCode());
    }
}
