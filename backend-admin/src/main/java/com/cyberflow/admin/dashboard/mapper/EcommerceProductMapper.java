package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface EcommerceProductMapper {

    @Select("SELECT COUNT(*) FROM scraped_data.ecommerce_products")
    long countProducts();

    @Select("SELECT source_domain, COUNT(*) as count FROM scraped_data.ecommerce_products " +
            "GROUP BY source_domain ORDER BY count DESC")
    List<Map<String, Object>> countByDomain();

    @Select("SELECT custom_category, COUNT(*) as count FROM scraped_data.ecommerce_products " +
            "GROUP BY custom_category ORDER BY count DESC")
    List<Map<String, Object>> countByCategory();

    @Select("SELECT language, COUNT(*) as count FROM scraped_data.ecommerce_products GROUP BY language")
    List<Map<String, Object>> countByLanguage();

    @Select("SELECT * FROM scraped_data.ecommerce_products ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listProducts(int offset, int size);

    @Select("SELECT * FROM scraped_data.ecommerce_products WHERE source_domain = #{domain} " +
            "ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listProductsByDomain(String domain, int offset, int size);

    @Select("SELECT COUNT(*) FROM scraped_data.ecommerce_products WHERE source_domain = #{domain}")
    long countProductsByDomain(String domain);
}
