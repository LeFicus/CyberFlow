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
                create.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            }
            session.getConnection().setCatalog(schema);
            try (Statement sql = session.getConnection().createStatement()) {
                try (var current = sql.executeQuery("SELECT DATABASE()")) {
                    assertTrue(current.next());
                    assertEquals(schema, current.getString(1));
                }
                sql.execute("CREATE TABLE order_fraud_reviews (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_group VARCHAR(32), " +
                        "order_id BIGINT, email VARCHAR(255), decision VARCHAR(16), reviewer VARCHAR(100), note VARCHAR(500), " +
                        "reviewed_at DATETIME DEFAULT CURRENT_TIMESTAMP) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                sql.execute("CREATE TABLE site_info (site_domain VARCHAR(255), admin_name VARCHAR(100), " +
                        "user_group VARCHAR(32), site_tag INT, cat_names JSON, product_category VARCHAR(100), " +
                        "domain_applied_at DATETIME, created_at DATETIME) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
                sql.execute("CREATE TABLE retired_site_info LIKE site_info");
                sql.execute("CREATE TABLE orders (id BIGINT, amount DECIMAL(10,2), currency VARCHAR(10), " +
                        "create_time DATETIME, product_host VARCHAR(255), pay_status_text VARCHAR(50), " +
                        "customer_ip_country VARCHAR(100), shipping_email VARCHAR(255), shipping_address JSON, " +
                        "admin_name VARCHAR(100), user_group VARCHAR(32), theme_name VARCHAR(100), " +
                        "product_category VARCHAR(100), site_tag INT, product_info JSON, is_valid INT, is_fraud INT DEFAULT 0, dedupe_key VARCHAR(64)) " +
                        "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
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
                        null, "2026-10-01", "2026-10-16", 0, 10, type, null);
                long total = orders.countOrdersFiltered(null, null, "A", null, null, null, null,
                        null, "2026-10-01", "2026-10-16", type, null);
                var summary = orders.summarizeOrdersFiltered(null, null, "A", null, null, null, null,
                        null, "2026-10-01", "2026-10-16", type, null);
                assertEquals(total, rows.size());
                assertEquals(total, ((Number) summary.get("total_count")).longValue());
                assertTrue(rows.stream().allMatch(row -> ((Number) row.get("site_tag")).intValue() == expectedType));
                assertEquals(rows.stream().map(row -> (BigDecimal) row.get("amount"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add), summary.get("total_amount"));
                assertEquals(type == 2 ? 1 : 2, total);
            }
            var paid = orders.summarizeOrdersFiltered(null, null, "A", "live.test", "已支付", "USD", null,
                    "alice", "2026-10-01", "2026-10-16", 1, null);
            assertEquals(1L, ((Number) paid.get("paid_count")).longValue());
            assertEquals(new BigDecimal("10.00"), paid.get("paid_amount"));
            assertEquals(6L, orders.countOrdersFiltered(null, null, null, null, null, null, null,
                    null, null, null, null, null));
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
    private void platformFixtures(Fixture fixture) throws Exception {
        try (var sql = fixture.session().getConnection().createStatement()) {
            sql.executeUpdate("INSERT INTO site_info VALUES ('flag.test','alice','A',1,JSON_ARRAY('平台分类'),NULL,'2026-10-01','2026-10-01')," +
                    "('unknown-flag.test','alice','A',1,JSON_ARRAY('未同步分类'),NULL,'2026-10-01','2026-10-01')");
            sql.executeUpdate("INSERT INTO orders (id,user_group,admin_name,product_host,shipping_email,is_valid,is_fraud,dedupe_key,create_time,site_tag) VALUES " +
                    "(101,'A','alice','flag.test','first.last.1@gmail.com',0,0,'platform-normal','2026-10-12',1)," +
                    "(102,'A','alice','flag.test','normal@example.com',0,1,'platform-risk','2026-10-12',1)," +
                    "(103,'A','alice','flag.test','first.last.12345@gmail.com',1,1,'platform-risk2','2026-10-12',1)," +
                    "(104,'A','alice','flag.test','invalid@example.com',1,0,'platform-invalid','2026-10-12',1)," +
                    "(105,'A','alice','unknown-flag.test','unknown@example.com',0,NULL,'platform-unknown','2026-10-12',1)," +
                    "(106,'A','alice','flag.test','first.last.1@gmail.com',0,0,'platform-normal','2026-10-12',1)," +
                    "(107,'A','alice','flag.test','unknown-valid@example.com',NULL,0,'platform-unknown-valid','2026-10-12',1)," +
                    "(108,'A','alice','flag.test',NULL,0,0,'platform-no-email','2026-10-12',1)," +
                    "(109,'A','alice','flag.test','normal@example.com',0,1,'platform-risk','2026-10-12',1)," +
                    "(101,'B','alice','flag.test','other@example.com',0,1,'platform-other-group','2026-10-12',1)");
            sql.executeUpdate("INSERT INTO order_fraud_reviews (user_group,order_id,email,decision,reviewer,note) " +
                    "VALUES ('A',102,'normal@example.com','APPROVED','old-reviewer','旧审核通过不能覆盖支付平台风控')");
        }
    }

    @Test
    void platformFlagsReplaceEmailRulesAndLocalReviewsAcrossReports() throws Exception {
        try (var fixture = fixture()) {
            var orders = fixture.getMapper(OrderMapper.class);
            var revenue = fixture.getMapper(RevenueMapper.class);
            long originalSites = ((Number) revenue.adminSiteStats("A", null, List.of(), null, null).get(0).get("valid_ordered_site_count")).longValue();
            platformFixtures(fixture);
            fixture.session().clearCache();
            var summary = orders.businessSummary();
            assertEquals(14L, ((Number) summary.get("deduplicated_orders")).longValue());
            assertEquals(7L, ((Number) summary.get("valid_deduplicated_orders")).longValue());
            // The risk card is a raw order count, including repeated risk identities.
            assertEquals(4L, ((Number) summary.get("risk_orders")).longValue());
            var scoped = orders.businessSummaryByGroup("2026-10-01", "2026-11-01", "A", "alice");
            assertEquals(12L, ((Number) scoped.get("deduplicated_orders")).longValue());
            assertEquals(6L, ((Number) scoped.get("valid_deduplicated_orders")).longValue());
            assertEquals(3L, ((Number) scoped.get("risk_orders")).longValue());
            var group = revenue.groupOrderStats("A", null, null).get(0);
            assertEquals(12L, ((Number) group.get("total_orders")).longValue());
            assertEquals(6L, ((Number) group.get("valid_orders")).longValue());
            assertEquals(12L, List.of("standalone_orders", "batch_orders", "copy_orders").stream()
                    .mapToLong(key -> ((Number) group.get(key)).longValue()).sum());
            assertEquals(6L, ((Number) revenue.adminOrderStats("A", null, List.of(), null, null).get(0).get("valid_orders")).longValue());
            var domain = revenue.revenueOrdersByDomain(null, null, null, List.of()).stream()
                    .filter(row -> row.get("product_host").equals("flag.test")).findFirst().orElseThrow();
            assertEquals(7L, ((Number) domain.get("total_orders")).longValue());
            assertEquals(2L, ((Number) domain.get("valid_orders")).longValue());
            assertEquals(originalSites + 1, ((Number) revenue.adminSiteStats("A", null, List.of(), null, null).get(0).get("valid_ordered_site_count")).longValue());
            var category = revenue.groupOrderCategoryStats("A", null, null).stream()
                    .filter(row -> row.get("category_name").equals("平台分类")).findFirst().orElseThrow();
            assertEquals(6L, ((Number) category.get("order_count")).longValue());
            assertEquals(1L, ((Number) category.get("ordered_site_count")).longValue());
            assertTrue(revenue.adminOrderCategoryStats("A", null, List.of(), null, null).stream()
                    .anyMatch(row -> row.get("category_name").equals("未同步分类")));
            assertEquals(14L, orders.orderTrend("2026-10-01", "2026-11-01").stream()
                    .mapToLong(row -> ((Number) row.get("count")).longValue()).sum());
            assertEquals(16L, orders.countOrders());
        }
    }

    @Test
    void platformRiskFiltersMatchRowsCountsAndSummariesAndRefreshChangesStatistics() throws Exception {
        try (var fixture = fixture()) {
            platformFixtures(fixture);
            var orders = fixture.getMapper(OrderMapper.class);
            for (String state : List.of("RISK", "NORMAL", "UNKNOWN")) {
                var rows = orders.listOrdersFiltered(null, null, null, null, null, null, null, null, null, null, 0, 50, null, state);
                long count = orders.countOrdersFiltered(null, null, null, null, null, null, null, null, null, null, null, state);
                var totals = orders.summarizeOrdersFiltered(null, null, null, null, null, null, null, null, null, null, null, state);
                assertEquals(count, rows.size());
                assertEquals(count, ((Number) totals.get("total_count")).longValue());
                assertTrue(rows.stream().allMatch(row -> row.get("risk_status").equals(state)));
                assertEquals(state.equals("RISK") ? 4L : state.equals("NORMAL") ? 11L : 1L, count);
                if (!state.equals("NORMAL")) assertTrue(rows.stream().allMatch(row -> ((Number) row.get("effective_valid")).intValue() == 0));
            }
            try (var sql = fixture.session().getConnection().createStatement()) {
                sql.executeUpdate("UPDATE orders SET is_fraud=1 WHERE user_group='A' AND id IN (101,106)");
            }
            fixture.session().clearCache();
            assertEquals(14L, ((Number) orders.businessSummary().get("deduplicated_orders")).longValue());
            assertEquals(6L, ((Number) orders.businessSummary().get("valid_deduplicated_orders")).longValue());
            assertEquals(6L, ((Number) orders.businessSummary().get("risk_orders")).longValue());
        }
    }
    @Test
    void groupBTodays77RawOrdersAre34Deduplicated25ValidAnd32RiskOrders() throws Exception {
        try (var fixture = fixture()) {
            try (var insert = fixture.session().getConnection().prepareStatement(
                    "INSERT INTO orders (id,user_group,admin_name,product_host,shipping_email,is_valid,is_fraud,dedupe_key,create_time,site_tag) " +
                            "VALUES (?,'B','bob','b-today.test',?,?,?,?,'2026-10-27 10:00:00',0)")) {
                for (int index = 0; index < 77; index++) {
                    boolean risk = index < 32;
                    boolean valid = index >= 32 && index < 73;
                    int identity = risk ? index % 9 : valid ? 8 + (index - 32) % 25 : new int[]{8, 9, 10, 33}[index - 73];
                    insert.setInt(1, 1000 + index);
                    insert.setString(2, "customer" + identity + "@example.test");
                    insert.setInt(3, valid ? 0 : 1);
                    insert.setInt(4, risk ? 1 : 0);
                    insert.setString(5, "b-today-" + identity);
                    insert.executeUpdate();
                }
            }
            var orders = fixture.getMapper(OrderMapper.class);
            var revenue = fixture.getMapper(RevenueMapper.class);
            var summary = orders.businessSummaryByGroup("2026-10-27", "2026-10-28", "B", null);
            assertEquals(34L, ((Number) summary.get("deduplicated_orders")).longValue());
            assertEquals(25L, ((Number) summary.get("valid_deduplicated_orders")).longValue());
            assertEquals(32L, ((Number) summary.get("risk_orders")).longValue());
            assertEquals(77L, orders.countOrdersFiltered(null, null, "B", null, null, null, null,
                    null, "2026-10-27", "2026-10-27", null, null));
            var group = revenue.groupOrderStats("B", "2026-10-27", "2026-10-27").get(0);
            assertEquals(34L, ((Number) group.get("total_orders")).longValue());
            assertEquals(25L, ((Number) group.get("valid_orders")).longValue());
            assertEquals(34L, ((Number) group.get("standalone_orders")).longValue());
            var category = revenue.groupOrderCategoryStats("B", "2026-10-27", "2026-10-27").get(0);
            assertEquals(34L, ((Number) category.get("order_count")).longValue());
            var person = revenue.adminOrderStats("B", "bob", List.of(), "2026-10-27", "2026-10-27").get(0);
            assertEquals(34L, ((Number) person.get("total_orders")).longValue());
            var domain = revenue.revenueOrdersByDomain("2026-10-27", "2026-10-27", "bob", List.of()).get(0);
            assertEquals(34L, ((Number) domain.get("total_orders")).longValue());
            assertEquals(25L, ((Number) domain.get("valid_orders")).longValue());
            assertEquals(34L, orders.orderTrendByGroup("2026-10-27", "2026-10-28", "B", "bob").stream()
                    .mapToLong(row -> ((Number) row.get("count")).longValue()).sum());
        }
    }

}
