package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.RevenueMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevenueSiteConversionQueryTest {
    @Test
    void personalSiteStatsUseAllSitesAndDateFilteredValidOrderSites() {
        Configuration configuration = new Configuration();
        configuration.addMapper(RevenueMapper.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userGroup", null);
        parameters.put("ownerName", null);
        parameters.put("teacherSuffixes", List.of());
        parameters.put("startDate", "2026-09-01");
        parameters.put("endDate", "2026-09-23");

        String sql = configuration.getMappedStatement(
                RevenueMapper.class.getName() + ".adminSiteStats"
        ).getBoundSql(parameters).getSql();

        assertTrue(sql.contains("COUNT(*) AS site_count"));
        assertTrue(sql.contains("COUNT(valid_sites.product_host) AS valid_ordered_site_count"));
        assertTrue(sql.contains("FROM retired_site_info r"));
        assertTrue(sql.contains("WHERE is_valid = 0"));
        assertTrue(sql.contains("create_time >= CONCAT(?, ' 00:00:00')"));
        assertTrue(sql.contains("create_time < DATE_ADD(?, INTERVAL 1 DAY)"));
        assertFalse(sql.contains("site_created_before"));
        assertFalse(sql.contains("siteCreatedBefore"));

        parameters.put("siteCreatedMonth", "2026-09");
        String cohortSql = configuration.getMappedStatement(
                RevenueMapper.class.getName() + ".revenueSites"
        ).getBoundSql(parameters).getSql();
        assertTrue(cohortSql.contains("FROM retired_site_info r"));
        assertTrue(cohortSql.contains("DATE_FORMAT(s.domain_applied_at, '%Y-%m') AS site_month"));
        assertTrue(cohortSql.contains("AND s.domain_applied_at IS NOT NULL"));
        assertFalse(cohortSql.contains("OR s.domain_applied_at IS NULL"));
        assertFalse(cohortSql.contains("COALESCE(s.domain_applied_at, s.created_at)"));
    }
}
