package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 站点信息数据访问接口。
 * <p>
 * 提供站点信息的统计、分组和分页查询操作，直接操作 site_info 表。
 * 支持按管理员、模板名称等维度进行筛选和聚合统计。
 * </p>
 *
 * @author CyberFlow
 */
@Mapper
public interface SiteInfoMapper {

    /** Count sites with combinable administrator, domain and creation-date filters. */
    @Select({
            "<script>",
            "SELECT COUNT(*) FROM site_info",
            "<where>",
            "<if test='adminName != null and adminName != &quot;&quot;'> AND admin_name LIKE CONCAT('%', #{adminName}, '%')</if>",
            "<if test='userGroup != null and userGroup != &quot;&quot;'> AND user_group = #{userGroup}</if>",
            "<if test='domain != null and domain != &quot;&quot;'> AND site_domain LIKE CONCAT('%', #{domain}, '%')</if>",
            "<if test='startDate != null and startDate != &quot;&quot;'> AND created_at &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>",
            "<if test='endDate != null and endDate != &quot;&quot;'> AND created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>",
            "</where>",
            "</script>"
    })
    long countSitesFiltered(@Param("adminName") String adminName,
                            @Param("userGroup") String userGroup,
                            @Param("domain") String domain,
                            @Param("startDate") String startDate,
                            @Param("endDate") String endDate);

    /** List sites using the same combined filters as {@link #countSitesFiltered}. */
    @Select({
            "<script>",
            "SELECT * FROM site_info",
            "<where>",
            "<if test='adminName != null and adminName != &quot;&quot;'> AND admin_name LIKE CONCAT('%', #{adminName}, '%')</if>",
            "<if test='userGroup != null and userGroup != &quot;&quot;'> AND user_group = #{userGroup}</if>",
            "<if test='domain != null and domain != &quot;&quot;'> AND site_domain LIKE CONCAT('%', #{domain}, '%')</if>",
            "<if test='startDate != null and startDate != &quot;&quot;'> AND created_at &gt;= CONCAT(#{startDate}, ' 00:00:00')</if>",
            "<if test='endDate != null and endDate != &quot;&quot;'> AND created_at &lt; DATE_ADD(#{endDate}, INTERVAL 1 DAY)</if>",
            "</where>",
            "ORDER BY created_at DESC LIMIT #{offset}, #{size}",
            "</script>"
    })
    List<Map<String, Object>> listSitesFiltered(@Param("adminName") String adminName,
                                                @Param("userGroup") String userGroup,
                                                @Param("domain") String domain,
                                                @Param("startDate") String startDate,
                                                @Param("endDate") String endDate,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    /**
     * 统计站点总数。
     *
     * @return 站点总记录数
     */
    @Select("SELECT COUNT(*) FROM site_info")
    long countSites();

    @Select("SELECT COUNT(*) FROM site_info WHERE (#{userGroup} IS NULL OR #{userGroup} = '' OR user_group = #{userGroup})")
    long countSitesByGroup(@Param("userGroup") String userGroup);

    @Select({"<script>", "SELECT COUNT(*) FROM site_info",
            "<where>",
            "<if test='userGroup != null and userGroup != &quot;&quot;'> AND user_group = #{userGroup}</if>",
            "<if test='startDateTime != null and startDateTime != &quot;&quot;'> AND created_at &gt;= #{startDateTime}</if>",
            "<if test='endDateTime != null and endDateTime != &quot;&quot;'> AND created_at &lt; #{endDateTime}</if>",
            "</where>", "</script>"})
    long countSitesByGroupAndDateRange(@Param("userGroup") String userGroup,
                                       @Param("startDateTime") String startDateTime,
                                       @Param("endDateTime") String endDateTime);

    /**
     * 按管理员分组统计站点数量，结果按数量降序排列。
     *
     * @return 每组包含 admin_name（管理员名称）和 count（站点数量）的列表
     */
    @Select("SELECT admin_name, COUNT(*) as count FROM site_info GROUP BY admin_name ORDER BY count DESC")
    List<Map<String, Object>> countByAdmin();

    @Select("SELECT admin_name, COUNT(*) AS count FROM site_info " +
            "WHERE (#{userGroup} IS NULL OR #{userGroup} = '' OR user_group = #{userGroup}) " +
            "GROUP BY admin_name ORDER BY count DESC")
    List<Map<String, Object>> countByAdminForGroup(@Param("userGroup") String userGroup);

    @Select("SELECT COALESCE(user_group, '未分组') AS user_group, COUNT(*) AS site_count " +
            "FROM site_info GROUP BY user_group ORDER BY user_group")
    List<Map<String, Object>> summarizeByGroup();

    /**
     * 按模板名称分组统计站点数量，结果按数量降序排列。
     *
     * @return 每组包含 theme_name（模板名称）和 count（站点数量）的列表
     */
    @Select("SELECT theme_name, COUNT(*) as count FROM site_info GROUP BY theme_name ORDER BY count DESC")
    List<Map<String, Object>> countByTheme();

    /**
     * 按商品分类分组统计站点数量，结果按数量降序排列。
     *
     * @return 每组包含 product_category（商品分类）和 count（站点数量）的列表
     */
    @Select("SELECT product_category, COUNT(*) as count FROM site_info GROUP BY product_category ORDER BY count DESC")
    List<Map<String, Object>> countByCategory();

    @Select("SELECT product_category, COUNT(*) AS count FROM site_info " +
            "WHERE (#{userGroup} IS NULL OR #{userGroup} = '' OR user_group = #{userGroup}) " +
            "GROUP BY product_category ORDER BY count DESC")
    List<Map<String, Object>> countByCategoryForGroup(@Param("userGroup") String userGroup);

    /**
     * 分页查询站点列表，按创建时间倒序排列。
     *
     * @param offset 偏移量（从 0 开始）
     * @param size   每页条数
     * @return 站点信息列表
     */
    @Select("SELECT * FROM site_info ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listSites(int offset, int size);

    /**
     * 按管理员名称统计站点数量。
     *
     * @param adminName 管理员名称
     * @return 符合条件的站点数量
     */
    @Select("SELECT COUNT(*) FROM site_info WHERE admin_name = #{adminName}")
    long countSitesByAdmin(String adminName);

    /**
     * 按模板名称统计站点数量。
     *
     * @param themeName 模板名称
     * @return 符合条件的站点数量
     */
    @Select("SELECT COUNT(*) FROM site_info WHERE theme_name = #{themeName}")
    long countSitesByTheme(String themeName);

    /**
     * 按管理员名称分页查询站点列表，按创建时间倒序排列。
     *
     * @param adminName 管理员名称
     * @param offset    偏移量
     * @param size      每页条数
     * @return 符合条件的站点列表
     */
    @Select("SELECT * FROM site_info WHERE admin_name = #{adminName} ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listSitesByAdmin(String adminName, int offset, int size);

    /**
     * 按模板名称分页查询站点列表，按创建时间倒序排列。
     *
     * @param themeName 模板名称
     * @param offset    偏移量
     * @param size      每页条数
     * @return 符合条件的站点列表
     */
    @Select("SELECT * FROM site_info WHERE theme_name = #{themeName} ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listSitesByTheme(String themeName, int offset, int size);
}
