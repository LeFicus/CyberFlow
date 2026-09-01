package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cursor.Cursor;

import java.util.List;
import java.util.Map;

/**
 * 站点收录历史数据访问接口。
 * <p>
 * 提供站点收录历史的趋势分析和分页查询操作，直接操作 site_indexing_history 表。
 * 收录趋势按日聚合索引数量、商品数量和站点数量，用于前端图表展示。
 * </p>
 *
 * @author CyberFlow
 */
@Mapper
public interface SiteIndexingHistoryMapper {

    String NORMALIZED_HISTORY_DOMAIN =
            "LOWER(CASE WHEN LEFT(TRIM(h.site_domain), 4) = 'www.' " +
            "THEN SUBSTRING(TRIM(h.site_domain), 5) ELSE TRIM(h.site_domain) END)";
    String NORMALIZED_SITE_DOMAIN =
            "LOWER(CASE WHEN LEFT(TRIM(s.site_domain), 4) = 'www.' " +
            "THEN SUBSTRING(TRIM(s.site_domain), 5) ELSE TRIM(s.site_domain) END)";
    String LATEST_INDEX_CTE =
            "WITH ranked_index AS (" +
            "SELECT " + NORMALIZED_HISTORY_DOMAIN + " AS normalized_domain, h.index_count, h.product_count, " +
            "h.server_name, h.server_ip, h.last_submitted_at, h.recorded_at, " +
            "ROW_NUMBER() OVER (PARTITION BY " + NORMALIZED_HISTORY_DOMAIN +
            " ORDER BY h.recorded_at DESC, h.id DESC) AS row_num " +
            "FROM site_indexing_history h), " +
            "latest_index AS (SELECT * FROM ranked_index WHERE row_num=1), " +
            "previous_index AS (SELECT * FROM ranked_index WHERE row_num=2) ";
    String INDEX_JOINS =
            " FROM site_info s LEFT JOIN latest_index l ON l.normalized_domain=" + NORMALIZED_SITE_DOMAIN +
            " LEFT JOIN previous_index p ON p.normalized_domain=" + NORMALIZED_SITE_DOMAIN + " ";
    String INDEX_CHANGE =
            "(COALESCE(l.index_count, 0)-COALESCE(p.index_count, COALESCE(l.index_count, 0)))";
    String INDEX_FILTERS =
            "<where>" +
            "<if test='filters.userGroup != null and filters.userGroup != &quot;&quot;'> AND s.user_group=#{filters.userGroup}</if>" +
            "<if test='filters.ownerName != null'> AND FIND_IN_SET(s.admin_name, #{filters.ownerName}) &gt; 0</if>" +
            "<if test='filters.adminName != null and filters.adminName != &quot;&quot;'> AND (s.admin_name LIKE CONCAT('%', #{filters.adminName}, '%') OR s.builder_username LIKE CONCAT('%', #{filters.adminName}, '%'))</if>" +
            "<if test='filters.builderUsername != null and filters.builderUsername != &quot;&quot;'> AND COALESCE(NULLIF(TRIM(s.builder_username), ''), '未分配')=#{filters.builderUsername}</if>" +
            "<if test='filters.serverName != null and filters.serverName != &quot;&quot;'> AND (COALESCE(NULLIF(l.server_name, ''), s.server_name, '') LIKE CONCAT('%', #{filters.serverName}, '%') OR COALESCE(NULLIF(l.server_ip, ''), s.server_ip, '') LIKE CONCAT('%', #{filters.serverName}, '%'))</if>" +
            "<if test='filters.serverIp != null and filters.serverIp != &quot;&quot;'> AND COALESCE(NULLIF(l.server_ip, ''), s.server_ip, '')=#{filters.serverIp}</if>" +
            "<if test='filters.builderNameExact != null and filters.builderNameExact != &quot;&quot;'> AND COALESCE(NULLIF(TRIM(s.admin_name), ''), '未分配')=#{filters.builderNameExact}</if>" +
            "<if test='filters.serverNameExact != null and filters.serverNameExact != &quot;&quot;'> AND COALESCE(NULLIF(TRIM(COALESCE(NULLIF(l.server_name, ''), s.server_name)), ''), '未分配')=#{filters.serverNameExact}</if>" +
            "<if test='filters.serverIpEmpty'> AND COALESCE(NULLIF(TRIM(COALESCE(NULLIF(l.server_ip, ''), s.server_ip)), ''), '')=''</if>" +
            "<if test='filters.domain != null and filters.domain != &quot;&quot;'> AND s.site_domain LIKE CONCAT('%', #{filters.domain}, '%')</if>" +
            "<if test='filters.themeName != null and filters.themeName != &quot;&quot;'> AND s.theme_name LIKE CONCAT('%', #{filters.themeName}, '%')</if>" +
            "<if test='filters.productCategory != null and filters.productCategory != &quot;&quot;'> AND s.product_category LIKE CONCAT('%', #{filters.productCategory}, '%')</if>" +
            "<if test='filters.siteStartDate != null and filters.siteStartDate != &quot;&quot;'> AND s.created_at &gt;= CONCAT(#{filters.siteStartDate}, ' 00:00:00')</if>" +
            "<if test='filters.siteEndDate != null and filters.siteEndDate != &quot;&quot;'> AND s.created_at &lt; DATE_ADD(#{filters.siteEndDate}, INTERVAL 1 DAY)</if>" +
            "<if test='filters.submittedStartDate != null and filters.submittedStartDate != &quot;&quot;'> AND COALESCE(l.last_submitted_at, s.last_submitted_at) &gt;= CONCAT(#{filters.submittedStartDate}, ' 00:00:00')</if>" +
            "<if test='filters.submittedEndDate != null and filters.submittedEndDate != &quot;&quot;'> AND COALESCE(l.last_submitted_at, s.last_submitted_at) &lt; DATE_ADD(#{filters.submittedEndDate}, INTERVAL 1 DAY)</if>" +
            "<if test='filters.updatedStartDate != null and filters.updatedStartDate != &quot;&quot;'> AND l.recorded_at &gt;= CONCAT(#{filters.updatedStartDate}, ' 00:00:00')</if>" +
            "<if test='filters.updatedEndDate != null and filters.updatedEndDate != &quot;&quot;'> AND l.recorded_at &lt; DATE_ADD(#{filters.updatedEndDate}, INTERVAL 1 DAY)</if>" +
            "<if test='filters.minIndexCount != null'> AND COALESCE(l.index_count, 0) &gt;= #{filters.minIndexCount}</if>" +
            "<if test='filters.maxIndexCount != null'> AND COALESCE(l.index_count, 0) &lt;= #{filters.maxIndexCount}</if>" +
            "<if test='filters.changeDirection == &quot;up&quot;'> AND " + INDEX_CHANGE + " &gt; 0</if>" +
            "<if test='filters.changeDirection == &quot;down&quot;'> AND " + INDEX_CHANGE + " &lt; 0</if>" +
            "<if test='filters.changeDirection == &quot;flat&quot;'> AND " + INDEX_CHANGE + " = 0</if>" +
            "</where>";

