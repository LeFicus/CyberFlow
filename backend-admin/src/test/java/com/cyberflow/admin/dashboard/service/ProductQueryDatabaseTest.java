package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.ProductQueryMapper;
import com.cyberflow.admin.dashboard.model.ProductFilter;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly opt in. Session-local temporary tables shadow, but NEVER modify, production tables. */
@EnabledIfEnvironmentVariable(named = "CYBERFLOW_PRODUCT_DB_TEST", matches = "1")
class ProductQueryDatabaseTest {
    @Test void millionRowCursorQueriesAndFilterParity() throws Exception {
        String url = System.getenv().getOrDefault("CYBERFLOW_TEST_DB_URL", "jdbc:mysql://localhost:3306/cyberflow?serverTimezone=Asia/Shanghai");
        var ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url,
                System.getenv().getOrDefault("CYBERFLOW_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("CYBERFLOW_TEST_DB_PASSWORD", "123456"));
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        config.setLocalCacheScope(LocalCacheScope.STATEMENT);
        try (var input = getClass().getResourceAsStream("/mapper/ProductQueryMapper.xml")) {
            new XMLMapperBuilder(input, config, "mapper/ProductQueryMapper.xml", config.getSqlFragments()).parse();
        }
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
            var connection = session.getConnection();
            try (var statement = connection.createStatement()) {
                // MySQL resolves this name to a table private to this connection until it closes.
                String ddl;
                try (var structure = statement.executeQuery("SHOW CREATE TABLE scraped_data.ecommerce_products")) {
                    assertTrue(structure.next());
                    ddl = structure.getString(2).replaceFirst("CREATE TABLE `ecommerce_products`", "CREATE TEMPORARY TABLE scraped_data.ecommerce_products");
                }
                assertTrue(ddl.startsWith("CREATE TEMPORARY TABLE"));
                statement.execute(ddl);
                String digits = "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)";
                String numbers = "SELECT 1+a.n+10*b.n+100*c.n+1000*d.n+10000*e.n+100000*f.n n FROM " + digits + " a CROSS JOIN " + digits + " b CROSS JOIN " + digits + " c CROSS JOIN " + digits + " d CROSS JOIN " + digits + " e CROSS JOIN " + digits + " f";
                statement.executeUpdate("INSERT INTO scraped_data.ecommerce_products (id,sku,name,source_domain,custom_category,categories,product_role,regular_price,created_at) " +
                        "SELECT n,LPAD(n,8,'0'),CONCAT('Product ',n),CONCAT('shop',MOD(n,100),'.test'),CONCAT('Category ',MOD(n,20)),'Tools, Garden','main',MOD(n,1000)/10,'2026-08-27 12:00:00' FROM (" + numbers + ") numbers");
                var mapper = session.getMapper(ProductQueryMapper.class);
                assertEquals(1_000_000, mapper.maxId());
                ProductFilter all = new ProductFilter().normalized();
                var deep = mapper.search(all, null, 1_000_000, 100_001L, 51);
                assertEquals(51, deep.size()); assertEquals(100_000L, ((Number)deep.get(0).get("id")).longValue());
                ProductFilter f = new ProductFilter(); f.setDomains(List.of("shop1.test"));
                f.setMinPrice(new BigDecimal("20")); f.setProductCategories(List.of("Garden")); f = f.normalized();
                long total = mapper.count(f, null, 1_000_000), processed = 0, after = 0;
                assertEquals(8000L, total);
                while (true) {
                    var batch = mapper.exportBatch(f, null, 1_000_000, after, 500);
                    processed += batch.size();
                    if (batch.isEmpty()) break;
                    after = ((Number)batch.get(batch.size()-1).get("id")).longValue();
                }
                assertEquals(total, processed);
                statement.executeUpdate("UPDATE scraped_data.ecommerce_products SET image_usable=0 WHERE id IN (999901,999801)");
                assertEquals(7998L, mapper.count(f, null, 1_000_000));
                assertTrue(mapper.search(f, null, 1_000_000, null, 50).stream().noneMatch(row -> List.of(999901L,999801L).contains(((Number)row.get("id")).longValue())));
                assertTrue(mapper.exportBatch(f, null, 1_000_000, 999800, 500).stream().noneMatch(row -> List.of(999901L,999801L).contains(((Number)row.get("id")).longValue())));
                assertTrue(mapper.search(all, List.of(), 1_000_000, null, 50).isEmpty());
                assertEquals(List.of("shop1.test"), mapper.domainOptions("shop%", List.of("shop1.test")));
                for (String sql : List.of(
                        "SELECT id,sku,name FROM scraped_data.ecommerce_products ORDER BY id DESC LIMIT 900000,50",
                        "SELECT id,sku,name FROM scraped_data.ecommerce_products WHERE id < 100001 ORDER BY id DESC LIMIT 50",
                        "SELECT id,sku,name FROM scraped_data.ecommerce_products WHERE source_domain='shop1.test' AND id<100001 ORDER BY id DESC LIMIT 50")) {
                    try (var result = statement.executeQuery("EXPLAIN ANALYZE " + sql)) {
                        while (result.next()) System.out.println("PRODUCT_BENCHMARK " + result.getString(1));
                    }
                }
                statement.execute("DROP TEMPORARY TABLE scraped_data.ecommerce_products");
            }
        }
    }
}
