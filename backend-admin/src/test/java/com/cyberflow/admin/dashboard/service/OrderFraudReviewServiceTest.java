package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.OrderFraudReviewMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderFraudReviewServiceTest {
    @Test
    void persistsNormalizedEmailDecisionActorAndRequiredEvidence() {
        var mapper = mock(OrderFraudReviewMapper.class);
        when(mapper.lockOrder("A", 42)).thenReturn(Map.of("shipping_email", " Alice.Smith.12345@GMAIL.COM ", "suspect", 1));
        var service = new OrderFraudReviewService(mapper);
        service.review("A", 42, "alice.smith.12345@gmail.com", "APPROVED", " 已核实 ", "admin");
        verify(mapper).insertReview("A", 42, "alice.smith.12345@gmail.com", "APPROVED", "admin", "已核实");
    }

    @Test
    void staleEmailNormalOrderMissingOrderAndInvalidDecisionCannotBeApproved() {
        var mapper = mock(OrderFraudReviewMapper.class);
        var service = new OrderFraudReviewService(mapper);
        assertThrows(IllegalArgumentException.class, () -> service.review("A", 42, "a", null, "核实", "admin"));
        assertThrows(IllegalArgumentException.class, () -> service.review("A", 42, "a", "APPROVED", " ", "admin"));
        assertThrows(IllegalArgumentException.class, () -> service.review("A", 42, "a", "APPROVED", "a".repeat(501), "admin"));
        assertThrows(IllegalArgumentException.class, () -> service.review("A", 42, "a", "APPROVED", "核实", "admin"));
        when(mapper.lockOrder("A", 42)).thenReturn(Map.of("shipping_email", "new.name.12345@gmail.com", "suspect", 1));
        assertThrows(IllegalArgumentException.class, () -> service.review("A", 42, "old.name.12345@gmail.com", "APPROVED", "核实", "admin"));
        when(mapper.lockOrder("A", 42)).thenReturn(Map.of("shipping_email", "normal@example.com", "suspect", 0));
        assertThrows(IllegalArgumentException.class, () -> service.review("A", 42, "normal@example.com", "APPROVED", "核实", "admin"));
        verify(mapper, never()).insertReview(any(), anyLong(), any(), any(), any(), any());
    }
}
