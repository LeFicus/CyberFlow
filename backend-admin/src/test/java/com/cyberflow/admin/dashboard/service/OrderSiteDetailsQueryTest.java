package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.OrderMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderSiteDetailsQueryTest {
    @Test
    void orderListQueryIncludesCurrentSiteCategoryDetailsAndBuildType() {
        Configuration configuration = new Configuration();
        configuration.addMapper(OrderMapper.class);

        String sql = configuration.getMappedStatement(
                OrderMapper.class.getName() + ".listOrdersFiltered"
        ).getBoundSql(null).getSql();

        assertTrue(sql.contains("s.cat_names"));
        assertTrue(sql.contains("COALESCE(s.site_tag, o.site_tag)"));
    }
}
