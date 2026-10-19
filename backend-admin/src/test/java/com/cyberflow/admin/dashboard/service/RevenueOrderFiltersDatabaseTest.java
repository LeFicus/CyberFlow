package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.OrderMapper;
import com.cyberflow.admin.dashboard.mapper.OrderFraudReviewMapper;
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
        configuration.addMapper(OrderFraudReviewMapper.class);
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
                        "product_category VARCHAR(100), site_tag INT, product_info JSON, is_valid INT, dedupe_key VARCHAR(64)) " +
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
    private void fraudFixtures(Fixture fixture) throws Exception {
        try (var sql = fixture.session().getConnection().createStatement()) {
            sql.executeUpdate("UPDATE orders SET is_valid=1");
            sql.executeUpdate("INSERT INTO orders (id,amount,currency,create_time,product_host,pay_status_text," +
                    "shipping_email,admin_name,user_group,site_tag,is_valid,dedupe_key) VALUES " +
                    "(101,10,'USD','2026-10-12','live.test','支付失败',' Alice.Smith.12345@GMAIL.COM ','alice','A',1,0,'risk')," +
                    "(102,10,'USD','2026-10-12','live.test','支付失败','alice.smith.12345@gmail.com','alice','A',1,0,'risk')," +
                    "(103,10,'USD','2026-10-12','plain.test','支付失败','alice.smith@gmail.com','alice','A',0,0,'normal')," +
                    "(104,10,'USD','2026-10-12','live.test','已支付','alice.smith.98765@gmail.com','alice','A',1,1,'invalid')," +
                    "(101,10,'USD','2026-10-12','live.test','支付失败','alice.smith.12345@gmail.com','alice','B',1,0,'other-group')," +
                    "(106,10,'USD','2026-10-12','plain.test','已支付',NULL,'alice','A',0,1,'invalid-normal')," +
                    "(107,10,'USD','2026-10-12','plain.test','支付失败','alice.smith.12345@gmail.com.example','alice','A',0,0,'normal2')");
        }
    }

    @Test
    void fraudReviewsGateEveryValidMetricAndPreserveOriginalValidityAndDeduplication() throws Exception {
        try (var fixture = fixture()) {
            fraudFixtures(fixture);
            var orders = fixture.getMapper(OrderMapper.class);
            var revenue = fixture.getMapper(RevenueMapper.class);
            var reviews = fixture.getMapper(OrderFraudReviewMapper.class);
            var service = new OrderFraudReviewService(reviews);
            assertEquals(2L, ((Number) orders.businessSummary().get("valid_deduplicated_orders")).longValue());
            assertEquals(2L, ((Number) revenue.groupOrderStats("A", null, null).get(0).get("valid_orders")).longValue());
            assertEquals(2L, ((Number) revenue.adminOrderStats("A", null, List.of(), null, null).get(0).get("valid_orders")).longValue());
            assertEquals(0L, ((Number) revenue.revenueOrdersByDomain(null, null, null, List.of()).stream()
                    .filter(row -> row.get("product_host").equals("live.test")).findFirst().orElseThrow().get("valid_orders")).longValue());
            assertEquals(1L, ((Number) revenue.adminSiteStats("A", null, List.of(), null, null).get(0).get("valid_ordered_site_count")).longValue());

            service.review("A", 101, "alice.smith.12345@gmail.com", "APPROVED", "核实无异常", "review-admin");
            assertEquals(3L, ((Number) orders.businessSummary().get("valid_deduplicated_orders")).longValue());
            assertEquals(3L, ((Number) revenue.groupOrderStats("A", null, null).get(0).get("valid_orders")).longValue());
            assertEquals(3L, ((Number) revenue.adminOrderStats("A", null, List.of(), null, null).get(0).get("valid_orders")).longValue());
            assertEquals(1L, ((Number) revenue.revenueOrdersByDomain(null, null, null, List.of()).stream()
                    .filter(row -> row.get("product_host").equals("live.test")).findFirst().orElseThrow().get("valid_orders")).longValue());
            assertEquals(2L, ((Number) revenue.adminSiteStats("A", null, List.of(), null, null).get(0).get("valid_ordered_site_count")).longValue());
            service.review("A", 102, "alice.smith.12345@gmail.com", "APPROVED", "重复订单核实", "review-admin");
            service.review("A", 104, "alice.smith.98765@gmail.com", "APPROVED", "仅解除刷单限制", "review-admin");
            assertEquals(3L, ((Number) orders.businessSummary().get("valid_deduplicated_orders")).longValue());
            service.review("A", 101, "alice.smith.12345@gmail.com", "REJECTED", "重新核查异常", "review-admin");
            service.review("A", 102, "alice.smith.12345@gmail.com", "PENDING", "需要重新核查", "review-admin");
            assertEquals(2L, ((Number) orders.businessSummary().get("valid_deduplicated_orders")).longValue());
            assertEquals(2, reviews.history("A", 101).size());
            assertEquals("REJECTED", reviews.history("A", 101).get(0).get("decision"));
            assertEquals("review-admin", reviews.history("A", 101).get(0).get("reviewer"));
        }
    }

    @Test
    void reviewFiltersMatchRowsTotalsAndSummariesAndEmailChangesNeedNewReview() throws Exception {
        try (var fixture = fixture()) {
            fraudFixtures(fixture);
            var orders = fixture.getMapper(OrderMapper.class);
            var service = new OrderFraudReviewService(fixture.getMapper(OrderFraudReviewMapper.class));
            for (String state : List.of("NORMAL", "PENDING", "APPROVED", "REJECTED")) {
                var rows = orders.listOrdersFiltered(null, null, "A", null, null, null, null, null, null, null, 0, 50, null, state);
                long count = orders.countOrdersFiltered(null, null, "A", null, null, null, null, null, null, null, null, state);
                var summary = orders.summarizeOrdersFiltered(null, null, "A", null, null, null, null, null, null, null, null, state);
                assertEquals(count, rows.size());
                assertEquals(count, ((Number) summary.get("total_count")).longValue());
                assertTrue(rows.stream().allMatch(row -> row.get("fraud_status").equals(state)));
                if (state.equals("PENDING")) assertEquals(3L, count);
            }
            service.review("A", 101, "alice.smith.12345@gmail.com", "APPROVED", "核实通过", "admin");
            // A fresh platform upsert leaves the separate review table intact.
            try (var sql = fixture.session().getConnection().createStatement()) {
                sql.executeUpdate("UPDATE orders SET pay_status_text='支付异常' WHERE user_group='A' AND id=101");
            }
            assertEquals(1L, orders.countOrdersFiltered(null, null, "A", null, null, null, null, null, null, null, null, "APPROVED"));
            assertEquals(1L, orders.countOrdersFiltered(null, null, "B", null, null, null, null, null, null, null, null, "PENDING"));
            try (var sql = fixture.session().getConnection().createStatement()) {
                sql.executeUpdate("UPDATE orders SET shipping_email='changed.name.12345@gmail.com' WHERE user_group='A' AND id=101");
            }
            fixture.session().clearCache();
            assertEquals(0L, orders.countOrdersFiltered(null, null, "A", null, null, null, null, null, null, null, null, "APPROVED"));
            assertEquals(2L, ((Number) orders.businessSummary().get("valid_deduplicated_orders")).longValue());
            assertThrows(IllegalArgumentException.class, () -> service.review("A", 101, "alice.smith.12345@gmail.com", "APPROVED", "过期页面", "admin"));
        }
    }

    @Test
    void suspectedOrdersAreExcludedFromDeduplicatedTotalsTrendsTypesAndCategorySitesUntilApproved() throws Exception {
        try (var fixture = fixture()) {
            fraudFixtures(fixture);
            var orders = fixture.getMapper(OrderMapper.class);
            var revenue = fixture.getMapper(RevenueMapper.class);
            var service = new OrderFraudReviewService(fixture.getMapper(OrderFraudReviewMapper.class));
            try (var sql = fixture.session().getConnection().createStatement()) {
                sql.executeUpdate("INSERT INTO site_info VALUES ('pending.test','alice','A',2,JSON_ARRAY('待审专属分类'),NULL,'2026-10-01','2026-10-01')");
                sql.executeUpdate("INSERT INTO orders (id,user_group,admin_name,product_host,shipping_email,is_valid,dedupe_key,create_time,site_tag) " +
                        "VALUES (108,'A','alice','pending.test','pending.person.12345@gmail.com',0,'pending-only','2026-10-12',2)");
            }
            assertEquals(14L, orders.countOrders());
            assertEquals(9L, ((Number) orders.businessSummary().get("deduplicated_orders")).longValue());
            assertEquals(8L, ((Number) orders.businessSummaryByGroup("2026-10-01", "2026-11-01", "A", null)
                    .get("deduplicated_orders")).longValue());
            assertEquals(9L, orders.orderTrend("2026-10-01", "2026-11-01").stream()
                    .mapToLong(row -> ((Number) row.get("count")).longValue()).sum());
            assertEquals(8L, orders.orderTrendByGroup("2026-10-01", "2026-11-01", "A", "alice").stream()
                    .mapToLong(row -> ((Number) row.get("count")).longValue()).sum());
            assertEquals(9L, orders.countByAdminForGroup(null, null).stream()
                    .mapToLong(row -> ((Number) row.get("count")).longValue()).sum());
            var group = revenue.groupOrderStats("A", null, null).get(0);
            assertEquals(8L, ((Number) group.get("total_orders")).longValue());
            assertEquals(8L, List.of("standalone_orders", "batch_orders", "copy_orders").stream()
                    .mapToLong(key -> ((Number) group.get(key)).longValue()).sum());
            assertEquals(8L, ((Number) revenue.adminOrderStats("A", null, List.of(), null, null).get(0).get("total_orders")).longValue());
            assertTrue(revenue.groupOrderCategoryStats("A", null, null).stream().noneMatch(row -> row.get("category_name").equals("待审专属分类")));
            assertTrue(revenue.adminOrderCategoryStats("A", null, List.of(), null, null).stream().noneMatch(row -> row.get("category_name").equals("待审专属分类")));
            assertEquals(0L, ((Number) revenue.revenueOrdersByDomain(null, null, null, List.of()).stream()
                    .filter(row -> row.get("product_host").equals("pending.test")).findFirst().orElseThrow().get("total_orders")).longValue());
            assertEquals(9L, revenue.groupOrderCountryStats(null, null, null).stream()
                    .mapToLong(row -> ((Number) row.get("order_count")).longValue()).sum());
            assertEquals(8L, revenue.adminOrderCountryStats("A", null, List.of(), null, null).stream()
                    .mapToLong(row -> ((Number) row.get("order_count")).longValue()).sum());
            assertEquals(9L, revenue.revenueOrderCountriesByDomain(null, null, null, List.of()).stream()
                    .mapToLong(row -> ((Number) row.get("order_count")).longValue()).sum());

            service.review("A", 101, "alice.smith.12345@gmail.com", "APPROVED", "核实无异常", "admin");
            assertEquals(10L, ((Number) orders.businessSummary().get("deduplicated_orders")).longValue());
            service.review("A", 102, "alice.smith.12345@gmail.com", "APPROVED", "重复订单已核实", "admin");
            assertEquals(10L, ((Number) orders.businessSummary().get("deduplicated_orders")).longValue());
            // Approval restores ordinary deduplication even if the upstream validity flag is invalid.
            service.review("A", 104, "alice.smith.98765@gmail.com", "APPROVED", "解除刷单限制", "admin");
            assertEquals(11L, ((Number) orders.businessSummary().get("deduplicated_orders")).longValue());
            assertEquals(3L, ((Number) orders.businessSummary().get("valid_deduplicated_orders")).longValue());
            service.review("A", 108, "pending.person.12345@gmail.com", "APPROVED", "分类站点已核实", "admin");
            var category = revenue.groupOrderCategoryStats("A", null, null).stream()
                    .filter(row -> row.get("category_name").equals("待审专属分类")).findFirst().orElseThrow();
            assertEquals(1L, ((Number) category.get("order_count")).longValue());
            assertEquals(1L, ((Number) category.get("ordered_site_count")).longValue());
            service.review("A", 108, "pending.person.12345@gmail.com", "REJECTED", "复核确认异常", "admin");
            assertEquals(11L, ((Number) orders.businessSummary().get("deduplicated_orders")).longValue());
            assertTrue(revenue.groupOrderCategoryStats("A", null, null).stream().noneMatch(row -> row.get("category_name").equals("待审专属分类")));
            service.review("A", 101, "alice.smith.12345@gmail.com", "PENDING", "复核", "admin");
            service.review("A", 102, "alice.smith.12345@gmail.com", "REJECTED", "确认异常", "admin");
            assertEquals(10L, ((Number) orders.businessSummary().get("deduplicated_orders")).longValue());
            assertEquals(14L, orders.countOrders());
        }
    }

    @Test
    void numericEmailSuffixHasNoFixedLengthAndUsesTheSameReviewAndStatisticsPolicy() throws Exception {
        try (var fixture = fixture()) {
            var orders = fixture.getMapper(OrderMapper.class);
            var service = new OrderFraudReviewService(fixture.getMapper(OrderFraudReviewMapper.class));
            long initialDeduplicated = ((Number) orders.businessSummary().get("deduplicated_orders")).longValue();
            long initialValid = ((Number) orders.businessSummary().get("valid_deduplicated_orders")).longValue();
            var suspicious = List.of("first.last.1@gmail.com", "first.last.12@gmail.com",
                    "first.last.1234@gmail.com", "first.last.12345@gmail.com", "first.last.123456@gmail.com",
                    "first.last.123456789012@gmail.com", "first.last." + "1".repeat(64) + "@gmail.com",
                    " First.Last.007@GMAIL.COM ");
            var normal = List.of("first.last@gmail.com", "first.last.@gmail.com", "first.last.12a@gmail.com",
                    "first.last.12@hotmail.com", "first.last.12@gmail.com.example", "first.middle.last.12@gmail.com");
            var samples = new ArrayList<>(suspicious);
            samples.addAll(normal);
            try (var insert = fixture.session().getConnection().prepareStatement(
                    "INSERT INTO orders (id,user_group,admin_name,product_host,shipping_email,is_valid,dedupe_key,create_time) " +
                            "VALUES (?,'A','alice','pattern.test',?,0,?,'2026-10-12')")) {
                for (int index = 0; index < samples.size(); index++) {
                    insert.setInt(1, 200 + index);
                    insert.setString(2, samples.get(index));
                    insert.setString(3, "variable-digits-" + index);
                    insert.executeUpdate();
                }
            }
            fixture.session().clearCache();
            var rows = orders.listOrdersFiltered(null, null, "A", "pattern.test", null, null, null,
                    null, null, null, 0, 50, null, null);
            assertEquals(samples.size(), rows.size());
            for (var row : rows) {
                boolean hit = suspicious.contains(row.get("shipping_email"));
                assertEquals(hit ? "PENDING" : "NORMAL", row.get("fraud_status"));
                assertEquals(hit ? 0 : 1, ((Number) row.get("effective_valid")).intValue());
            }
            assertEquals(suspicious.size(), orders.countOrdersFiltered(null, null, "A", "pattern.test", null, null, null,
                    null, null, null, null, "PENDING"));
            assertEquals(initialDeduplicated + normal.size(), ((Number) orders.businessSummary().get("deduplicated_orders")).longValue());
            assertEquals(initialValid + normal.size(), ((Number) orders.businessSummary().get("valid_deduplicated_orders")).longValue());
            service.review("A", 200, suspicious.get(0), "APPROVED", "一位数字邮箱已核实", "admin");
            service.review("A", 206, suspicious.get(6), "APPROVED", "长数字邮箱已核实", "admin");
            assertEquals(initialDeduplicated + normal.size() + 2, ((Number) orders.businessSummary().get("deduplicated_orders")).longValue());
            assertEquals(initialValid + normal.size() + 2, ((Number) orders.businessSummary().get("valid_deduplicated_orders")).longValue());
            service.review("A", 200, suspicious.get(0), "REJECTED", "重新核实为异常", "admin");
            assertEquals(initialDeduplicated + normal.size() + 1, ((Number) orders.businessSummary().get("deduplicated_orders")).longValue());
        }
    }

}
