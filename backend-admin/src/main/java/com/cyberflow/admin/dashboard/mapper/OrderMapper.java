package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    @Select("SELECT COUNT(*), COALESCE(SUM(amount), 0) as total_amount FROM orders")
    Map<String, Object> orderSummary();

    @Select("SELECT COUNT(*), COALESCE(SUM(amount), 0) as total_amount FROM orders " +
            "WHERE DATE(create_time) = CURDATE()")
    Map<String, Object> todaySummary();

    @Select("SELECT DATE(create_time) as date, COUNT(*) as count, COALESCE(SUM(amount), 0) as amount " +
            "FROM orders WHERE create_time >= #{startDate} AND create_time <= #{endDate} " +
            "GROUP BY DATE(create_time) ORDER BY date")
    List<Map<String, Object>> orderTrend(String startDate, String endDate);

    @Select("SELECT admin_name, COUNT(*) as count, COALESCE(SUM(amount), 0) as amount " +
            "FROM orders GROUP BY admin_name ORDER BY count DESC")
    List<Map<String, Object>> countByAdmin();

    @Select("SELECT theme_name, COUNT(*) as count, COALESCE(SUM(amount), 0) as amount " +
            "FROM orders GROUP BY theme_name ORDER BY count DESC")
    List<Map<String, Object>> countByTheme();

    @Select("SELECT currency, COUNT(*) as count FROM orders GROUP BY currency")
    List<Map<String, Object>> countByCurrency();

    @Select("SELECT * FROM orders ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listOrders(int offset, int size);

    @Select("SELECT COUNT(*) FROM orders")
    long countOrders();

    @Select("SELECT * FROM orders WHERE admin_name = #{adminName} ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listOrdersByAdmin(String adminName, int offset, int size);

    @Select("SELECT COUNT(*) FROM orders WHERE admin_name = #{adminName}")
    long countOrdersByAdmin(String adminName);

    @Select("SELECT * FROM orders WHERE create_time >= #{startDate} AND create_time <= #{endDate} " +
            "ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listOrdersByDateRange(String startDate, String endDate, int offset, int size);

    @Select("SELECT COUNT(*) FROM orders WHERE create_time >= #{startDate} AND create_time <= #{endDate}")
    long countOrdersByDateRange(String startDate, String endDate);
}
