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
            "COUNT(DISTINCT CASE WHEN pay_status_text = '已支付' THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS successful_orders,",
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS original_amount",
            "FROM orders WHERE user_group IN ('A', 'B')",
            "AND (#{userGroup} IS NULL OR user_group = #{userGroup})",
            "<if test='startDate != null and startDate != &quot;&quot;'> AND create_time &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>",
            "<if test='endDate != null and endDate != &quot;&quot;'> AND create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>",
            "GROUP BY user_group", "</script>"})
    List<Map<String, Object>> groupOrderStats(@Param("userGroup") String userGroup,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate);

    @Select({"<script>",
            "SELECT admin_name, user_group, COUNT(DISTINCT " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + ") AS total_orders,",
            "COUNT(DISTINCT CASE WHEN pay_status_text = '已支付' THEN " + OrderMapper.DEDUPLICATED_ORDER_KEY_SQL + " END) AS successful_orders,",
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS original_amount",
            "FROM orders WHERE TRIM(COALESCE(admin_name, '')) &lt;&gt; ''",
            "AND user_group IN ('A', 'B') AND (#{userGroup} IS NULL OR user_group = #{userGroup})",
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

    @Select({"<script>", "SELECT admin_name, user_group, COUNT(*) AS site_count",
            "FROM site_info WHERE TRIM(COALESCE(admin_name, '')) &lt;&gt; ''",
            "AND user_group IN ('A', 'B') AND (#{userGroup} IS NULL OR user_group = #{userGroup})",
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

    @Select({"<script>", "SELECT site_domain, admin_name, user_group, DATE_FORMAT(COALESCE(domain_applied_at, created_at), '%Y-%m') AS site_month",
            "FROM site_info WHERE TRIM(COALESCE(admin_name, '')) &lt;&gt; ''",
            "AND user_group IN ('A', 'B') AND (#{userGroup} IS NULL OR user_group = #{userGroup})",
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
