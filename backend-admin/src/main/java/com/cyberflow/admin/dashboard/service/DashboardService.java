package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * 仪表盘业务服务。
 * <p>
 * 负责汇聚系统各维度统计数据，为前端仪表盘页面提供总览、列表查询、
 * 图表趋势分析等功能。通过多个 Mapper 接口从数据库聚合站点信息、
 * 订单记录、商品数据及收录历史等数据。
 * </p>
 *
 * @author CyberFlow
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 站点信息数据访问接口 */
    private final SiteInfoMapper siteInfoMapper;

    /** 订单数据访问接口 */
    private final OrderMapper orderMapper;

    /** 收录历史数据访问接口 */
    private final SiteIndexingHistoryMapper indexingMapper;

    /** 电商商品数据访问接口 */
    private final EcommerceProductMapper productMapper;

    /** 商品爬虫去重指纹缓存 */
    private final StringRedisTemplate redisTemplate;

    /**
     * 获取系统总览数据。
     * <p>
     * 汇总站点总数、订单总数、商品总数，以及今日新增订单数和订单金额。
     * </p>
     *
     * @return 总览数据 Map，键包括 total_sites、total_orders、total_products、today_orders、today_amount
     */
    public Map<String, Object> getOverview(String rawUserGroup) {
        String userGroup = normalizeUserGroup(rawUserGroup);
        var overview = new LinkedHashMap<String, Object>();

        overview.put("user_group", userGroup == null ? "ALL" : userGroup);
        overview.put("total_sites", siteInfoMapper.countSitesByGroup(userGroup));
        overview.put("total_products", productMapper.countProducts());

        var business = orderMapper.businessSummaryByGroup(userGroup);
        overview.put("deduplicated_orders", business.getOrDefault("deduplicated_orders", 0L));
        overview.put("successful_orders", business.getOrDefault("successful_orders", 0L));
        overview.put("successful_amount", business.getOrDefault("successful_amount", 0.0));
        overview.put("total_orders", business.getOrDefault("deduplicated_orders", 0L));

        var today = orderMapper.todaySummaryByGroup(userGroup);
        overview.put("today_orders", today.getOrDefault("successful_orders", 0L));
        overview.put("today_amount", today.getOrDefault("successful_amount", 0.0));
        overview.put("site_group_summary", siteInfoMapper.summarizeByGroup());
        overview.put("order_group_summary", orderMapper.summarizeByGroup());

        return overview;
    }

    /**
     * 分页查询站点列表，支持按管理员名称或模板名称过滤。
     *
     * @param page      页码（从 1 开始）
     * @param size      每页条数
     * @param adminName 管理员名称，为 null 或空时不按管理员过滤
     * @param themeName 模板名称，为 null 或空时不按模板过滤；adminName 优先于 themeName
     * @return 包含 total（总数）和 list（站点列表）的 Map
     */
    public Map<String, Object> getSites(int page, int size, String adminName, String rawUserGroup, String domain,
                                        String startDate, String endDate) {
        String userGroup = normalizeUserGroup(rawUserGroup);
        int offset = (page - 1) * size;
        long total = siteInfoMapper.countSitesFiltered(adminName, userGroup, domain, startDate, endDate);
        List<Map<String, Object>> list = siteInfoMapper.listSitesFiltered(
                adminName, userGroup, domain, startDate, endDate, offset, size);

        var result = new LinkedHashMap<String, Object>();
        result.put("total", total);
        result.put("list", list);
        return result;
    }

    /**
     * 分页查询订单列表，支持按日期范围或管理员名称过滤。
     *
     * @param page      页码（从 1 开始）
     * @param size      每页条数
     * @param startDate 开始日期（年月日格式），与 endDate 配合时优先使用
     * @param endDate   结束日期（年月日格式）
     * @param adminName 管理员名称，日期范围为 null 时生效
     * @return 包含 total（总数）和 list（订单列表）的 Map
     */
    public Map<String, Object> getOrders(int page, int size, String orderId, String startDate,
                                          String endDate, String adminName, String rawUserGroup, String domain,
                                          String payStatus, String currency, String country) {
        String userGroup = normalizeUserGroup(rawUserGroup);
        int offset = (page - 1) * size;
        long total = orderMapper.countOrdersFiltered(orderId, adminName, userGroup, domain, payStatus,
                currency, country, startDate, endDate);
        List<Map<String, Object>> list = orderMapper.listOrdersFiltered(orderId, adminName, userGroup,
                domain, payStatus, currency, country, startDate, endDate, offset, size);
        Map<String, Object> summary = orderMapper.summarizeOrdersFiltered(orderId, adminName, userGroup,
                domain, payStatus, currency, country, startDate, endDate);

        var result = new LinkedHashMap<String, Object>();
        result.put("total", total);
        result.put("list", list);
        result.put("summary", summary);
        return result;
    }

    /**
     * 分页查询商品列表，支持按来源域名、分类和商品名称组合过滤。
     *
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @param domain 商品来源域名，为 null 或空时返回所有商品
     * @param category 商品分类，为 null 或空时不按分类过滤
     * @param name 商品名称，为 null 或空时不按名称过滤
     * @return 包含 total（总数）和 list（商品列表）的 Map
     */
    public Map<String, Object> getProducts(int page, int size, String domain, String category, String name) {
        int offset = (page - 1) * size;
        long total = productMapper.countProductsFiltered(domain, category, name);
        List<Map<String, Object>> list = productMapper.listProductsFiltered(
                domain, category, name, offset, size);

        var result = new LinkedHashMap<String, Object>();
        result.put("total", total);
        result.put("list", list);
        return result;
    }

    /** Delete selected products and remove only their SKU crawl fingerprints. */
    @Transactional
    public Map<String, Object> deleteProducts(List<Long> rawIds) {
        List<Long> ids = rawIds == null ? List.of() : rawIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Please select products to delete");
        }
        if (ids.size() > 500) {
            throw new IllegalArgumentException("No more than 500 products can be deleted at once");
        }

        List<Map<String, Object>> products = productMapper.listProductFingerprintsByIds(ids);
        int deletedCount = productMapper.deleteProductsByIds(ids);
        for (Map<String, Object> product : products) {
            String sku = Objects.toString(product.get("sku"), "").trim();
            String domain = Objects.toString(product.get("source_domain"), "").trim();
            if (sku.isEmpty() || domain.isEmpty()) continue;
            redisTemplate.opsForSet().remove("scraped_skus:shopify_crawl_fast:" + domain, sku);
            redisTemplate.opsForSet().remove("scraped_skus:platform_crawl:" + domain, sku);
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("deleted_count", deletedCount);
        return result;
    }

    public List<Map<String, Object>> getSiteIndexHistory(String domain) {
        if (domain == null || domain.isBlank()) {
            return List.of();
        }
        return indexingMapper.listHistoryByDomain(domain);
    }

    public Map<String, Object> getOrdersByDomain(int page, int size, String domain, String startDate, String endDate) {
        int offset = (page - 1) * size;
        long total;
        List<Map<String, Object>> list;

        if (domain == null || domain.isBlank()) {
            total = 0;
            list = List.of();
        } else if (startDate != null && endDate != null) {
            total = orderMapper.countOrdersByDomainAndDateRange(domain, startDate, endDate);
            list = orderMapper.listOrdersByDomainAndDateRange(domain, startDate, endDate, offset, size);
        } else {
            total = orderMapper.countOrdersByDomain(domain);
            list = orderMapper.listOrdersByDomain(domain, offset, size);
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("total", total);
        result.put("list", list);
        return result;
    }

    /**
     * 获取图表所需的全部统计数据。
     * <p>
     * 聚合最近 30 天的订单趋势和收录趋势，以及按管理员、分类、域名、
     * 币种等多个维度的分布数据，供前端图表组件渲染使用。
     * </p>
     *
     * @return 图表数据 Map，包含以下键：
     *         <ul>
     *           <li>order_trend - 近 30 天每日订单趋势</li>
     *           <li>index_trend - 近 30 天每日收录趋势</li>
     *           <li>orders_by_admin / sites_by_admin - 按管理员分布</li>
     *           <li>sites_by_category / products_by_category - 按分类分布</li>
     *           <li>products_by_domain - 按域名分布</li>
     *           <li>orders_by_currency - 按币种分布</li>
     *           <li>order_summary - 订单汇总信息</li>
     *         </ul>
     */
    public Map<String, Object> getChartData(String rawUserGroup) {
        String userGroup = normalizeUserGroup(rawUserGroup);
        var charts = new LinkedHashMap<String, Object>();

        String endDate = LocalDate.now().toString();
        String startDate = LocalDate.now().minusDays(30).toString();
        charts.put("user_group", userGroup == null ? "ALL" : userGroup);
        charts.put("order_trend", orderMapper.orderTrendByGroup(startDate, endDate, userGroup));

        charts.put("index_trend", indexingMapper.indexTrendByGroup(startDate, endDate, userGroup));

        charts.put("orders_by_admin", orderMapper.countByAdminForGroup(userGroup));
        charts.put("sites_by_admin", siteInfoMapper.countByAdminForGroup(userGroup));
        charts.put("site_group_summary", siteInfoMapper.summarizeByGroup());
        charts.put("order_group_summary", orderMapper.summarizeByGroup());

        charts.put("sites_by_category", siteInfoMapper.countByCategoryForGroup(userGroup));
        charts.put("products_by_category", productMapper.countByCategory());

        charts.put("products_by_domain", productMapper.countByDomain());

        charts.put("orders_by_currency", orderMapper.countByCurrencyForGroup(userGroup));

        charts.put("order_summary", orderMapper.orderSummaryByGroup(userGroup));

        return charts;
    }

    private static String normalizeUserGroup(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("A", "B").contains(normalized)) {
            throw new IllegalArgumentException("userGroup must be A, B or empty");
        }
        return normalized;
    }
}
