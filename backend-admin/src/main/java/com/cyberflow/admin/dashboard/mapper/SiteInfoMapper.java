package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SiteInfoMapper {

    @Select("SELECT COUNT(*) FROM site_info")
    long countSites();

    @Select("SELECT admin_name, COUNT(*) as count FROM site_info GROUP BY admin_name ORDER BY count DESC")
    List<Map<String, Object>> countByAdmin();

    @Select("SELECT theme_name, COUNT(*) as count FROM site_info GROUP BY theme_name ORDER BY count DESC")
    List<Map<String, Object>> countByTheme();

    @Select("SELECT product_category, COUNT(*) as count FROM site_info GROUP BY product_category ORDER BY count DESC")
    List<Map<String, Object>> countByCategory();

    @Select("SELECT * FROM site_info ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listSites(int offset, int size);

    @Select("SELECT COUNT(*) FROM site_info WHERE admin_name = #{adminName}")
    long countSitesByAdmin(String adminName);

    @Select("SELECT COUNT(*) FROM site_info WHERE theme_name = #{themeName}")
    long countSitesByTheme(String themeName);

    @Select("SELECT * FROM site_info WHERE admin_name = #{adminName} ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listSitesByAdmin(String adminName, int offset, int size);

    @Select("SELECT * FROM site_info WHERE theme_name = #{themeName} ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listSitesByTheme(String themeName, int offset, int size);
}
