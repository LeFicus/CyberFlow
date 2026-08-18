package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
