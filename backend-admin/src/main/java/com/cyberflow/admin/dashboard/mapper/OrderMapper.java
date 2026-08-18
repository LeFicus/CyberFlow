package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 订单数据访问接口。
 * <p>
 * 提供订单数据的汇总统计、趋势分析和分页查询操作，直接操作 orders 表。
 * 支持按日期范围、管理员等维度进行筛选和聚合统计。
 * </p>
 *
 * @author CyberFlow
 */
@Mapper
public interface OrderMapper {

    /** Delete every order across both user groups. This endpoint is admin-only. */
    @Delete("DELETE FROM orders")
    int deleteAllOrders();

    /** Reset every order crawl cursor so the next crawl starts from the first page. */
    @Delete("UPDATE crawl_cursor SET cursor_value = '0', last_sync_at = NOW() " +
            "WHERE cursor_key LIKE 'order_crawler%'")
    int resetOrderCursors();

    String FILTER_SQL = "<where>" +
            "<if test='orderId != null and orderId != &quot;&quot;'> AND CAST(id AS CHAR) LIKE CONCAT('%', #{orderId}, '%')</if>" +
            "<if test='adminName != null and adminName != &quot;&quot;'> AND admin_name LIKE CONCAT('%', #{adminName}, '%')</if>" +
            "<if test='userGroup != null and userGroup != &quot;&quot;'> AND user_group = #{userGroup}</if>" +
            "<if test='ownerName != null'> AND FIND_IN_SET(admin_name, #{ownerName}) &gt; 0</if>" +
            "<if test='domain != null and domain != &quot;&quot;'> AND product_host LIKE CONCAT('%', #{domain}, '%')</if>" +
            "<if test='payStatus != null and payStatus != &quot;&quot;'> AND pay_status_text = #{payStatus}</if>" +
            "<if test='currency != null and currency != &quot;&quot;'> AND currency = #{currency}</if>" +
            "<if test='country != null and country != &quot;&quot;'> AND customer_ip_country LIKE CONCAT('%', #{country}, '%')</if>" +
            "<if test='startDate != null and startDate != &quot;&quot;'> AND create_time &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>" +
            "<if test='endDate != null and endDate != &quot;&quot;'> AND create_time &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>" +
            "</where>";

    @Select({"<script>", "SELECT COUNT(*) FROM orders", FILTER_SQL, "</script>"})
    long countOrdersFiltered(@Param("orderId") String orderId,
                             @Param("adminName") String adminName,
                             @Param("userGroup") String userGroup,
                             @Param("domain") String domain,
                             @Param("payStatus") String payStatus,
                             @Param("currency") String currency,
                             @Param("country") String country,
                             @Param("ownerName") String ownerName,
                             @Param("startDate") String startDate,
                             @Param("endDate") String endDate);

    @Select({"<script>", "SELECT * FROM orders", FILTER_SQL,
            "ORDER BY create_time DESC LIMIT #{offset}, #{size}", "</script>"})
    List<Map<String, Object>> listOrdersFiltered(@Param("orderId") String orderId,
                                                 @Param("adminName") String adminName,
                                                 @Param("userGroup") String userGroup,
                                                 @Param("domain") String domain,
                                                 @Param("payStatus") String payStatus,
                                                 @Param("currency") String currency,
                                                 @Param("country") String country,
                                                 @Param("ownerName") String ownerName,
                                                 @Param("startDate") String startDate,
                                                 @Param("endDate") String endDate,
                                                 @Param("offset") int offset,
                                                 @Param("size") int size);

