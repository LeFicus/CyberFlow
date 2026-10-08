package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.OrderMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderSiteDetailsQueryTest {
    @Test
    void orderListQueryIncludesRetiredSiteCategoryDetailsAndBuildType() {
        Configuration configuration = new Configuration();
        configuration.addMapper(OrderMapper.class);

        String sql = configuration.getMappedStatement(
                OrderMapper.class.getName() + ".listOrdersFiltered"
        ).getBoundSql(null).getSql();

        assertTrue(sql.contains("COALESCE(s.cat_names, r.cat_names) AS cat_names"));
        assertTrue(sql.contains("COALESCE(s.site_tag, r.site_tag, o.site_tag)"));
        assertTrue(sql.contains("LEFT JOIN retired_site_info r"));
    }
}
