package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** Raw aggregates used by the revenue and commission report. */
@Mapper
public interface RevenueMapper {

    String ORDER_CATEGORIES_JOIN_SQL = " LEFT JOIN site_info s ON " +
            "LOWER(CASE WHEN LEFT(TRIM(s.site_domain), 4) = 'www.' " +
            "THEN SUBSTRING(TRIM(s.site_domain), 5) ELSE TRIM(s.site_domain) END) = filtered_orders.normalized_host " +
            "LEFT JOIN retired_site_info r ON s.site_domain IS NULL AND r.site_domain = filtered_orders.normalized_host " +
            "JOIN JSON_TABLE(CASE WHEN COALESCE(s.cat_names, r.cat_names) IS NULL " +
            "OR JSON_LENGTH(COALESCE(s.cat_names, r.cat_names)) = 0 " +
            "THEN JSON_ARRAY(COALESCE(NULLIF(TRIM(COALESCE(s.product_category, r.product_category)), ''), '未分类')) " +
            "ELSE COALESCE(s.cat_names, r.cat_names) END, " +
            "'$[*]' COLUMNS(category_name VARCHAR(100) PATH '$')) categories ";

    /** Active sites plus retired sites whose domain has not been reactivated. */
    String HISTORICAL_SITES_SQL = "(SELECT site_domain, admin_name, user_group, site_tag, " +
            "CAST(cat_names AS CHAR CHARACTER SET utf8mb4) AS cat_names, " +
            "product_category, domain_applied_at, created_at FROM site_info " +
            "UNION ALL SELECT r.site_domain, r.admin_name, r.user_group, r.site_tag, " +
            "CAST(r.cat_names AS CHAR CHARACTER SET utf8mb4), " +
            "r.product_category, r.domain_applied_at, r.created_at " +
            "FROM retired_site_info r WHERE NOT EXISTS (SELECT 1 FROM site_info current_site " +
            "WHERE LOWER(CASE WHEN LEFT(TRIM(current_site.site_domain), 4) = 'www.' " +
            "THEN SUBSTRING(TRIM(current_site.site_domain), 5) ELSE TRIM(current_site.site_domain) END) " +
            "= r.site_domain))";

    /** Whole-group totals, including paid orders whose administrator is not yet assigned. */
    @Select({"<script>",
            "SELECT user_group, COUNT(DISTINCT " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + ") AS total_orders,",
            "COUNT(DISTINCT CASE WHEN " + OrderFraudSql.VALID_SQL + " THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS valid_orders,",
            "COUNT(DISTINCT CASE WHEN pay_status_text = '已支付' THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS successful_orders,",
            "COUNT(DISTINCT CASE WHEN site_tag = 0 THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS standalone_orders,",
            "COUNT(DISTINCT CASE WHEN site_tag = 1 THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS batch_orders,",
            "COUNT(DISTINCT CASE WHEN site_tag = 2 THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS copy_orders,",
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS original_amount",
            "FROM orders WHERE TRIM(COALESCE(user_group, '')) &lt;&gt; ''",
            "AND (#{userGroup} IS NULL OR user_group = #{userGroup})",
            "<if test='startDate != null and startDate != &quot;&quot;'> AND create_time &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>",
            "<if test='endDate != null and endDate != &quot;&quot;'> AND create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>",
            "GROUP BY user_group", "</script>"})
    List<Map<String, Object>> groupOrderStats(@Param("userGroup") String userGroup,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate);

    @Select({"<script>",
            "SELECT user_group, COALESCE(NULLIF(TRIM(customer_ip_country), ''), '未知') AS customer_country,",
            "COUNT(DISTINCT " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + ") AS order_count",
            "FROM orders WHERE TRIM(COALESCE(user_group, '')) &lt;&gt; ''",
            "AND (#{userGroup} IS NULL OR user_group = #{userGroup})",
            "<if test='startDate != null and startDate != &quot;&quot;'> AND create_time &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>",
            "<if test='endDate != null and endDate != &quot;&quot;'> AND create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>",
            "GROUP BY user_group, customer_country", "</script>"})
    List<Map<String, Object>> groupOrderCountryStats(@Param("userGroup") String userGroup,
                                                      @Param("startDate") String startDate,
                                                      @Param("endDate") String endDate);

