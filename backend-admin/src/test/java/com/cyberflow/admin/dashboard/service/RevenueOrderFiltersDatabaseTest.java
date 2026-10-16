package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.OrderMapper;
import com.cyberflow.admin.dashboard.mapper.RevenueMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Disposable, randomly named test schemas keep fixture writes separate from business tables. */
@EnabledIfEnvironmentVariable(named = "CYBERFLOW_REPORT_DB_TEST", matches = "1")
class RevenueOrderFiltersDatabaseTest {
    private record Fixture(SqlSession session, String schema) implements AutoCloseable {
        <T> T getMapper(Class<T> type) { return session.getMapper(type); }
        @Override
        public void close() throws Exception {
            try (var sql = session.getConnection().createStatement()) {
                sql.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            } finally {
                session.close();
            }
        }
    }

    private Fixture fixture() throws Exception {
        String url = System.getenv().getOrDefault("CYBERFLOW_TEST_DB_URL",
                "jdbc:mysql://127.0.0.1:3306/?serverTimezone=Asia/Shanghai");
        int schemaStart = url.indexOf('/', "jdbc:mysql://".length());
        int queryStart = url.indexOf('?', schemaStart);
        // Start without any database selected, even if the supplied URL names a business database.
        url = url.substring(0, schemaStart + 1) + (queryStart < 0 ? "" : url.substring(queryStart));
        var datasource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                url,
                System.getenv().getOrDefault("CYBERFLOW_TEST_DB_USER", "root"),
                System.getenv("CYBERFLOW_TEST_DB_PASSWORD"));
        var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), datasource));
        configuration.addMapper(OrderMapper.class);
        configuration.addMapper(RevenueMapper.class);
        var session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        String schema = "cyberflow_report_test_" + UUID.randomUUID().toString().replace("-", "");
        var fixture = new Fixture(session, schema);
        try {
            try (Statement create = session.getConnection().createStatement()) {
                create.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            session.getConnection().setCatalog(schema);
            try (Statement sql = session.getConnection().createStatement()) {
                try (var current = sql.executeQuery("SELECT DATABASE()")) {
                    assertTrue(current.next());
                    assertEquals(schema, current.getString(1));
                }
                sql.execute("CREATE TABLE site_info (site_domain VARCHAR(255), admin_name VARCHAR(100), " +
                        "user_group VARCHAR(32), site_tag INT, cat_names JSON, product_category VARCHAR(100), " +
                        "domain_applied_at DATETIME, created_at DATETIME) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                sql.execute("CREATE TABLE retired_site_info LIKE site_info");
                sql.execute("CREATE TABLE orders (id BIGINT, amount DECIMAL(10,2), currency VARCHAR(10), " +
                        "create_time DATETIME, product_host VARCHAR(255), pay_status_text VARCHAR(50), " +
                        "customer_ip_country VARCHAR(100), shipping_email VARCHAR(255), shipping_address JSON, " +
                        "admin_name VARCHAR(100), user_group VARCHAR(32), theme_name VARCHAR(100), " +
                        "product_category VARCHAR(100), site_tag INT, product_info JSON, is_valid INT, dedupe_key VARCHAR(64)) " +
                        "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                sql.executeUpdate("INSERT INTO site_info VALUES " +
                        "('live.test','alice','A',1,JSON_ARRAY('家居','家居','户外'),NULL,'2026-09-15','2026-10-01')," +
                        "('plain.test','alice','A',0,JSON_ARRAY('家居'),NULL,'2026-10-05','2026-09-01')," +
                        "('missing.test','alice','A',0,JSON_ARRAY('玩具'),NULL,NULL,'2026-10-07')");
                sql.executeUpdate("INSERT INTO retired_site_info VALUES " +
                        "('live.test','alice','A',2,JSON_ARRAY('旧分类'),NULL,'2026-08-01','2026-08-01')," +
                        "('retired.test','alice','A',2,JSON_ARRAY('玩具'),NULL,'2026-09-20','2026-10-02')," +
                        "('missing-retired.test','alice','A',0,JSON_ARRAY('玩具'),NULL,NULL,'2026-09-07')");
                sql.executeUpdate("INSERT INTO orders (id,amount,currency,create_time,product_host,pay_status_text," +
                        "shipping_email,admin_name,user_group,site_tag,is_valid,dedupe_key) VALUES " +
                        "(1,10,'USD','2026-10-02','WWW.LIVE.TEST','已支付','1@example.test','alice','A',0,0,'one')," +
                        "(2,20,'USD','2026-10-03','retired.test','支付失败','2@example.test','alice','A',0,0,'two')," +
                        "(3,30,'USD','2026-10-04','unknown.test','已支付','3@example.test','alice','A',0,0,'three')," +
                        "(4,40,'USD','2026-10-05','plain.test','已支付','4@example.test','alice','A',1,0,'four')," +
                        "(5,50,'USD','2026-10-06','live.test','支付失败','5@example.test','alice','A',0,1,'five')," +
                        "(6,60,'USD','2026-10-06','live.test','已支付','6@example.test','','B',0,0,'six')");
            }
        } catch (Exception | AssertionError error) {
            fixture.close();
            throw error;
        }
        return fixture;
    }

    @Test
    void buildTypeFiltersHaveMatchingRowsTotalsAndAmountsWithActiveRetiredAndSnapshotFallbacks() throws Exception {
        try (var session = fixture()) {
            var orders = session.getMapper(OrderMapper.class);
            for (int type = 0; type <= 2; type++) {
                int expectedType = type;
                var rows = orders.listOrdersFiltered(null, null, "A", null, null, null, null,
                        null, "2026-10-01", "2026-10-16", 0, 10, type);
                long total = orders.countOrdersFiltered(null, null, "A", null, null, null, null,
                        null, "2026-10-01", "2026-10-16", type);
                var summary = orders.summarizeOrdersFiltered(null, null, "A", null, null, null, null,
                        null, "2026-10-01", "2026-10-16", type);
                assertEquals(total, rows.size());
                assertEquals(total, ((Number) summary.get("total_count")).longValue());
                assertTrue(rows.stream().allMatch(row -> ((Number) row.get("site_tag")).intValue() == expectedType));
                assertEquals(rows.stream().map(row -> (BigDecimal) row.get("amount"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add), summary.get("total_amount"));
                assertEquals(type == 2 ? 1 : 2, total);
            }
            var paid = orders.summarizeOrdersFiltered(null, null, "A", "live.test", "已支付", "USD", null,
                    "alice", "2026-10-01", "2026-10-16", 1);
            assertEquals(1L, ((Number) paid.get("paid_count")).longValue());
            assertEquals(new BigDecimal("10.00"), paid.get("paid_amount"));
            assertEquals(6L, orders.countOrdersFiltered(null, null, null, null, null, null, null,
                    null, null, null, null));
        }
    }

    @Test
    void cohortsUseApplicationDatesAndExcludeMissingDatesDespiteHavingAnImportDate() throws Exception {
        try (var session = fixture()) {
            var mapper = session.getMapper(RevenueMapper.class);
            var september = mapper.revenueSites("A", null, List.of(), "2026-09");
            assertEquals(Set.of("live.test", "retired.test"),
                    new HashSet<>(september.stream().map(row -> row.get("site_domain")).toList()));
            var october = mapper.revenueSites("A", null, List.of(), "2026-10");
            assertEquals(Set.of("plain.test"),
                    new HashSet<>(october.stream().map(row -> row.get("site_domain")).toList()));
            var all = mapper.revenueSites("A", null, List.of(), null);
            assertEquals(Set.of("live.test", "retired.test", "plain.test"),
                    new HashSet<>(all.stream().map(row -> row.get("site_domain")).toList()));
        }
    }

    @Test
    void groupTypesCountDistinctSitesAndIncludeOrdersWithoutAnAssignedAdministrator() throws Exception {
        try (var session = fixture()) {
            var mapper = session.getMapper(RevenueMapper.class);
            var group = mapper.groupOrderCategoryStats("A", "2026-10-01", "2026-10-16");
            var home = group.stream().filter(row -> row.get("category_name").equals("家居")).findFirst().orElseThrow();
            assertEquals(3L, ((Number) home.get("order_count")).longValue());
            assertEquals(2L, ((Number) home.get("ordered_site_count")).longValue());
            assertTrue(group.stream().noneMatch(row -> row.get("category_name").equals("旧分类")));
            var unassigned = mapper.groupOrderCategoryStats("B", "2026-10-01", "2026-10-16");
            assertEquals(2, unassigned.size());
            assertTrue(unassigned.stream().allMatch(row -> ((Number) row.get("order_count")).longValue() == 1));
            var shorter = mapper.groupOrderCategoryStats("A", "2026-10-01", "2026-10-05");
            var shorterHome = shorter.stream().filter(row -> row.get("category_name").equals("家居")).findFirst().orElseThrow();
            assertEquals(2L, ((Number) shorterHome.get("order_count")).longValue());
        }
    }
}
