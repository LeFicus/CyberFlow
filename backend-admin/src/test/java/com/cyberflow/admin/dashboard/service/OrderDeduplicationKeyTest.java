package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.OrderMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderDeduplicationKeyTest {
    @Test
    void businessFingerprintExcludesPaymentCardButKeepsRequiredDimensions() {
        String sql = OrderMapper.DEDUPLICATED_ORDER_KEY_SQL.toLowerCase();
        assertFalse(sql.contains("card_number"));
        assertTrue(sql.contains("dedupe_key"));
        assertTrue(sql.contains("user_group"));
        assertTrue(sql.contains("product_host"));
        assertTrue(sql.contains("shipping_email"));
        assertTrue(sql.contains("create_time"));
    }
}