    @Select({"<script>",
            "SELECT admin_name, user_group, COUNT(DISTINCT " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + ") AS total_orders,",
            "COUNT(DISTINCT CASE WHEN " + OrderFraudSql.VALID_SQL + " THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS valid_orders,",
            "COUNT(DISTINCT CASE WHEN pay_status_text = '已支付' THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS successful_orders,",
            "COUNT(DISTINCT CASE WHEN site_tag = 0 THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS standalone_orders,",
            "COUNT(DISTINCT CASE WHEN site_tag = 1 THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS batch_orders,",
            "COUNT(DISTINCT CASE WHEN site_tag = 2 THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS copy_orders,",
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS original_amount,",
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' AND site_tag = 1 THEN amount ELSE 0 END), 0) AS batch_site_amount",
            "FROM orders WHERE TRIM(COALESCE(admin_name, '')) &lt;&gt; ''",
            "AND TRIM(COALESCE(user_group, '')) &lt;&gt; '' AND (#{userGroup} IS NULL OR user_group = #{userGroup})",
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) &gt; 0 " +
            "<if test='teacherSuffixes != null and !teacherSuffixes.isEmpty()'> OR " +
            "<foreach collection='teacherSuffixes' item='suffix' separator=' OR '>admin_name LIKE CONCAT('%', #{suffix})</foreach>" +
            "</if>)",
            "<if test='startDate != null and startDate != &quot;&quot;'> AND create_time &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>",
            "<if test='endDate != null and endDate != &quot;&quot;'> AND create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>",
            "GROUP BY admin_name, user_group", "</script>"})
    List<Map<String, Object>> adminOrderStats(@Param("userGroup") String userGroup,
                                               @Param("ownerName") String ownerName,
                                               @Param("teacherSuffixes") List<String> teacherSuffixes,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate);

    @Select({"<script>",
            "SELECT admin_name, user_group, COALESCE(NULLIF(TRIM(customer_ip_country), ''), '未知') AS customer_country,",
            "COUNT(DISTINCT " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + ") AS order_count",
            "FROM orders WHERE TRIM(COALESCE(admin_name, '')) &lt;&gt; ''",
            "AND TRIM(COALESCE(user_group, '')) &lt;&gt; '' AND (#{userGroup} IS NULL OR user_group = #{userGroup})",
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) &gt; 0 " +
            "<if test='teacherSuffixes != null and !teacherSuffixes.isEmpty()'> OR " +
            "<foreach collection='teacherSuffixes' item='suffix' separator=' OR '>admin_name LIKE CONCAT('%', #{suffix})</foreach>" +
            "</if>)",
            "<if test='startDate != null and startDate != &quot;&quot;'> AND create_time &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>",
            "<if test='endDate != null and endDate != &quot;&quot;'> AND create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>",
            "GROUP BY admin_name, user_group, customer_country", "</script>"})
    List<Map<String, Object>> adminOrderCountryStats(@Param("userGroup") String userGroup,
                                                      @Param("ownerName") String ownerName,
                                                      @Param("teacherSuffixes") List<String> teacherSuffixes,
                                                      @Param("startDate") String startDate,
                                                      @Param("endDate") String endDate);

    @Select({"<script>",
            "SELECT s.admin_name, s.user_group, COUNT(*) AS site_count, SUM(s.site_tag = 1) AS batch_site_count,",
            "COUNT(valid_sites.product_host) AS valid_ordered_site_count",
            "FROM " + HISTORICAL_SITES_SQL + " s LEFT JOIN (",
            "SELECT DISTINCT " + OrderMapper.NORMALIZED_PRODUCT_HOST_SQL + " AS product_host FROM orders",
            "WHERE " + OrderFraudSql.VALID_SQL + " AND TRIM(COALESCE(product_host, '')) &lt;&gt; ''",
            "<if test='startDate != null and startDate != &quot;&quot;'> AND create_time &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>",
            "<if test='endDate != null and endDate != &quot;&quot;'> AND create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>",
            ") valid_sites ON LOWER(CASE WHEN LEFT(TRIM(s.site_domain), 4) = 'www.'",
            "THEN SUBSTRING(TRIM(s.site_domain), 5) ELSE TRIM(s.site_domain) END) = valid_sites.product_host",
            "WHERE TRIM(COALESCE(s.admin_name, '')) &lt;&gt; ''",
            "AND TRIM(COALESCE(s.user_group, '')) &lt;&gt; '' AND (#{userGroup} IS NULL OR s.user_group = #{userGroup})",
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(s.admin_name, #{ownerName}) &gt; 0 " +
            "<if test='teacherSuffixes != null and !teacherSuffixes.isEmpty()'> OR " +
            "<foreach collection='teacherSuffixes' item='suffix' separator=' OR '>s.admin_name LIKE CONCAT('%', #{suffix})</foreach>" +
            "</if>)",
            "GROUP BY s.admin_name, s.user_group", "</script>"})
    List<Map<String, Object>> adminSiteStats(@Param("userGroup") String userGroup,
                                              @Param("ownerName") String ownerName,
                                              @Param("teacherSuffixes") List<String> teacherSuffixes,
                                              @Param("startDate") String startDate,
                                              @Param("endDate") String endDate);

    @Select({"<script>",
            "SELECT filtered_orders.admin_name, filtered_orders.user_group,",
            "COALESCE(NULLIF(TRIM(categories.category_name), ''), '未分类') AS category_name,",
            "COUNT(DISTINCT filtered_orders.order_identity) AS order_count,",
            "COUNT(DISTINCT NULLIF(filtered_orders.normalized_host, '')) AS ordered_site_count",
            "FROM (SELECT DISTINCT admin_name, user_group,",
            OrderMapper.NORMALIZED_PRODUCT_HOST_SQL + " AS normalized_host,",
            OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " AS order_identity",
            "FROM orders WHERE TRIM(COALESCE(admin_name, '')) &lt;&gt; ''",
            "AND TRIM(COALESCE(user_group, '')) &lt;&gt; '' AND (#{userGroup} IS NULL OR user_group = #{userGroup})",
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) &gt; 0 " +
            "<if test='teacherSuffixes != null and !teacherSuffixes.isEmpty()'> OR " +
            "<foreach collection='teacherSuffixes' item='suffix' separator=' OR '>admin_name LIKE CONCAT('%', #{suffix})</foreach>" +
            "</if>)",
            "<if test='startDate != null and startDate != &quot;&quot;'> AND create_time &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>",
            "<if test='endDate != null and endDate != &quot;&quot;'> AND create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>",
            ") filtered_orders", ORDER_CATEGORIES_JOIN_SQL,
            "GROUP BY filtered_orders.admin_name, filtered_orders.user_group, category_name", "</script>"})
    List<Map<String, Object>> adminOrderCategoryStats(@Param("userGroup") String userGroup,
                                                       @Param("ownerName") String ownerName,
                                                       @Param("teacherSuffixes") List<String> teacherSuffixes,
                                                       @Param("startDate") String startDate,
                                                       @Param("endDate") String endDate);

    @Select({"<script>",
            "SELECT filtered_orders.user_group,",
            "COALESCE(NULLIF(TRIM(categories.category_name), ''), '未分类') AS category_name,",
            "COUNT(DISTINCT filtered_orders.order_identity) AS order_count,",
            "COUNT(DISTINCT NULLIF(filtered_orders.normalized_host, '')) AS ordered_site_count",
            "FROM (SELECT DISTINCT user_group,",
            OrderMapper.NORMALIZED_PRODUCT_HOST_SQL + " AS normalized_host,",
            OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " AS order_identity",
            "FROM orders WHERE TRIM(COALESCE(user_group, '')) &lt;&gt; ''",
            "AND (#{userGroup} IS NULL OR user_group = #{userGroup})",
            "<if test='startDate != null and startDate != &quot;&quot;'> AND create_time &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>",
            "<if test='endDate != null and endDate != &quot;&quot;'> AND create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>",
            ") filtered_orders", ORDER_CATEGORIES_JOIN_SQL,
            "GROUP BY filtered_orders.user_group, category_name", "</script>"})
    List<Map<String, Object>> groupOrderCategoryStats(@Param("userGroup") String userGroup,
                                                     @Param("startDate") String startDate,
                                                     @Param("endDate") String endDate);

    @Select({"<script>", "SELECT s.site_domain, s.admin_name, s.user_group, s.site_tag, s.cat_names, DATE_FORMAT(s.domain_applied_at, '%Y-%m') AS site_month",
            "FROM " + HISTORICAL_SITES_SQL + " s WHERE TRIM(COALESCE(s.admin_name, '')) &lt;&gt; ''",
            "AND s.domain_applied_at IS NOT NULL",
            "AND TRIM(COALESCE(s.user_group, '')) &lt;&gt; '' AND (#{userGroup} IS NULL OR s.user_group = #{userGroup})",
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(s.admin_name, #{ownerName}) &gt; 0 " +
            "<if test='teacherSuffixes != null and !teacherSuffixes.isEmpty()'> OR " +
            "<foreach collection='teacherSuffixes' item='suffix' separator=' OR '>s.admin_name LIKE CONCAT('%', #{suffix})</foreach>" +
            "</if>)",
            "<if test='siteCreatedMonth != null and siteCreatedMonth != &quot;&quot;'> " +
            "AND s.domain_applied_at &gt;= CONCAT(#{siteCreatedMonth}, '-01') " +
            "AND s.domain_applied_at &lt; DATE_ADD(CONCAT(#{siteCreatedMonth}, '-01'), INTERVAL 1 MONTH)</if>",
            "ORDER BY site_month DESC, s.user_group, s.admin_name", "</script>"})
    List<Map<String, Object>> revenueSites(@Param("userGroup") String userGroup,
                                           @Param("ownerName") String ownerName,
                                           @Param("teacherSuffixes") List<String> teacherSuffixes,
                                           @Param("siteCreatedMonth") String siteCreatedMonth);

    @Select({"<script>", "SELECT " + OrderMapper.NORMALIZED_PRODUCT_HOST_SQL + " AS product_host,",
            "COUNT(DISTINCT " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + ") AS total_orders,",
            "COUNT(DISTINCT CASE WHEN " + OrderFraudSql.VALID_SQL + " THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS valid_orders,",
            "COUNT(DISTINCT CASE WHEN pay_status_text = '已支付' THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS successful_orders,",
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS successful_amount",
            "FROM orders WHERE TRIM(COALESCE(product_host, '')) &lt;&gt; ''",
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) &gt; 0 " +
            "<if test='teacherSuffixes != null and !teacherSuffixes.isEmpty()'> OR " +
            "<foreach collection='teacherSuffixes' item='suffix' separator=' OR '>admin_name LIKE CONCAT('%', #{suffix})</foreach>" +
            "</if>)",
            "<if test='startDate != null and startDate != &quot;&quot;'> AND create_time &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>",
            "<if test='endDate != null and endDate != &quot;&quot;'> AND create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>",
            "GROUP BY " + OrderMapper.NORMALIZED_PRODUCT_HOST_SQL, "</script>"})
    List<Map<String, Object>> revenueOrdersByDomain(@Param("startDate") String startDate,
                                                     @Param("endDate") String endDate,
                                                     @Param("ownerName") String ownerName,
                                                     @Param("teacherSuffixes") List<String> teacherSuffixes);

    @Select({"<script>", "SELECT " + OrderMapper.NORMALIZED_PRODUCT_HOST_SQL + " AS product_host,",
            "COALESCE(NULLIF(TRIM(customer_ip_country), ''), '未知') AS customer_country,",
            "COUNT(DISTINCT " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + ") AS order_count",
            "FROM orders WHERE TRIM(COALESCE(product_host, '')) &lt;&gt; ''",
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) &gt; 0 " +
            "<if test='teacherSuffixes != null and !teacherSuffixes.isEmpty()'> OR " +
            "<foreach collection='teacherSuffixes' item='suffix' separator=' OR '>admin_name LIKE CONCAT('%', #{suffix})</foreach>" +
            "</if>)",
            "<if test='startDate != null and startDate != &quot;&quot;'> AND create_time &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>",
            "<if test='endDate != null and endDate != &quot;&quot;'> AND create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>",
            "GROUP BY " + OrderMapper.NORMALIZED_PRODUCT_HOST_SQL + ", customer_country", "</script>"})
    List<Map<String, Object>> revenueOrderCountriesByDomain(@Param("startDate") String startDate,
                                                             @Param("endDate") String endDate,
                                                             @Param("ownerName") String ownerName,
                                                             @Param("teacherSuffixes") List<String> teacherSuffixes);
}
