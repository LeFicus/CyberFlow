package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
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

    /** Count products matching the optional domain, category and name filters. */
    @Select("<script>SELECT COUNT(*) FROM scraped_data.ecommerce_products " +
            "<where>" +
            "<if test='domain != null and domain.trim() != \"\"'>" +
            "AND LOWER(source_domain) LIKE CONCAT('%', LOWER(TRIM(#{domain})), '%') </if>" +
            "<if test='category != null and category.trim() != \"\"'>" +
            "AND (LOWER(categories) LIKE CONCAT('%', LOWER(TRIM(#{category})), '%') " +
            "OR LOWER(custom_category) LIKE CONCAT('%', LOWER(TRIM(#{category})), '%')) </if>" +
            "<if test='name != null and name.trim() != \"\"'>" +
            "AND LOWER(name) LIKE CONCAT('%', LOWER(TRIM(#{name})), '%') </if>" +
            "</where></script>")
    long countProductsFiltered(@Param("domain") String domain,
                               @Param("category") String category,
                               @Param("name") String name);

    /** List products matching the optional filters. */
    @Select("<script>SELECT * FROM scraped_data.ecommerce_products " +
            "<where>" +
            "<if test='domain != null and domain.trim() != \"\"'>" +
            "AND LOWER(source_domain) LIKE CONCAT('%', LOWER(TRIM(#{domain})), '%') </if>" +
            "<if test='category != null and category.trim() != \"\"'>" +
            "AND (LOWER(categories) LIKE CONCAT('%', LOWER(TRIM(#{category})), '%') " +
            "OR LOWER(custom_category) LIKE CONCAT('%', LOWER(TRIM(#{category})), '%')) </if>" +
            "<if test='name != null and name.trim() != \"\"'>" +
            "AND LOWER(name) LIKE CONCAT('%', LOWER(TRIM(#{name})), '%') </if>" +
            "</where>ORDER BY created_at DESC LIMIT #{offset}, #{size}</script>")
    List<Map<String, Object>> listProductsFiltered(@Param("domain") String domain,
                                                   @Param("category") String category,
                                                   @Param("name") String name,
                                                   @Param("offset") int offset,
                                                   @Param("size") int size);

    /** Return the Redis fingerprint fields for the selected products. */
    @Select("<script>SELECT sku, source_domain FROM scraped_data.ecommerce_products " +
            "WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach></script>")
    List<Map<String, Object>> listProductFingerprintsByIds(@Param("ids") List<Long> ids);

    /** Delete only products selected by their database IDs. */
    @Delete("<script>DELETE FROM scraped_data.ecommerce_products " +
            "WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach></script>")
    int deleteProductsByIds(@Param("ids") List<Long> ids);

    /** Returns normalized products for a streaming import-template export. */
    @Select("SELECT sku, name, description, regular_price, categories, images, cf_opingts, source_domain " +
            "FROM scraped_data.ecommerce_products " +
            "WHERE (#{domain} IS NULL OR #{domain} = '' OR source_domain = #{domain}) ORDER BY id")
    List<Map<String, Object>> listProductsForExport(String domain);

    /** Export products filtered by exact source domain and exact custom category. */
    @Select("<script>SELECT sku, name, description, regular_price, categories, images, cf_opingts, " +
            "custom_category, source_domain, language FROM scraped_data.ecommerce_products " +
            "<where>" +
            "<if test='domain != null and domain.trim() != \"\"'>" +
            "AND LOWER(source_domain) = LOWER(TRIM(#{domain})) </if>" +
            "<if test='customCategory != null and customCategory.trim() != \"\"'>" +
            "AND LOWER(custom_category) = LOWER(TRIM(#{customCategory})) </if>" +
            "</where>ORDER BY id</script>")
    List<Map<String, Object>> listProductsForExcelExport(@Param("domain") String domain,
                                                         @Param("customCategory") String customCategory);
}