    @Select({"<script>", LATEST_INDEX_CTE,
            "SELECT COUNT(*)", INDEX_JOINS, INDEX_FILTERS, "</script>"})
    long countLatestSites(@Param("filters") Map<String, Object> filters);

    @Select({"<script>", LATEST_INDEX_CTE,
            "SELECT s.site_domain, s.builder_username, s.admin_name, s.user_group, s.theme_name, s.product_category, s.domain_applied_at, s.created_at,",
            "COALESCE(NULLIF(l.server_name, ''), s.server_name) AS server_name,",
            "COALESCE(NULLIF(l.server_ip, ''), s.server_ip) AS server_ip,",
            "COALESCE(l.last_submitted_at, s.last_submitted_at) AS last_submitted_at,",
            "COALESCE(l.product_count, 0) AS product_count, COALESCE(l.index_count, 0) AS index_count,",
            "l.recorded_at AS index_updated_at, " + INDEX_CHANGE + " AS index_change",
            INDEX_JOINS, INDEX_FILTERS,
            "ORDER BY l.recorded_at IS NULL, l.recorded_at DESC, s.created_at DESC LIMIT #{offset}, #{size}",
            "</script>"})
    List<Map<String, Object>> listLatestSites(@Param("filters") Map<String, Object> filters,
                                               @Param("offset") int offset,
                                               @Param("size") int size);

