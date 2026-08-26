package com.cyberflow.admin.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.ResultSetType;

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

    /** Distinct source domains available for new-site creation. */
    @Select("SELECT DISTINCT TRIM(p.source_domain) FROM scraped_data.ecommerce_products p " +
            "WHERE p.source_domain IS NOT NULL AND TRIM(p.source_domain) <> '' " +
            "ORDER BY 1")
    List<String> listDistinctSourceDomains();

    /** Raw category values are split into leaf options by the new-site service. */
    @Select("SELECT DISTINCT TRIM(p.categories) FROM scraped_data.ecommerce_products p " +
            "WHERE p.categories IS NOT NULL AND TRIM(p.categories) <> '' " +
            "ORDER BY 1")
    List<String> listDistinctProductCategories();

    /**
     * 统计商品总数。
     *
     * @return 商品总记录数
     */
    @Select("SELECT COUNT(*) FROM scraped_data.ecommerce_products")
    long countProducts();

    /** Count products whose source domain belongs to the selected site group. */
    @Select({"<script>",
            "SELECT COUNT(*) FROM scraped_data.ecommerce_products p",
            "LEFT JOIN site_info s ON LOWER(CASE WHEN LEFT(p.source_domain, 4) = 'www.'",
            "THEN SUBSTRING(p.source_domain, 5) ELSE p.source_domain END) = LOWER(CASE WHEN LEFT(s.site_domain, 4) = 'www.'",
            "THEN SUBSTRING(s.site_domain, 5) ELSE s.site_domain END)",
            "<where>",
            "<if test='userGroup != null and userGroup != &quot;&quot;'> AND s.user_group = #{userGroup}</if>",
            "<if test='ownerName != null'> AND FIND_IN_SET(s.admin_name, #{ownerName}) &gt; 0</if>",
            "</where>",
            "</script>"})
    long countProductsByGroup(@Param("userGroup") String userGroup,
                               @Param("ownerName") String ownerName);

    /**
     * 按来源域名分组统计商品数量，结果按数量降序排列。
     *
     * @return 每组包含 source_domain（域名）和 count（商品数）的列表
     */
    @Select("SELECT p.source_domain, COUNT(*) as count FROM scraped_data.ecommerce_products p " +
            "LEFT JOIN site_info s ON LOWER(TRIM(LEADING 'www.' FROM p.source_domain)) = LOWER(TRIM(LEADING 'www.' FROM s.site_domain)) " +
            "WHERE (#{ownerName} IS NULL OR FIND_IN_SET(s.admin_name, #{ownerName}) > 0) " +
            "GROUP BY p.source_domain ORDER BY count DESC")
    List<Map<String, Object>> countByDomain(@Param("ownerName") String ownerName);

    /**
     * 按自定义分类分组统计商品数量，结果按数量降序排列。
     *
     * @return 每组包含 custom_category（分类名）和 count（商品数）的列表
     */
    @Select("SELECT p.custom_category, COUNT(*) as count FROM scraped_data.ecommerce_products p " +
            "LEFT JOIN site_info s ON LOWER(TRIM(LEADING 'www.' FROM p.source_domain)) = LOWER(TRIM(LEADING 'www.' FROM s.site_domain)) " +
            "WHERE (#{ownerName} IS NULL OR FIND_IN_SET(s.admin_name, #{ownerName}) > 0) " +
            "GROUP BY p.custom_category ORDER BY count DESC")
    List<Map<String, Object>> countByCategory(@Param("ownerName") String ownerName);

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
    @Select("SELECT id, sku, name, regular_price, categories, custom_category, product_role, source_domain, language, images " +
            "FROM scraped_data.ecommerce_products ORDER BY created_at DESC, id DESC LIMIT #{offset}, #{size}")
    List<Map<String, Object>> listProducts(int offset, int size);

    /** Count products matching the optional domain, category and name filters. */
    @Select("<script>SELECT COUNT(id) FROM scraped_data.ecommerce_products " +
            "<where>" +
            "<if test='ownerName != null'> AND EXISTS (SELECT 1 FROM site_info s WHERE LOWER(TRIM(LEADING 'www.' FROM s.site_domain)) = LOWER(TRIM(LEADING 'www.' FROM ecommerce_products.source_domain)) AND FIND_IN_SET(s.admin_name, #{ownerName}) &gt; 0)</if>" +
            "<if test='domainsFilter != null and domainsFilter.size() > 0'>" +
            "AND <foreach collection='domainsFilter' item='domain' separator=' OR ' open='(' close=')'>" +
            "source_domain LIKE CONCAT(TRIM(#{domain}), '%')" +
            "</foreach></if>" +
            "<if test='customCategoriesFilter != null and customCategoriesFilter.size() > 0'>" +
            "AND <foreach collection='customCategoriesFilter' item='category' separator=' OR ' open='(' close=')'>" +
            "custom_category LIKE CONCAT('%', TRIM(#{category}), '%')" +
            "</foreach></if>" +
            "<if test='productCategoriesFilter != null and productCategoriesFilter.size() > 0'>" +
            "AND <foreach collection='productCategoriesFilter' item='category' separator=' OR ' open='(' close=')'>" +
            "categories LIKE CONCAT('%', TRIM(#{category}), '%')" +
            "</foreach></if>" +
            "<if test='productRolesFilter != null and productRolesFilter.size() > 0'>" +
            "AND product_role IN <foreach collection='productRolesFilter' item='productRole' separator=',' open='(' close=')'>#{productRole}</foreach>" +
            "</if>" +
            "<if test='name != null and name.trim() != \"\"'>" +
            "AND name LIKE CONCAT('%', TRIM(#{name}), '%') </if>" +
            "</where></script>")
    long countProductsFiltered(@Param("domainsFilter") List<String> domainsFilter,
                               @Param("customCategoriesFilter") List<String> customCategoriesFilter,
                               @Param("productCategoriesFilter") List<String> productCategoriesFilter,
                               @Param("productRolesFilter") List<String> productRolesFilter,
                               @Param("name") String name,
                               @Param("ownerName") String ownerName);

    /** List products matching the optional filters. */
    @Select("<script>SELECT id, sku, name, regular_price, categories, custom_category, product_role, source_domain, language, images " +
            "FROM scraped_data.ecommerce_products " +
            "<where>" +
            "<if test='ownerName != null'> AND EXISTS (SELECT 1 FROM site_info s WHERE LOWER(TRIM(LEADING 'www.' FROM s.site_domain)) = LOWER(TRIM(LEADING 'www.' FROM ecommerce_products.source_domain)) AND FIND_IN_SET(s.admin_name, #{ownerName}) &gt; 0)</if>" +
            "<if test='domainsFilter != null and domainsFilter.size() > 0'>" +
            "AND <foreach collection='domainsFilter' item='domain' separator=' OR ' open='(' close=')'>" +
            "source_domain LIKE CONCAT(TRIM(#{domain}), '%')" +
            "</foreach></if>" +
            "<if test='customCategoriesFilter != null and customCategoriesFilter.size() > 0'>" +
            "AND <foreach collection='customCategoriesFilter' item='category' separator=' OR ' open='(' close=')'>" +
            "custom_category LIKE CONCAT('%', TRIM(#{category}), '%')" +
            "</foreach></if>" +
            "<if test='productCategoriesFilter != null and productCategoriesFilter.size() > 0'>" +
            "AND <foreach collection='productCategoriesFilter' item='category' separator=' OR ' open='(' close=')'>" +
            "categories LIKE CONCAT('%', TRIM(#{category}), '%')" +
            "</foreach></if>" +
            "<if test='productRolesFilter != null and productRolesFilter.size() > 0'>" +
            "AND product_role IN <foreach collection='productRolesFilter' item='productRole' separator=',' open='(' close=')'>#{productRole}</foreach>" +
            "</if>" +
            "<if test='name != null and name.trim() != \"\"'>" +
            "AND name LIKE CONCAT('%', TRIM(#{name}), '%') </if>" +
            "</where>ORDER BY created_at DESC, id DESC LIMIT #{offset}, #{size}</script>")
    List<Map<String, Object>> listProductsFiltered(@Param("domainsFilter") List<String> domainsFilter,
                                                   @Param("customCategoriesFilter") List<String> customCategoriesFilter,
                                                   @Param("productCategoriesFilter") List<String> productCategoriesFilter,
                                                   @Param("productRolesFilter") List<String> productRolesFilter,
                                                   @Param("name") String name,
                                                   @Param("ownerName") String ownerName,
                                                   @Param("offset") int offset,
                                                   @Param("size") int size);

    /** Return the Redis fingerprint fields for the selected products. */
    @Select("<script>SELECT sku, source_domain FROM scraped_data.ecommerce_products " +
            "WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach> " +
            "<if test='ownerName != null'>AND EXISTS (SELECT 1 FROM site_info s WHERE LOWER(TRIM(LEADING 'www.' FROM s.site_domain)) = LOWER(TRIM(LEADING 'www.' FROM ecommerce_products.source_domain)) AND FIND_IN_SET(s.admin_name, #{ownerName}) &gt; 0)</if></script>")
    List<Map<String, Object>> listProductFingerprintsByIds(@Param("ids") List<Long> ids,
                                                           @Param("ownerName") String ownerName);

    /** Delete only products selected by their database IDs. */
    @Delete("<script>DELETE FROM scraped_data.ecommerce_products " +
            "WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach> " +
            "<if test='ownerName != null'>AND EXISTS (SELECT 1 FROM site_info s WHERE LOWER(TRIM(LEADING 'www.' FROM s.site_domain)) = LOWER(TRIM(LEADING 'www.' FROM ecommerce_products.source_domain)) AND FIND_IN_SET(s.admin_name, #{ownerName}) &gt; 0)</if>" +
            "</script>")
    int deleteProductsByIds(@Param("ids") List<Long> ids, @Param("ownerName") String ownerName);

    /** Stream crawl fingerprints for all products matching the current list filters. */
    @Select("<script>SELECT sku, source_domain FROM scraped_data.ecommerce_products " +
            "<where>" +
            "<if test='ownerName != null'> AND EXISTS (SELECT 1 FROM site_info s WHERE LOWER(TRIM(LEADING 'www.' FROM s.site_domain)) = LOWER(TRIM(LEADING 'www.' FROM ecommerce_products.source_domain)) AND FIND_IN_SET(s.admin_name, #{ownerName}) &gt; 0)</if>" +
            "<if test='domainsFilter != null and domainsFilter.size() > 0'>" +
            "AND <foreach collection='domainsFilter' item='domain' separator=' OR ' open='(' close=')'>" +
            "source_domain LIKE CONCAT(TRIM(#{domain}), '%')" +
            "</foreach></if>" +
            "<if test='customCategoriesFilter != null and customCategoriesFilter.size() > 0'>" +
            "AND <foreach collection='customCategoriesFilter' item='category' separator=' OR ' open='(' close=')'>" +
            "custom_category LIKE CONCAT('%', TRIM(#{category}), '%')" +
            "</foreach></if>" +
            "<if test='productCategoriesFilter != null and productCategoriesFilter.size() > 0'>" +
            "AND <foreach collection='productCategoriesFilter' item='category' separator=' OR ' open='(' close=')'>" +
            "categories LIKE CONCAT('%', TRIM(#{category}), '%')" +
            "</foreach></if>" +
            "<if test='productRolesFilter != null and productRolesFilter.size() > 0'>" +
            "AND product_role IN <foreach collection='productRolesFilter' item='productRole' separator=',' open='(' close=')'>#{productRole}</foreach>" +
            "</if>" +
            "<if test='name != null and name.trim() != \"\"'>" +
            "AND name LIKE CONCAT('%', TRIM(#{name}), '%') </if>" +
            "</where>ORDER BY id</script>")
    @Options(fetchSize = 500, resultSetType = ResultSetType.FORWARD_ONLY)
    Cursor<Map<String, Object>> streamProductFingerprintsFiltered(@Param("domainsFilter") List<String> domainsFilter,
                                                                    @Param("customCategoriesFilter") List<String> customCategoriesFilter,
                                                                    @Param("productCategoriesFilter") List<String> productCategoriesFilter,
                                                                    @Param("productRolesFilter") List<String> productRolesFilter,
                                                                    @Param("name") String name,
                                                                    @Param("ownerName") String ownerName);

    /** Delete all products matching the current list filters. */
    @Delete("<script>DELETE FROM scraped_data.ecommerce_products " +
            "<where>" +
            "<if test='ownerName != null'> AND EXISTS (SELECT 1 FROM site_info s WHERE LOWER(TRIM(LEADING 'www.' FROM s.site_domain)) = LOWER(TRIM(LEADING 'www.' FROM ecommerce_products.source_domain)) AND FIND_IN_SET(s.admin_name, #{ownerName}) &gt; 0)</if>" +
            "<if test='domainsFilter != null and domainsFilter.size() > 0'>" +
            "AND <foreach collection='domainsFilter' item='domain' separator=' OR ' open='(' close=')'>" +
            "source_domain LIKE CONCAT(TRIM(#{domain}), '%')" +
            "</foreach></if>" +
            "<if test='customCategoriesFilter != null and customCategoriesFilter.size() > 0'>" +
            "AND <foreach collection='customCategoriesFilter' item='category' separator=' OR ' open='(' close=')'>" +
            "custom_category LIKE CONCAT('%', TRIM(#{category}), '%')" +
            "</foreach></if>" +
            "<if test='productCategoriesFilter != null and productCategoriesFilter.size() > 0'>" +
            "AND <foreach collection='productCategoriesFilter' item='category' separator=' OR ' open='(' close=')'>" +
            "categories LIKE CONCAT('%', TRIM(#{category}), '%')" +
            "</foreach></if>" +
            "<if test='productRolesFilter != null and productRolesFilter.size() > 0'>" +
            "AND product_role IN <foreach collection='productRolesFilter' item='productRole' separator=',' open='(' close=')'>#{productRole}</foreach>" +
            "</if>" +
            "<if test='name != null and name.trim() != \"\"'>" +
            "AND name LIKE CONCAT('%', TRIM(#{name}), '%') </if>" +
            "</where></script>")
    int deleteProductsFiltered(@Param("domainsFilter") List<String> domainsFilter,
                                @Param("customCategoriesFilter") List<String> customCategoriesFilter,
                                @Param("productCategoriesFilter") List<String> productCategoriesFilter,
                                @Param("productRolesFilter") List<String> productRolesFilter,
                                @Param("name") String name,
                                @Param("ownerName") String ownerName);

    /** Returns normalized products for a small import-template export. */
    @Select("SELECT sku, name, description, regular_price, categories, images, cf_opingts, source_domain " +
            "FROM scraped_data.ecommerce_products " +
            "WHERE (#{domain} IS NULL OR #{domain} = '' OR source_domain = #{domain}) " +
            "AND (#{ownerName} IS NULL OR EXISTS (SELECT 1 FROM site_info s WHERE LOWER(TRIM(LEADING 'www.' FROM s.site_domain)) = LOWER(TRIM(LEADING 'www.' FROM ecommerce_products.source_domain)) AND FIND_IN_SET(s.admin_name, #{ownerName}) > 0)) ORDER BY id")
    List<Map<String, Object>> listProductsForExport(@Param("domain") String domain,
                                                    @Param("ownerName") String ownerName);

    /** Stream normalized products so large exports never materialize the full table. */
    @Select("<script>SELECT sku, name, description, regular_price, categories, images, cf_opingts, " +
            "custom_category, product_role, source_domain, language FROM scraped_data.ecommerce_products " +
            "<where>" +
            "<if test='ownerName != null'> AND EXISTS (SELECT 1 FROM site_info s WHERE LOWER(TRIM(LEADING 'www.' FROM s.site_domain)) = LOWER(TRIM(LEADING 'www.' FROM ecommerce_products.source_domain)) AND FIND_IN_SET(s.admin_name, #{ownerName}) &gt; 0)</if>" +
            "<if test='domainsFilter != null and domainsFilter.size() > 0'>" +
            "AND <foreach collection='domainsFilter' item='domain' separator=' OR ' open='(' close=')'>" +
            "source_domain LIKE CONCAT(TRIM(#{domain}), '%')" +
            "</foreach></if>" +
            "<if test='customCategoriesFilter != null and customCategoriesFilter.size() > 0'>" +
            "AND <foreach collection='customCategoriesFilter' item='category' separator=' OR ' open='(' close=')'>" +
            "custom_category LIKE CONCAT('%', TRIM(#{category}), '%')" +
            "</foreach></if>" +
            "<if test='productCategoriesFilter != null and productCategoriesFilter.size() > 0'>" +
            "AND <foreach collection='productCategoriesFilter' item='category' separator=' OR ' open='(' close=')'>" +
            "categories LIKE CONCAT('%', TRIM(#{category}), '%')" +
            "</foreach></if>" +
            "<if test='productRolesFilter != null and productRolesFilter.size() > 0'>" +
            "AND product_role IN <foreach collection='productRolesFilter' item='productRole' separator=',' open='(' close=')'>#{productRole}</foreach>" +
            "</if>" +
            "<if test='name != null and name.trim() != \"\"'>AND name LIKE CONCAT('%', TRIM(#{name}), '%') </if>" +
            "</where>ORDER BY id</script>")
    @Options(fetchSize = 500, resultSetType = ResultSetType.FORWARD_ONLY)
    Cursor<Map<String, Object>> streamProductsForExport(@Param("domainsFilter") List<String> domainsFilter,
                                                         @Param("customCategoriesFilter") List<String> customCategoriesFilter,
                                                         @Param("productCategoriesFilter") List<String> productCategoriesFilter,
                                                         @Param("productRolesFilter") List<String> productRolesFilter,
                                                         @Param("name") String name,
                                                         @Param("ownerName") String ownerName);

    /** Legacy small export method retained for callers outside the HTTP export path. */
    @Select("<script>SELECT sku, name, description, regular_price, categories, images, cf_opingts, " +
            "custom_category, source_domain, language FROM scraped_data.ecommerce_products " +
            "<where>" +
            "<if test='domain != null and domain.trim() != \"\"'>AND source_domain = TRIM(#{domain}) </if>" +
            "<if test='customCategory != null and customCategory.trim() != \"\"'>AND custom_category = TRIM(#{customCategory}) </if>" +
            "</where>ORDER BY id</script>")
    List<Map<String, Object>> listProductsForExcelExport(@Param("domain") String domain,
                                                         @Param("customCategory") String customCategory);
}
