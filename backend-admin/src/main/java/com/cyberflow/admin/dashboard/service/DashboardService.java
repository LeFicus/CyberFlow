package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.*;
import com.cyberflow.admin.common.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cursor.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
@Slf4j
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DB_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
    private final DataScopeService dataScopeService;

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
        String ownerName = ownerName();
        var overview = new LinkedHashMap<String, Object>();

        overview.put("user_group", userGroup == null ? "ALL" : userGroup);
        overview.put("total_products", productMapper.countProductsByGroup(userGroup, ownerName));

        LocalDate businessToday = LocalDate.now(BUSINESS_ZONE);
        LocalDate monthStartDate = businessToday.withDayOfMonth(1);
        LocalDate previousMonthStartDate = monthStartDate.minusMonths(1);
        LocalDate previousMonthEndDate = previousMonthStartDate.plusDays(
                Math.min(businessToday.getDayOfMonth(), previousMonthStartDate.lengthOfMonth()));
        String monthStart = dbDateTime(monthStartDate);
        String todayStart = dbDateTime(businessToday);
        String tomorrowStart = dbDateTime(businessToday.plusDays(1));
        String previousMonthStart = dbDateTime(previousMonthStartDate);
        String previousMonthEnd = dbDateTime(previousMonthEndDate);
        var today = orderMapper.businessSummaryByGroup(todayStart, tomorrowStart, userGroup, ownerName);
        var month = orderMapper.businessSummaryByGroup(monthStart, tomorrowStart, userGroup, ownerName);
        var previousMonthSamePeriod = orderMapper.businessSummaryByGroup(
                previousMonthStart, previousMonthEnd, userGroup, ownerName);
        long todaySites = siteInfoMapper.countSitesByGroupAndDateRange(userGroup, ownerName, todayStart, tomorrowStart);
        long monthSites = siteInfoMapper.countSitesByGroupAndDateRange(userGroup, ownerName, monthStart, tomorrowStart);
        overview.put("total_sites", todaySites);
        overview.put("today_sites", todaySites);
        overview.put("month_sites", monthSites);
        overview.put("deduplicated_orders", today.getOrDefault("deduplicated_orders", 0L));
        overview.put("successful_orders", today.getOrDefault("successful_orders", 0L));
        overview.put("successful_amount", today.getOrDefault("successful_amount", 0.0));
        overview.put("total_orders", today.getOrDefault("deduplicated_orders", 0L));
        overview.put("period", "TODAY");
        overview.put("today_orders", today.getOrDefault("successful_orders", 0L));
        overview.put("today_deduplicated_orders", today.getOrDefault("deduplicated_orders", 0L));
        overview.put("today_successful_orders", today.getOrDefault("successful_orders", 0L));
        overview.put("today_successful_amount", today.getOrDefault("successful_amount", 0.0));
        overview.put("today_amount", today.getOrDefault("successful_amount", 0.0));
        overview.put("month_orders", month.getOrDefault("successful_orders", 0L));
        overview.put("month_amount", month.getOrDefault("successful_amount", 0.0));
        overview.put("month_deduplicated_orders", month.getOrDefault("deduplicated_orders", 0L));
        overview.put("previous_month_same_period_deduplicated_orders",
                previousMonthSamePeriod.getOrDefault("deduplicated_orders", 0L));
        overview.put("month_same_period_start", monthStartDate.toString());
        overview.put("month_same_period_end", businessToday.toString());
        overview.put("previous_month_same_period_start", previousMonthStartDate.toString());
        overview.put("previous_month_same_period_end", previousMonthEndDate.minusDays(1).toString());
        overview.put("month_successful_orders", month.getOrDefault("successful_orders", 0L));
        overview.put("month_successful_amount", month.getOrDefault("successful_amount", 0.0));
        overview.put("site_group_summary", siteInfoMapper.summarizeByGroup(ownerName));
        overview.put("order_group_summary", orderMapper.summarizeByGroup(ownerName));

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
                                        String serverName, String themeName, String productCategory,
                                        String startDate, String endDate) {
        String userGroup = normalizeUserGroup(rawUserGroup);
        String ownerName = ownerName();
        int offset = (page - 1) * size;
        long total = siteInfoMapper.countSitesFiltered(adminName, userGroup, ownerName, domain,
                serverName, themeName, productCategory, startDate, endDate);
        List<Map<String, Object>> list = siteInfoMapper.listSitesFiltered(
                adminName, userGroup, ownerName, domain, serverName, themeName, productCategory,
                startDate, endDate, offset, size);

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
        String ownerName = ownerName();
        int offset = (page - 1) * size;
        long total = orderMapper.countOrdersFiltered(orderId, adminName, userGroup, domain, payStatus,
                currency, country, ownerName, startDate, endDate);
        List<Map<String, Object>> list = orderMapper.listOrdersFiltered(orderId, adminName, userGroup,
                domain, payStatus, currency, country, ownerName, startDate, endDate, offset, size);
        Map<String, Object> summary = orderMapper.summarizeOrdersFiltered(orderId, adminName, userGroup,
                domain, payStatus, currency, country, ownerName, startDate, endDate);

        var result = new LinkedHashMap<String, Object>();
        result.put("total", total);
        result.put("list", list);
        result.put("summary", summary);
        return result;
    }

    /** Delete all orders. Authorization is enforced by the controller and the admin role. */
    @Transactional
    public Map<String, Object> clearAllOrders() {
        int deletedCount = orderMapper.deleteAllOrders();
        orderMapper.resetOrderCursors();
        var result = new LinkedHashMap<String, Object>();
        result.put("deleted_count", deletedCount);
        return result;
    }

    /**
     * 分页查询商品列表，支持按来源域名、分类和商品名称组合过滤。
     *
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @param domains 商品来源域名，可传多个；为空时返回所有商品
     * @param category 商品分类，为 null 或空时不按分类过滤
     * @param name 商品名称，为 null 或空时不按名称过滤
     * @return 包含 total（总数）和 list（商品列表）的 Map
     */
    public Map<String, Object> getProducts(int page, int size, List<String> domains,
                                           List<String> customCategories, List<String> productCategories,
                                           List<String> productRoles, String name) {
        String ownerName = ownerName();
        List<String> domainFilter = normalizeDomainFilter(domains);
        List<String> customCategoryFilter = normalizeCategoryFilter(customCategories);
        List<String> productCategoryFilter = normalizeCategoryFilter(productCategories);
        List<String> productRoleFilter = normalizeProductRoleFilter(productRoles);
        int offset = (page - 1) * size;
        long total = productMapper.countProductsFiltered(
                domainFilter, customCategoryFilter, productCategoryFilter, productRoleFilter, name, ownerName);
        List<Map<String, Object>> list = productMapper.listProductsFiltered(
                domainFilter, customCategoryFilter, productCategoryFilter, productRoleFilter, name, ownerName, offset, size);

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

        String ownerName = ownerName();
        List<Map<String, Object>> products = productMapper.listProductFingerprintsByIds(ids, ownerName);
        int deletedCount = productMapper.deleteProductsByIds(ids, ownerName);
        for (Map<String, Object> product : products) {
            if (!removeProductFingerprint(product)) break;
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("deleted_count", deletedCount);
        return result;
    }

    /** Delete every product matching the current product-list filters. */
    @Transactional
    public Map<String, Object> clearProducts(List<String> domains, List<String> customCategories,
                                             List<String> productCategories, List<String> productRoles, String name) {
        List<String> domainFilter = normalizeDomainFilter(domains);
        List<String> customCategoryFilter = normalizeCategoryFilter(customCategories);
        List<String> productCategoryFilter = normalizeCategoryFilter(productCategories);
        List<String> productRoleFilter = normalizeProductRoleFilter(productRoles);
        String ownerName = ownerName();
        // Consume the fingerprint cursor before issuing DELETE so a million-row
        // clear never creates a million-element Java List.
        try (Cursor<Map<String, Object>> products = productMapper.streamProductFingerprintsFiltered(
                domainFilter, customCategoryFilter, productCategoryFilter, productRoleFilter, name, ownerName)) {
            for (Map<String, Object> product : products) {
                if (!removeProductFingerprint(product)) break;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to finish streaming product fingerprints", ex);
        }
        int deletedCount = productMapper.deleteProductsFiltered(
                domainFilter, customCategoryFilter, productCategoryFilter, productRoleFilter, name, ownerName);

        var result = new LinkedHashMap<String, Object>();
        result.put("deleted_count", deletedCount);
        return result;
    }

    private boolean removeProductFingerprint(Map<String, Object> product) {
        String sku = Objects.toString(product.get("sku"), "").trim();
        String domain = Objects.toString(product.get("source_domain"), "").trim();
        if (sku.isEmpty() || domain.isEmpty()) return true;
        try {
            for (String spider : List.of("shopify_crawl_fast", "platform_crawl", "bigcommerce_crawl",
                    "magento_crawl", "wix_crawl", "ecwid_crawl", "shopline_crawl")) {
                redisTemplate.opsForSet().remove("scraped_skus:" + spider + ":" + domain, sku);
            }
            return true;
        } catch (RuntimeException ex) {
            log.warn("Redis fingerprint cleanup unavailable; MySQL operation continues ({})", ex.getClass().getSimpleName());
            return false;
        }
    }

    private List<String> normalizeCategoryFilter(List<String> categories) {
        if (categories == null) return List.of();
        return categories.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private List<String> normalizeProductRoleFilter(List<String> roles) {
        if (roles == null) return List.of();
        return roles.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> value.equals("main") || value.equals("supplement"))
                .distinct()
                .toList();
    }

    private List<String> normalizeDomainFilter(List<String> domains) {
        if (domains == null) return List.of();
        return domains.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    public List<Map<String, Object>> getSiteIndexHistory(String domain) {
        if (domain == null || domain.isBlank()) {
            return List.of();
        }
        return indexingMapper.listHistoryByDomain(domain, ownerName());
    }

    /** Latest indexing snapshot report, grouped by site, builder, or server. */
    public Map<String, Object> getSiteIndexData(int page, int size, String dimension,
                                                 String rawUserGroup, String adminName,
                                                 String builderUsername, String serverName,
                                                 String serverIp, String domain,
                                                 String themeName, String productCategory,
                                                 String siteStartDate, String siteEndDate,
                                                 String submittedStartDate, String submittedEndDate,
                                                 String updatedStartDate, String updatedEndDate,
                                                 Integer minIndexCount, Integer maxIndexCount,
                                                 String changeDirection, String serverNameExact, boolean serverIpEmpty, String builderNameExact) {
        String normalizedDimension = dimension == null ? "site" : dimension.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("site", "builder", "server").contains(normalizedDimension)) {
            throw new IllegalArgumentException("dimension must be site, builder or server");
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("userGroup", normalizeUserGroup(rawUserGroup));
        filters.put("ownerName", ownerName());
        filters.put("adminName", adminName);
        filters.put("builderUsername", builderUsername);
        filters.put("serverName", serverName);
        filters.put("serverIp", serverIp);
        filters.put("serverNameExact", serverNameExact);
        filters.put("serverIpEmpty", serverIpEmpty);
        filters.put("builderNameExact", builderNameExact);
        filters.put("domain", domain);
        filters.put("themeName", themeName);
        filters.put("productCategory", productCategory);
        filters.put("siteStartDate", siteStartDate);
        filters.put("siteEndDate", siteEndDate);
        filters.put("submittedStartDate", submittedStartDate);
        filters.put("submittedEndDate", submittedEndDate);
        filters.put("updatedStartDate", updatedStartDate);
        filters.put("updatedEndDate", updatedEndDate);
        filters.put("minIndexCount", minIndexCount);
        filters.put("maxIndexCount", maxIndexCount);
        String normalizedChange = changeDirection == null ? null : changeDirection.trim().toLowerCase(Locale.ROOT);
        filters.put("changeDirection", normalizedChange != null
                && Set.of("up", "down", "flat").contains(normalizedChange) ? normalizedChange : null);

        long total;
        List<Map<String, Object>> list;
        if ("site".equals(normalizedDimension)) {
            total = indexingMapper.countLatestSites(filters);
            list = indexingMapper.listLatestSites(filters, (safePage - 1) * safeSize, safeSize);
        } else {
            List<Map<String, Object>> grouped = "builder".equals(normalizedDimension)
                    ? indexingMapper.summarizeByBuilder(filters)
                    : indexingMapper.summarizeByServer(filters);
            total = grouped.size();
            int from = Math.min((safePage - 1) * safeSize, grouped.size());
            int to = Math.min(from + safeSize, grouped.size());
            list = grouped.subList(from, to);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", normalizedDimension);
        result.put("total", total);
        result.put("list", list);
        result.put("summary", indexingMapper.summarizeLatestSites(filters));
        return result;
    }

    public Map<String, Object> getOrdersByDomain(int page, int size, String domain, String startDate, String endDate) {
        String ownerName = ownerName();
        int offset = (page - 1) * size;
        long total;
        List<Map<String, Object>> list;

        if (domain == null || domain.isBlank()) {
            total = 0;
            list = List.of();
        } else if (startDate != null && endDate != null) {
            total = orderMapper.countOrdersByDomainAndDateRange(domain, ownerName, startDate, endDate);
            list = orderMapper.listOrdersByDomainAndDateRange(domain, ownerName, startDate, endDate, offset, size);
        } else {
            total = orderMapper.countOrdersByDomain(domain, ownerName);
            list = orderMapper.listOrdersByDomain(domain, ownerName, offset, size);
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
        String ownerName = ownerName();
        var charts = new LinkedHashMap<String, Object>();

        LocalDate businessToday = LocalDate.now(BUSINESS_ZONE);
        String orderEnd = dbDateTime(businessToday.plusDays(1));
        String orderStart = dbDateTime(businessToday.minusDays(30));
        String indexEnd = businessToday.toString();
        String indexStart = businessToday.minusDays(30).toString();
        charts.put("user_group", userGroup == null ? "ALL" : userGroup);
        charts.put("order_trend", orderMapper.orderTrendByGroup(orderStart, orderEnd, userGroup, ownerName));

        charts.put("index_trend", indexingMapper.indexTrendByGroup(indexStart, indexEnd, userGroup, ownerName));

        charts.put("orders_by_admin", orderMapper.countByAdminForGroup(userGroup, ownerName));
        charts.put("sites_by_admin", siteInfoMapper.countByAdminForGroup(userGroup, ownerName));
        charts.put("site_group_summary", siteInfoMapper.summarizeByGroup(ownerName));
        charts.put("order_group_summary", orderMapper.summarizeByGroup(ownerName));

        charts.put("sites_by_category", siteInfoMapper.countByCategoryForGroup(userGroup, ownerName));
        charts.put("products_by_category", productMapper.countByCategory(ownerName));

        charts.put("products_by_domain", productMapper.countByDomain(ownerName));

        charts.put("orders_by_currency", orderMapper.countByCurrencyForGroup(userGroup, ownerName));

        charts.put("order_summary", orderMapper.orderSummaryByGroup(userGroup, ownerName));

        return charts;
    }

    /** Upstream APIs already store Asia/Shanghai wall-clock timestamps. */
    private static String dbDateTime(LocalDate date) {
        return date.atStartOfDay().format(DB_DATETIME);
    }

    private static String normalizeUserGroup(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("A", "B").contains(normalized)) {
            throw new IllegalArgumentException("userGroup must be A, B or empty");
        }
        return normalized;
    }

    private String ownerName() {
        var scope = dataScopeService.current();
        return scope.administrator() ? null : scope.ownerName();
    }
}
