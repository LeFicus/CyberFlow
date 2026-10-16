package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.RevenueMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevenueCategoryBreakdownQueryTest {
    @Test
    void categoryBreakdownUsesDateFilteredDeduplicatedOrdersInsteadOfSiteCounts() {
        Configuration configuration = new Configuration();
        configuration.addMapper(RevenueMapper.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userGroup", "A");
        parameters.put("ownerName", null);
        parameters.put("teacherSuffixes", List.of());
        parameters.put("startDate", "2026-09-01");
        parameters.put("endDate", "2026-09-22");

        String sql = configuration.getMappedStatement(
                RevenueMapper.class.getName() + ".adminOrderCategoryStats"
        ).getBoundSql(parameters).getSql();

        assertTrue(sql.contains("SELECT DISTINCT admin_name, user_group"));
        assertTrue(sql.contains("AS order_identity"));
        assertTrue(sql.contains("create_time >= CONCAT(?, ' 00:00:00')"));
        assertTrue(sql.contains("create_time < DATE_ADD(?, INTERVAL 1 DAY)"));
        assertTrue(sql.contains("COUNT(DISTINCT filtered_orders.order_identity) AS order_count"));
        assertTrue(sql.contains("COUNT(DISTINCT NULLIF(filtered_orders.normalized_host, '')) AS ordered_site_count"));
        assertTrue(sql.contains("JOIN JSON_TABLE"));
        assertTrue(sql.contains("LEFT JOIN retired_site_info r"));
        assertTrue(sql.contains("COALESCE(s.cat_names, r.cat_names)"));
        assertFalse(sql.contains("site_created_before"));
        assertFalse(sql.contains("AS site_count"));
    }
}