    /** Stream every matching site row for a filtered Excel export. */
    @Select({"<script>", LATEST_INDEX_CTE,
            "SELECT s.site_domain, s.builder_username, s.admin_name, s.user_group, s.theme_name, s.product_category, s.domain_applied_at, s.created_at,",
            "COALESCE(NULLIF(l.server_name, ''), s.server_name) AS server_name,",
            "COALESCE(NULLIF(l.server_ip, ''), s.server_ip) AS server_ip,",
            "COALESCE(l.last_submitted_at, s.last_submitted_at) AS last_submitted_at,",
            "COALESCE(l.product_count, 0) AS product_count, COALESCE(l.index_count, 0) AS index_count,",
            "l.recorded_at AS index_updated_at, " + INDEX_CHANGE + " AS index_change",
            INDEX_JOINS, INDEX_FILTERS,
            "ORDER BY l.recorded_at IS NULL, l.recorded_at DESC, s.created_at DESC",
            "</script>"})
    @Options(fetchSize = 500)
    Cursor<Map<String, Object>> streamLatestSites(@Param("filters") Map<String, Object> filters);

    @Select({"<script>", LATEST_INDEX_CTE,
            "SELECT COALESCE(NULLIF(TRIM(s.builder_username), ''), '未分配') AS builder_username,",
            "COALESCE(NULLIF(TRIM(s.admin_name), ''), '未分配') AS admin_name,",
            "COALESCE(NULLIF(TRIM(s.admin_name), ''), '未分配') AS dimension_name,",
            "COUNT(*) AS site_count, COALESCE(SUM(l.product_count), 0) AS product_count,",
            "COALESCE(SUM(l.index_count), 0) AS index_count, ROUND(AVG(COALESCE(l.index_count, 0)), 2) AS average_index_count,",
            "COALESCE(SUM(" + INDEX_CHANGE + "), 0) AS index_change, ROUND(AVG(" + INDEX_CHANGE + "), 2) AS average_index_change,",
            "MAX(COALESCE(l.last_submitted_at, s.last_submitted_at)) AS last_submitted_at, MAX(l.recorded_at) AS index_updated_at",
            INDEX_JOINS, INDEX_FILTERS,
            "GROUP BY COALESCE(NULLIF(TRIM(s.builder_username), ''), '未分配'), COALESCE(NULLIF(TRIM(s.admin_name), ''), '未分配') ORDER BY builder_username, admin_name",
            "</script>"})
    List<Map<String, Object>> summarizeByBuilder(@Param("filters") Map<String, Object> filters);

    @Select({"<script>", LATEST_INDEX_CTE,
            "SELECT COALESCE(NULLIF(TRIM(COALESCE(NULLIF(l.server_name, ''), s.server_name)), ''), '未分配') AS dimension_name,",
            "COALESCE(NULLIF(TRIM(COALESCE(NULLIF(l.server_ip, ''), s.server_ip)), ''), '') AS server_ip,",
            "COUNT(*) AS site_count, COALESCE(SUM(l.product_count), 0) AS product_count,",
            "COALESCE(SUM(l.index_count), 0) AS index_count, ROUND(AVG(COALESCE(l.index_count, 0)), 2) AS average_index_count,",
            "COALESCE(SUM(" + INDEX_CHANGE + "), 0) AS index_change, ROUND(AVG(" + INDEX_CHANGE + "), 2) AS average_index_change,",
            "MAX(COALESCE(l.last_submitted_at, s.last_submitted_at)) AS last_submitted_at, MAX(l.recorded_at) AS index_updated_at",
            INDEX_JOINS, INDEX_FILTERS,
            "GROUP BY COALESCE(NULLIF(TRIM(COALESCE(NULLIF(l.server_name, ''), s.server_name)), ''), '未分配'), COALESCE(NULLIF(TRIM(COALESCE(NULLIF(l.server_ip, ''), s.server_ip)), ''), '') ORDER BY dimension_name, server_ip",
            "</script>"})
    List<Map<String, Object>> summarizeByServer(@Param("filters") Map<String, Object> filters);