    @Select({"<script>",
            "SELECT COUNT(*) AS total_count, COALESCE(SUM(amount), 0) AS total_amount,",
            "COUNT(CASE WHEN pay_status_text = '已支付' THEN 1 END) AS paid_count,",
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS paid_amount",
            "FROM orders", FILTER_SQL, "</script>"})
    Map<String, Object> summarizeOrdersFiltered(@Param("orderId") String orderId,
                                                @Param("adminName") String adminName,
                                                @Param("userGroup") String userGroup,
                                                @Param("domain") String domain,
                                                @Param("payStatus") String payStatus,
                                                @Param("currency") String currency,
                                                @Param("country") String country,
                                                @Param("ownerName") String ownerName,
                                                @Param("startDate") String startDate,
                                                @Param("endDate") String endDate);

    /** Overview metrics follow monthly_revenue_conversion.py: order ID first. */
    @Select("SELECT COUNT(DISTINCT CONCAT(user_group, CHAR(31), CAST(id AS CHAR))) AS deduplicated_orders, " +
            "COUNT(DISTINCT CASE WHEN pay_status_text = '已支付' " +
            "THEN CONCAT(user_group, CHAR(31), CAST(id AS CHAR)) END) AS successful_orders, " +
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS successful_amount " +
            "FROM orders")
    Map<String, Object> businessSummary();

