package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    /**
     * 获取系统总览数据。
     * <p>
     * 汇总站点总数、订单总数、商品总数，以及今日新增订单数和订单金额。
     * </p>
     *
     * @return 总览数据 Map，键包括 total_sites、total_orders、total_products、today_orders、today_amount
     */
    public Map<String, Object> getOverview() {
        var overview = new LinkedHashMap<String, Object>();

        overview.put("total_sites", siteInfoMapper.countSites());
        overview.put("total_products", productMapper.countProducts());

        var business = orderMapper.businessSummary();
        overview.put("deduplicated_orders", business.getOrDefault("deduplicated_orders", 0L));
        overview.put("successful_orders", business.getOrDefault("successful_orders", 0L));
        overview.put("successful_amount", business.getOrDefault("successful_amount", 0.0));
        overview.put("total_orders", business.getOrDefault("deduplicated_orders", 0L));

        var today = orderMapper.todaySummary();
        overview.put("today_orders", today.getOrDefault("successful_orders", 0L));
        overview.put("today_amount", today.getOrDefault("successful_amount", 0.0));

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
    public Map<String, Object> getSites(int page, int size, String adminName, String domain,
                                        String startDate, String endDate) {
        int offset = (page - 1) * size;
        long total = siteInfoMapper.countSitesFiltered(adminName, domain, startDate, endDate);
        List<Map<String, Object>> list = siteInfoMapper.listSitesFiltered(
                adminName, domain, startDate, endDate, offset, size);

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
                                          String endDate, String adminName, String domain,
                                          String payStatus, String currency, String country) {
        int offset = (page - 1) * size;
        long total = orderMapper.countOrdersFiltered(orderId, adminName, domain, payStatus,
                currency, country, startDate, endDate);
        List<Map<String, Object>> list = orderMapper.listOrdersFiltered(orderId, adminName,
                domain, payStatus, currency, country, startDate, endDate, offset, size);
        Map<String, Object> summary = orderMapper.summarizeOrdersFiltered(orderId, adminName,
                domain, payStatus, currency, country, startDate, endDate);

        var result = new LinkedHashMap<String, Object>();
        result.put("total", total);
        result.put("list", list);
        result.put("summary", summary);
        return result;
    }

    /**
     * 分页查询商品列表，支持按来源域名过滤。
     *
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @param domain 商品来源域名，为 null 或空时返回所有商品
     * @return 包含 total（总数）和 list（商品列表）的 Map
     */
    public Map<String, Object> getProducts(int page, int size, String domain) {
        int offset = (page - 1) * size;
        long total = domain != null && !domain.isBlank()
                ? productMapper.countProductsByDomain(domain)
                : productMapper.countProducts();
        List<Map<String, Object>> list = domain != null && !domain.isBlank()
                ? productMapper.listProductsByDomain(domain, offset, size)
                : productMapper.listProducts(offset, size);

        var result = new LinkedHashMap<String, Object>();
        result.put("total", total);
        result.put("list", list);
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
    public Map<String, Object> getChartData() {
        var charts = new LinkedHashMap<String, Object>();

        String endDate = LocalDate.now().toString();
        String startDate = LocalDate.now().minusDays(30).toString();
        charts.put("order_trend", orderMapper.orderTrend(startDate, endDate));

        charts.put("index_trend", indexingMapper.indexTrend(startDate, endDate));

        charts.put("orders_by_admin", orderMapper.countByAdmin());
        charts.put("sites_by_admin", siteInfoMapper.countByAdmin());

        charts.put("sites_by_category", siteInfoMapper.countByCategory());
        charts.put("products_by_category", productMapper.countByCategory());

        charts.put("products_by_domain", productMapper.countByDomain());

        charts.put("orders_by_currency", orderMapper.countByCurrency());

        charts.put("order_summary", orderMapper.orderSummary());

        return charts;
    }
}
