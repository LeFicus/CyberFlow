package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.SiteIndexingHistoryMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="CYBERFLOW_PRODUCT_DB_TEST",matches="1")
class SiteIndexingGroupingDatabaseTest {
    @Test void groupedDrilldownsRetainExactIdentityIncludingUnassigned() throws Exception {
        var ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",System.getenv().getOrDefault("CYBERFLOW_TEST_DB_URL","jdbc:mysql://localhost:3306/cyberflow?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"),System.getenv().getOrDefault("CYBERFLOW_TEST_DB_USER","root"),System.getenv().getOrDefault("CYBERFLOW_TEST_DB_PASSWORD","123456"));
        var config=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));
        config.setLocalCacheScope(LocalCacheScope.STATEMENT);
        config.addMapper(SiteIndexingHistoryMapper.class);
        try (var session=new SqlSessionFactoryBuilder().build(config).openSession(true); var st=session.getConnection().createStatement()) {
            // Only site_info is temporary: MySQL cannot reference a temporary history table twice in a CTE.
            // Existing history is read-only and cannot add rows to the private site fixture.
            for(String table:List.of("site_info")) {
                String ddl;
                try(var rs=st.executeQuery("SHOW CREATE TABLE "+table)) { assertTrue(rs.next());ddl=rs.getString(2).replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE"); }
                st.execute(ddl);
            }
            st.executeUpdate("INSERT INTO site_info(site_domain,admin_name,builder_username,server_name,server_ip) VALUES('one.test','甲',NULL,'服务器',NULL),('two.test','乙',NULL,'服务器新',NULL),('three.test','甲','a','服务器','1.2.3.4')");
            var mapper=session.getMapper(SiteIndexingHistoryMapper.class);
            Map<String,Object> f=new HashMap<>();f.put("serverIpEmpty",false);
            assertEquals(3,mapper.countLatestSites(f));
            assertEquals(3,mapper.summarizeByBuilder(f).size());
            f.put("builderUsername","未分配");f.put("builderNameExact","甲");
            assertEquals(1,mapper.countLatestSites(f));
            assertEquals("one.test",mapper.listLatestSites(f,0,20).get(0).get("site_domain"));
            f.clear();f.put("serverNameExact","服务器");f.put("serverIpEmpty",true);
            assertEquals(1,mapper.countLatestSites(f));
            assertEquals("one.test",mapper.listLatestSites(f,0,20).get(0).get("site_domain"));
            f.put("serverIpEmpty",false);f.put("serverIp","1.2.3.4");
            assertEquals(1,mapper.countLatestSites(f));
            assertEquals("three.test",mapper.listLatestSites(f,0,20).get(0).get("site_domain"));
            f.put("ownerName","乙");assertEquals(0,mapper.countLatestSites(f));
        }
    }
}