    @Select({"<script>",
            "SELECT COUNT(DISTINCT CONCAT(user_group, CHAR(31), CAST(id AS CHAR))) AS deduplicated_orders, " +
            "COUNT(DISTINCT CASE WHEN pay_status_text = '已支付' " +
            "THEN CONCAT(user_group, CHAR(31), CAST(id AS CHAR)) END) AS successful_orders, " +
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS successful_amount " +
            "FROM orders WHERE create_time &gt;= #{startDateTime} AND create_time &lt; #{endDateTime} " +
            "AND (#{userGroup} IS NULL OR #{userGroup} = '' OR user_group = #{userGroup}) " +
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) &gt; 0)",
            "</script>"})
    Map<String, Object> businessSummaryByGroup(@Param("startDateTime") String startDateTime,
                                               @Param("endDateTime") String endDateTime,
                                               @Param("userGroup") String userGroup,
                                               @Param("ownerName") String ownerName);

    /**
     * 查询订单总体摘要，包括总订单数和总金额。
     *
     * @return 包含 COUNT(*)（总订单数）和 total_amount（总金额）的 Map
     */
    @Select("SELECT COUNT(*), COALESCE(SUM(amount), 0) as total_amount FROM orders")
    Map<String, Object> orderSummary();

    @Select("SELECT COUNT(*) AS total_count, COALESCE(SUM(amount), 0) AS total_amount, " +
            "COUNT(CASE WHEN pay_status_text = '已支付' THEN 1 END) AS paid_count, " +
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS paid_amount " +
            "FROM orders WHERE (#{userGroup} IS NULL OR #{userGroup} = '' OR user_group = #{userGroup}) " +
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) > 0)")
    Map<String, Object> orderSummaryByGroup(@Param("userGroup") String userGroup,
                                             @Param("ownerName") String ownerName);

    /**
     * 查询当日订单摘要，包括今日订单数和今日订单总金额。
     *
     * @return 包含 COUNT(*)（今日订单数）和 total_amount（今日总金额）的 Map
     */
    @Select({"<script>",
            "SELECT COUNT(CASE WHEN pay_status_text = '已支付' THEN 1 END) AS successful_orders,",
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS successful_amount",
            "FROM orders WHERE create_time &gt;= #{startDateTime}",
            "AND create_time &lt; #{endDateTime}",
            "AND (#{userGroup} IS NULL OR #{userGroup} = '' OR user_group = #{userGroup}) " +
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) &gt; 0)",
            "</script>"})
    Map<String, Object> todaySummaryByGroup(@Param("startDateTime") String startDateTime,
                                            @Param("endDateTime") String endDateTime,
                                            @Param("userGroup") String userGroup,
                                            @Param("ownerName") String ownerName);

    /**
     * 查询指定日期范围内的每日订单趋势（按天聚合）。
     *
     * @param startDate 起始日期（年月日格式）
     * @param endDate   结束日期（年月日格式）
     * @return 每日订单趋势列表，每组包含 date（日期）、count（订单数）、amount（金额）
     */
    @Select("SELECT DATE(create_time) as date, COUNT(DISTINCT CASE " +
            "WHEN TRIM(COALESCE(product_host, '')) <> '' " +
            "AND TRIM(COALESCE(shipping_email, '')) <> '' " +
            "THEN CONCAT(LOWER(TRIM(product_host)), CHAR(31), LOWER(TRIM(shipping_email))) " +
            "END) as count, " +
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) as amount " +
            "FROM orders WHERE create_time >= #{startDateTime} AND create_time < #{endDateTime} " +
            "GROUP BY DATE(create_time) ORDER BY date")
    List<Map<String, Object>> orderTrend(@Param("startDateTime") String startDateTime,
                                         @Param("endDateTime") String endDateTime);

    @Select("SELECT DATE(create_time) AS date, " +
            "COUNT(DISTINCT CONCAT(user_group, CHAR(31), CAST(id AS CHAR))) AS count, " +
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS amount " +
            "FROM orders WHERE create_time >= #{startDateTime} AND create_time < #{endDateTime} " +
            "AND (#{userGroup} IS NULL OR #{userGroup} = '' OR user_group = #{userGroup}) " +
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) > 0) " +
            "GROUP BY DATE(create_time) ORDER BY date")
    List<Map<String, Object>> orderTrendByGroup(@Param("startDateTime") String startDateTime,
                                                @Param("endDateTime") String endDateTime,
                                                @Param("userGroup") String userGroup,
                                                @Param("ownerName") String ownerName);

    /**
     * 按管理员分组统计订单数量和金额，结果按订单数量降序排列。
     *
     * @return 每组包含 admin_name、count（订单数）、amount（总金额）的列表
     */
    @Select("SELECT admin_name, COUNT(*) as count, " +
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) as amount " +
            "FROM orders GROUP BY admin_name ORDER BY count DESC")
    List<Map<String, Object>> countByAdmin();

    @Select("SELECT admin_name, " +
            "COUNT(DISTINCT CONCAT(user_group, CHAR(31), CAST(id AS CHAR))) AS count, " +
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS amount " +
            "FROM orders WHERE (#{userGroup} IS NULL OR #{userGroup} = '' OR user_group = #{userGroup}) " +
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) > 0) " +
            "GROUP BY admin_name ORDER BY count DESC")
    List<Map<String, Object>> countByAdminForGroup(@Param("userGroup") String userGroup,
                                                   @Param("ownerName") String ownerName);

    @Select("SELECT COALESCE(user_group, '未分组') AS user_group, " +
            "COUNT(*) AS total_count, " +
            "COUNT(CASE WHEN pay_status_text = '已支付' THEN 1 END) AS paid_count, " +
            "COALESCE(SUM(CASE WHEN pay_status_text = '已支付' THEN amount ELSE 0 END), 0) AS paid_amount " +
            "FROM orders WHERE (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) > 0) " +
            "GROUP BY user_group ORDER BY user_group")
    List<Map<String, Object>> summarizeByGroup(@Param("ownerName") String ownerName);

    /**
     * 按模板名称分组统计订单数量和金额，结果按订单数量降序排列。
     *
     * @return 每组包含 theme_name、count（订单数）、amount（总金额）的列表
     */
    @Select("SELECT theme_name, COUNT(*) as count, COALESCE(SUM(amount), 0) as amount " +
            "FROM orders GROUP BY theme_name ORDER BY count DESC")
    List<Map<String, Object>> countByTheme();

    /**
     * 按货币类型分组统计订单数量。
     *
     * @return 每组包含 currency（货币代码）和 count（订单数）的列表
     */
    @Select("SELECT currency, COUNT(*) as count FROM orders GROUP BY currency")
    List<Map<String, Object>> countByCurrency();

    @Select("SELECT currency, COUNT(*) AS count FROM orders " +
            "WHERE (#{userGroup} IS NULL OR #{userGroup} = '' OR user_group = #{userGroup}) " +
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) > 0) " +
            "GROUP BY currency")
    List<Map<String, Object>> countByCurrencyForGroup(@Param("userGroup") String userGroup,
                                                      @Param("ownerName") String ownerName);

    /**
     * 分页查询全部订单列表，按创建时间倒序排列。
     *
     * @param offset 偏移量（从 0 开始）
     * @param size   每页条数
     * @return 订单信息列表
     */
    @Select("SELECT * FROM orders ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listOrders(int offset, int size);

    /**
     * 统计订单总数。
     *
     * @return 订单总记录数
     */
    @Select("SELECT COUNT(*) FROM orders")
    long countOrders();

    /**
     * 按管理员名称分页查询订单列表，按创建时间倒序排列。
     *
     * @param adminName 管理员名称
     * @param offset    偏移量
     * @param size      每页条数
     * @return 符合条件的订单列表
     */
    @Select("SELECT * FROM orders WHERE admin_name = #{adminName} ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listOrdersByAdmin(String adminName, int offset, int size);

    /**
     * 按管理员名称统计订单数量。
     *
     * @param adminName 管理员名称
     * @return 符合条件的订单数量
     */
    @Select("SELECT COUNT(*) FROM orders WHERE admin_name = #{adminName}")
    long countOrdersByAdmin(String adminName);

    /**
     * 按日期范围分页查询订单列表，按创建时间倒序排列。
     *
     * @param startDate 起始日期（年月日格式）
     * @param endDate   结束日期（年月日格式）
     * @param offset    偏移量
     * @param size      每页条数
     * @return 符合条件的订单列表
     */
    @Select("SELECT * FROM orders WHERE create_time >= #{startDate} AND create_time <= #{endDate} " +
            "ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listOrdersByDateRange(String startDate, String endDate, int offset, int size);

    /**
     * 按日期范围统计订单数量。
     *
     * @param startDate 起始日期（年月日格式）
     * @param endDate   结束日期（年月日格式）
     * @return 符合日期范围的订单数量
     */
    @Select("SELECT COUNT(*) FROM orders WHERE create_time >= #{startDate} AND create_time <= #{endDate}")
    long countOrdersByDateRange(String startDate, String endDate);

    @Select("SELECT * FROM orders WHERE product_host = #{domain} " +
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) > 0) " +
            "ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listOrdersByDomain(@Param("domain") String domain,
                                                  @Param("ownerName") String ownerName,
                                                  @Param("offset") int offset,
                                                  @Param("size") int size);

    @Select("SELECT COUNT(*) FROM orders WHERE product_host = #{domain} " +
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) > 0)")
    long countOrdersByDomain(@Param("domain") String domain, @Param("ownerName") String ownerName);

    @Select("SELECT * FROM orders WHERE product_host = #{domain} " +
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) > 0) " +
            "AND create_time >= #{startDate} AND create_time <= #{endDate} " +
            "ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listOrdersByDomainAndDateRange(@Param("domain") String domain,
                                                              @Param("ownerName") String ownerName,
                                                              @Param("startDate") String startDate,
                                                              @Param("endDate") String endDate,
                                                              @Param("offset") int offset,
                                                              @Param("size") int size);

    @Select("SELECT COUNT(*) FROM orders WHERE product_host = #{domain} " +
            "AND (#{ownerName} IS NULL OR FIND_IN_SET(admin_name, #{ownerName}) > 0) " +
            "AND create_time >= #{startDate} AND create_time <= #{endDate}")
    long countOrdersByDomainAndDateRange(@Param("domain") String domain,
                                          @Param("ownerName") String ownerName,
                                          @Param("startDate") String startDate,
                                          @Param("endDate") String endDate);
}
