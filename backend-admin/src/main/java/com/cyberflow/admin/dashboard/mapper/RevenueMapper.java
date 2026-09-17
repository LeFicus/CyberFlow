package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** Raw aggregates used by the revenue and commission report. */
@Mapper
public interface RevenueMapper {

    /** Whole-group totals, including paid orders whose administrator is not yet assigned. */
    @Select({"<script>",
            "SELECT user_group, COUNT(DISTINCT " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + ") AS total_orders,",
            "COUNT(DISTINCT CASE WHEN is_valid = 0 THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS valid_orders,",
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
            "SELECT admin_name, user_group, COUNT(DISTINCT " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + ") AS total_orders,",
            "COUNT(DISTINCT CASE WHEN is_valid = 0 THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS valid_orders,",
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

    @Select({"<script>", "SELECT admin_name, user_group, COUNT(*) AS site_count, SUM(site_tag = 1) AS batch_site_count",
            "FROM site_info WHERE TRIM(COALESCE(admin_name, '')) &lt;&gt; ''",
            "AND TRIM(COALESCE(user_group, '')) &lt;&gt; '' AND (#{userGroup} IS NULL OR user_group = #{userGroup})",
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) &gt; 0 " +
            "<if test='teacherSuffixes != null and !teacherSuffixes.isEmpty()'> OR " +
            "<foreach collection='teacherSuffixes' item='suffix' separator=' OR '>admin_name LIKE CONCAT('%', #{suffix})</foreach>" +
            "</if>)",
            "AND COALESCE(domain_applied_at, created_at) &lt; CONCAT(#{siteCreatedBefore}, ' 00:00:00')",
            "GROUP BY admin_name, user_group", "</script>"})
    List<Map<String, Object>> adminSiteStats(@Param("userGroup") String userGroup,
                                              @Param("ownerName") String ownerName,
                                              @Param("teacherSuffixes") List<String> teacherSuffixes,
                                              @Param("siteCreatedBefore") String siteCreatedBefore);

    @Select({"<script>",
            "SELECT s.admin_name, s.user_group,",
            "COALESCE(NULLIF(TRIM(categories.category_name), ''), '未分类') AS category_name, COUNT(*) AS site_count",
            "FROM site_info s JOIN JSON_TABLE(",
            "CASE WHEN s.cat_names IS NULL OR JSON_LENGTH(s.cat_names) = 0 THEN JSON_ARRAY('未分类') ELSE s.cat_names END,",
            "'$[*]' COLUMNS(category_name VARCHAR(100) PATH '$')) categories",
            "WHERE TRIM(COALESCE(s.admin_name, '')) &lt;&gt; ''",
            "AND TRIM(COALESCE(s.user_group, '')) &lt;&gt; '' AND (#{userGroup} IS NULL OR s.user_group = #{userGroup})",
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(s.admin_name, #{ownerName}) &gt; 0 " +
            "<if test='teacherSuffixes != null and !teacherSuffixes.isEmpty()'> OR " +
            "<foreach collection='teacherSuffixes' item='suffix' separator=' OR '>s.admin_name LIKE CONCAT('%', #{suffix})</foreach>" +
            "</if>)",
            "AND COALESCE(s.domain_applied_at, s.created_at) &lt; CONCAT(#{siteCreatedBefore}, ' 00:00:00')",
            "GROUP BY s.admin_name, s.user_group, category_name", "</script>"})
    List<Map<String, Object>> adminSiteCategoryStats(@Param("userGroup") String userGroup,
                                                      @Param("ownerName") String ownerName,
                                                      @Param("teacherSuffixes") List<String> teacherSuffixes,
                                                      @Param("siteCreatedBefore") String siteCreatedBefore);

    @Select({"<script>", "SELECT site_domain, admin_name, user_group, site_tag, cat_names, DATE_FORMAT(COALESCE(domain_applied_at, created_at), '%Y-%m') AS site_month",
            "FROM site_info WHERE TRIM(COALESCE(admin_name, '')) &lt;&gt; ''",
            "AND TRIM(COALESCE(user_group, '')) &lt;&gt; '' AND (#{userGroup} IS NULL OR user_group = #{userGroup})",
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) &gt; 0 " +
            "<if test='teacherSuffixes != null and !teacherSuffixes.isEmpty()'> OR " +
            "<foreach collection='teacherSuffixes' item='suffix' separator=' OR '>admin_name LIKE CONCAT('%', #{suffix})</foreach>" +
            "</if>)",
            "<if test='siteCreatedMonth != null and siteCreatedMonth != &quot;&quot;'> AND DATE_FORMAT(COALESCE(domain_applied_at, created_at), '%Y-%m') = #{siteCreatedMonth}</if>",
            "ORDER BY site_month DESC, user_group, admin_name", "</script>"})
    List<Map<String, Object>> revenueSites(@Param("userGroup") String userGroup,
                                           @Param("ownerName") String ownerName,
                                           @Param("teacherSuffixes") List<String> teacherSuffixes,
                                           @Param("siteCreatedMonth") String siteCreatedMonth);

    @Select({"<script>", "SELECT " + OrderMapper.NORMALIZED_PRODUCT_HOST_SQL + " AS product_host,",
            "COUNT(DISTINCT " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + ") AS total_orders,",
            "COUNT(DISTINCT CASE WHEN is_valid = 0 THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS valid_orders,",
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
}
