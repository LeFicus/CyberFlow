package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SiteIndexingHistoryMapper {

    @Select("SELECT DATE(recorded_at) as date, SUM(index_count) as total_index, " +
            "SUM(product_count) as total_products, COUNT(DISTINCT site_domain) as site_count " +
            "FROM site_indexing_history " +
            "WHERE recorded_at >= #{startDate} AND recorded_at <= #{endDate} " +
            "GROUP BY DATE(recorded_at) ORDER BY date")
    List<Map<String, Object>> indexTrend(String startDate, String endDate);

    @Select("SELECT * FROM site_indexing_history ORDER BY recorded_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listHistory(int offset, int size);

    @Select("SELECT COUNT(*) FROM site_indexing_history")
    long countHistory();
}