    @Select({"<script>", LATEST_INDEX_CTE,
            "SELECT COUNT(*) AS site_count, COALESCE(SUM(l.product_count), 0) AS product_count,",
            "COALESCE(SUM(l.index_count), 0) AS index_count, ROUND(AVG(COALESCE(l.index_count, 0)), 2) AS average_index_count,",
            "COALESCE(SUM(" + INDEX_CHANGE + "), 0) AS index_change, ROUND(AVG(" + INDEX_CHANGE + "), 2) AS average_index_change,",
            "MAX(COALESCE(l.last_submitted_at, s.last_submitted_at)) AS last_submitted_at, MAX(l.recorded_at) AS index_updated_at",
            INDEX_JOINS, INDEX_FILTERS, "</script>"})
    Map<String, Object> summarizeLatestSites(@Param("filters") Map<String, Object> filters);

    /**
     * 查询指定日期范围内的每日收录趋势（按天聚合）。
     * <p>
     * 对每天的索引数量、商品数量求和，同时统计唯一站点域名数。
     * </p>
     *
     * @param startDate 起始日期（年月日格式）
     * @param endDate   结束日期（年月日格式）
     * @return 每日收录趋势列表，每组包含 date（日期）、total_index（索引总数）、
     *         total_products（商品总数）、site_count（唯一站点数）
     */
    @Select("SELECT DATE(recorded_at) as date, SUM(index_count) as total_index, " +
            "SUM(product_count) as total_products, COUNT(DISTINCT site_domain) as site_count " +
            "FROM site_indexing_history " +
            "WHERE recorded_at >= #{startDate} AND recorded_at < DATE_ADD(#{endDate}, INTERVAL 1 DAY) " +
            "GROUP BY DATE(recorded_at) ORDER BY date")
    List<Map<String, Object>> indexTrend(String startDate, String endDate);

    @Select("SELECT DATE(h.recorded_at) AS date, SUM(h.index_count) AS total_index, " +
            "SUM(h.product_count) AS total_products, COUNT(DISTINCT h.site_domain) AS site_count " +
            "FROM site_indexing_history h JOIN site_info s " +
            "ON LOWER(TRIM(LEADING 'www.' FROM h.site_domain)) = LOWER(TRIM(LEADING 'www.' FROM s.site_domain)) " +
            "WHERE h.recorded_at >= #{startDate} AND h.recorded_at < DATE_ADD(#{endDate}, INTERVAL 1 DAY) " +
            "AND (#{userGroup} IS NULL OR #{userGroup} = '' OR s.user_group = #{userGroup}) " +
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(s.admin_name, #{ownerName}) > 0) " +
            "GROUP BY DATE(h.recorded_at) ORDER BY date")
    List<Map<String, Object>> indexTrendByGroup(@Param("startDate") String startDate,
                                                @Param("endDate") String endDate,
                                                @Param("userGroup") String userGroup,
                                                @Param("ownerName") String ownerName);

    /**
     * 分页查询收录历史列表，按收录时间倒序排列。
     *
     * @param offset 偏移量（从 0 开始）
     * @param size   每页条数
     * @return 收录历史记录列表
     */
    @Select("SELECT * FROM site_indexing_history ORDER BY recorded_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listHistory(int offset, int size);

    @Select("SELECT DATE(h.recorded_at) as date, h.index_count, h.product_count " +
            "FROM site_indexing_history h JOIN site_info s " +
            "ON LOWER(TRIM(LEADING 'www.' FROM h.site_domain)) = LOWER(TRIM(LEADING 'www.' FROM s.site_domain)) " +
            "WHERE h.site_domain = #{domain} AND (#{ownerName} IS NULL OR FIND_IN_SET(s.admin_name, #{ownerName}) > 0) " +
            "ORDER BY h.recorded_at ASC")
    List<Map<String, Object>> listHistoryByDomain(@Param("domain") String domain,
                                                   @Param("ownerName") String ownerName);

    /**
     * 统计收录历史总记录数。
     *
     * @return 收录历史总记录数
     */
    @Select("SELECT COUNT(*) FROM site_indexing_history")
    long countHistory();
}
