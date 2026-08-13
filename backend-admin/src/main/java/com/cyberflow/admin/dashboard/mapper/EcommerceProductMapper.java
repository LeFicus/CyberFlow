package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 电商商品数据访问接口。
 * <p>
 * 提供商品的统计、分组和分页查询操作，操作 scraped_data 库中的 ecommerce_products 表。
 * 支持按来源域名、自定义分类、语言等维度进行筛选和聚合统计。
 * </p>
 *
 * @author CyberFlow
 */
@Mapper
public interface EcommerceProductMapper {

    /**
     * 统计商品总数。
     *
     * @return 商品总记录数
     */
    @Select("SELECT COUNT(*) FROM scraped_data.ecommerce_products")
    long countProducts();

    /**
     * 按来源域名分组统计商品数量，结果按数量降序排列。
     *
     * @return 每组包含 source_domain（域名）和 count（商品数）的列表
     */
    @Select("SELECT source_domain, COUNT(*) as count FROM scraped_data.ecommerce_products " +
            "GROUP BY source_domain ORDER BY count DESC")
    List<Map<String, Object>> countByDomain();

    /**
     * 按自定义分类分组统计商品数量，结果按数量降序排列。
     *
     * @return 每组包含 custom_category（分类名）和 count（商品数）的列表
     */
    @Select("SELECT custom_category, COUNT(*) as count FROM scraped_data.ecommerce_products " +
            "GROUP BY custom_category ORDER BY count DESC")
    List<Map<String, Object>> countByCategory();

    /**
     * 按语言分组统计商品数量。
     *
     * @return 每组包含 language（语言）和 count（商品数）的列表
     */
    @Select("SELECT language, COUNT(*) as count FROM scraped_data.ecommerce_products GROUP BY language")
    List<Map<String, Object>> countByLanguage();

    /**
     * 分页查询全部商品列表，按创建时间倒序排列。
     *
     * @param offset 偏移量（从 0 开始）
     * @param size   每页条数
     * @return 商品信息列表
     */
    @Select("SELECT * FROM scraped_data.ecommerce_products ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listProducts(int offset, int size);

    /**
     * 按来源域名分页查询商品列表，按创建时间倒序排列。
     *
     * @param domain 商品来源域名
     * @param offset 偏移量
     * @param size   每页条数
     * @return 符合条件的商品列表
     */
    @Select("SELECT * FROM scraped_data.ecommerce_products WHERE source_domain = #{domain} " +
            "ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listProductsByDomain(String domain, int offset, int size);

    /**
     * 按来源域名统计商品数量。
     *
     * @param domain 商品来源域名
     * @return 符合条件的商品数量
     */
    @Select("SELECT COUNT(*) FROM scraped_data.ecommerce_products WHERE source_domain = #{domain}")
    long countProductsByDomain(String domain);

    /** Returns normalized products for a streaming import-template export. */
    @Select("SELECT sku, name, description, regular_price, categories, images, cf_opingts, source_domain " +
            "FROM scraped_data.ecommerce_products " +
            "WHERE (#{domain} IS NULL OR #{domain} = '' OR source_domain = #{domain}) ORDER BY id")
    List<Map<String, Object>> listProductsForExport(String domain);
}
