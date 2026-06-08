package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SiteInfoMapper siteInfoMapper;
    private final OrderMapper orderMapper;
    private final SiteIndexingHistoryMapper indexingMapper;
    private final EcommerceProductMapper productMapper;

    public Map<String, Object> getOverview() {
        var overview = new LinkedHashMap<String, Object>();

        overview.put("total_sites", siteInfoMapper.countSites());
        overview.put("total_orders", orderMapper.countOrders());
        overview.put("total_products", productMapper.countProducts());

        var today = orderMapper.todaySummary();
        overview.put("today_orders", today.getOrDefault("COUNT(*)", 0L));
        overview.put("today_amount", today.getOrDefault("total_amount", 0.0));

        return overview;
    }

    public Map<String, Object> getSites(int page, int size, String adminName, String themeName) {
        int offset = (page - 1) * size;
        long total;
        List<Map<String, Object>> list;

        if (adminName != null && !adminName.isBlank()) {
            total = siteInfoMapper.countSitesByAdmin(adminName);
            list = siteInfoMapper.listSitesByAdmin(adminName, offset, size);
        } else if (themeName != null && !themeName.isBlank()) {
            total = siteInfoMapper.countSitesByTheme(themeName);
            list = siteInfoMapper.listSitesByTheme(themeName, offset, size);
        } else {
            total = siteInfoMapper.countSites();
            list = siteInfoMapper.listSites(offset, size);
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("total", total);
        result.put("list", list);
        return result;
    }

    public Map<String, Object> getOrders(int page, int size, String startDate, String endDate, String adminName) {
        int offset = (page - 1) * size;
        long total;
        List<Map<String, Object>> list;

        if (startDate != null && endDate != null) {
            total = orderMapper.countOrdersByDateRange(startDate, endDate);
            list = orderMapper.listOrdersByDateRange(startDate, endDate, offset, size);
        } else if (adminName != null && !adminName.isBlank()) {
            total = orderMapper.countOrdersByAdmin(adminName);
            list = orderMapper.listOrdersByAdmin(adminName, offset, size);
        } else {
            total = orderMapper.countOrders();
            list = orderMapper.listOrders(offset, size);
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("total", total);
        result.put("list", list);
        return result;
    }

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

    public Map<String, Object> getChartData() {
        var charts = new LinkedHashMap<String, Object>();

        // 订单趋势（最近 30 天）
        String endDate = LocalDate.now().toString();
        String startDate = LocalDate.now().minusDays(30).toString();
        charts.put("order_trend", orderMapper.orderTrend(startDate, endDate));

        // 收录趋势（最近 30 天）
        charts.put("index_trend", indexingMapper.indexTrend(startDate, endDate));

        // 按管理员分布
        charts.put("orders_by_admin", orderMapper.countByAdmin());
        charts.put("sites_by_admin", siteInfoMapper.countByAdmin());

        // 按分类分布
        charts.put("sites_by_category", siteInfoMapper.countByCategory());
        charts.put("products_by_category", productMapper.countByCategory());

        // 按域名分布
        charts.put("products_by_domain", productMapper.countByDomain());

        // 按币种
        charts.put("orders_by_currency", orderMapper.countByCurrency());

        // 订单摘要
        charts.put("order_summary", orderMapper.orderSummary());

        return charts;
    }
}
