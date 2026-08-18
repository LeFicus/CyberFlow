package com.cyberflow.admin.dashboard.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.dashboard.service.DashboardService;
import com.cyberflow.admin.dashboard.service.ProductExportService;
import com.cyberflow.admin.dashboard.service.RevenueSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.Map;
import java.util.List;

/**
 * 仪表盘数据展示 REST 控制器。
 * <p>
 * 提供系统总览、站点列表、订单列表、商品列表及图表数据的 HTTP 查询接口，
 * 所有接口均需要相应的权限才能访问。
 * </p>
 *
 * <h3>权限列表</h3>
 * <ul>
 *   <li>dashboard:overview     - 查看系统总览和图表数据</li>
 *   <li>dashboard:site:view    - 查看站点列表</li>
 *   <li>dashboard:order:view   - 查看订单列表</li>
 *   <li>dashboard:product:view - 查看商品列表</li>
 * </ul>
 *
 * @author CyberFlow
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    /** 仪表盘业务服务 */
    private final DashboardService dashboardService;
    private final ProductExportService productExportService;
    private final RevenueSummaryService revenueSummaryService;

    /**
     * 获取系统总览数据。
     * <p>
     * 返回站点总数、订单总数、商品总数、今日订单数及今日交易金额等关键指标。
     * </p>
     *
     * @return 总览数据 Map，包含 total_sites、total_orders、total_products、today_orders、today_amount 等字段
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('dashboard:overview')")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) String userGroup) {
        return Result.ok(dashboardService.getOverview(userGroup));
    }

    /**
     * 分页查询站点列表，支持按管理员名称或模板名称过滤。
     *
     * @param page      页码，默认 1
     * @param size      每页大小，默认 10
     * @param adminName 管理员名称，可选，用于筛选指定管理员的站点
     * @param themeName 模板名称，可选，用于筛选指定模板的站点
     * @return 分页结果，包含 total（总数）和 list（站点列表）
     */
    @GetMapping("/sites")
    @PreAuthorize("hasAuthority('dashboard:site:view')")
    public Result<Map<String, Object>> sites(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(required = false) String adminName,
                                             @RequestParam(required = false) String userGroup,
                                             @RequestParam(required = false) String domain,
                                             @RequestParam(required = false) String startDate,
                                             @RequestParam(required = false) String endDate) {
        return Result.ok(dashboardService.getSites(page, size, adminName, userGroup, domain, startDate, endDate));
    }

    /**
     * 分页查询订单列表，支持按日期范围或管理员名称过滤。
     *
     * @param page      页码，默认 1
     * @param size      每页大小，默认 10
     * @param startDate 查询起始日期，可选
     * @param endDate   查询结束日期，可选
     * @param adminName 管理员名称，可选
     * @return 分页结果，包含 total（总数）和 list（订单列表）
     */
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('dashboard:order:view')")
    public Result<Map<String, Object>> orders(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) String orderId,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate,
                                              @RequestParam(required = false) String adminName,
                                              @RequestParam(required = false) String userGroup,
                                              @RequestParam(required = false) String domain,
                                              @RequestParam(required = false) String payStatus,
                                              @RequestParam(required = false) String currency,
                                              @RequestParam(required = false) String country) {
        return Result.ok(dashboardService.getOrders(page, size, orderId, startDate, endDate,
                adminName, userGroup, domain, payStatus, currency, country));
    }

    /** Delete all orders across both user groups. Restricted to administrators. */
    @DeleteMapping("/orders/clear")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Result<Map<String, Object>> clearAllOrders() {
        return Result.ok(dashboardService.clearAllOrders());
    }

    /**
     * 分页查询商品列表，支持按域名、分类和商品名称组合过滤。
     *
     * @param page   页码，默认 1
     * @param size   每页大小，默认 10
     * @param domain 商品来源域名，可选，用于筛选指定域名的商品
     * @param category 商品分类，可选
     * @param name 商品名称，可选
     * @return 分页结果，包含 total（总数）和 list（商品列表）
     */
    @GetMapping("/products")
    @PreAuthorize("hasAuthority('dashboard:product:view')")
    public Result<Map<String, Object>> products(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) List<String> domain,
                                                @RequestParam(required = false) List<String> category,
                                                @RequestParam(required = false) List<String> productCategory,
                                                @RequestParam(required = false) List<String> productRole,
                                                @RequestParam(required = false) String name) {
        return Result.ok(dashboardService.getProducts(page, size, domain, category, productCategory, productRole, name));
    }

    /** Delete only the products explicitly selected in the product table. */
    @DeleteMapping("/products")
    @PreAuthorize("hasAuthority('dashboard:product:delete')")
    public Result<Map<String, Object>> deleteProducts(@RequestBody java.util.List<Long> ids) {
        return Result.ok(dashboardService.deleteProducts(ids));
    }

    /** Delete all products matching the current domain/category/name filters. */
    @DeleteMapping("/products/clear")
    @PreAuthorize("hasAuthority('dashboard:product:delete')")
    public Result<Map<String, Object>> clearProducts(@RequestParam(required = false) List<String> domain,
                                                      @RequestParam(required = false) List<String> category,
                                                      @RequestParam(required = false) List<String> productCategory,
                                                      @RequestParam(required = false) List<String> productRole,
                                                      @RequestParam(required = false) String name) {
        return Result.ok(dashboardService.clearProducts(domain, category, productCategory, productRole, name));
    }

    /** Export normalized products to the CSV layout required by the selected engine. */
    @GetMapping(value = "/products/export", produces = "text/csv")
    @PreAuthorize("hasAuthority('dashboard:product:view')")
    public void exportProducts(@RequestParam String engine,
                               @RequestParam(required = false) List<String> domain,
                               @RequestParam(required = false) List<String> category,
                               @RequestParam(required = false) List<String> productCategory,
                               @RequestParam(required = false) List<String> productRole,
                               @RequestParam(required = false) String name,
                               HttpServletResponse response) throws IOException {
        String normalizedEngine = engine.toLowerCase();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=products-" + normalizedEngine + ".csv");
        var writer = response.getWriter();
        writer.write("\uFEFF"); // Excel UTF-8 BOM
        productExportService.writeCsv(normalizedEngine, domain, category, productCategory, productRole, name, writer);
    }

    /** Export the normalized crawler fields as XLSX, filtered by domain/custom category. */
    @GetMapping(value = "/products/export/excel",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("hasAuthority('dashboard:product:view')")
    public void exportProductsExcel(@RequestParam(required = false) List<String> domain,
                                    @RequestParam(required = false) List<String> category,
                                    @RequestParam(required = false) List<String> productCategory,
                                    @RequestParam(required = false) List<String> productRole,
                                    @RequestParam(required = false) String name,
                                    @RequestParam(required = false) String customCategory,
                                    HttpServletResponse response) throws IOException {
        String suffix = domain == null || domain.isEmpty() ? "all"
                : domain.size() == 1 ? domain.get(0).trim() : "multiple";
        String fileName = "products-" + suffix.replaceAll("[^a-zA-Z0-9._-]", "-") + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" +
                URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        List<String> effectiveCategories = category == null || category.isEmpty()
                ? (customCategory == null || customCategory.isBlank() ? List.of() : List.of(customCategory))
                : category;
        productExportService.writeExcel(domain, effectiveCategories, productCategory, productRole, name, response.getOutputStream());
    }

    private void writeCsv(java.io.Writer writer, java.util.List<String> row) throws IOException {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) writer.write(',');
            writer.write('"');
            writer.write(row.get(i).replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\\r\\n");
    }

    @GetMapping("/site-index-history")
    @PreAuthorize("hasAuthority('dashboard:site:view')")
    public Result<Object> siteIndexHistory(@RequestParam String domain) {
        return Result.ok(dashboardService.getSiteIndexHistory(domain));
    }

    @GetMapping("/orders-by-domain")
    @PreAuthorize("hasAuthority('dashboard:order:view')")
    public Result<Map<String, Object>> ordersByDomain(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size,
                                                      @RequestParam String domain,
                                                      @RequestParam(required = false) String startDate,
                                                      @RequestParam(required = false) String endDate) {
        return Result.ok(dashboardService.getOrdersByDomain(page, size, domain, startDate, endDate));
    }

    /**
     * 获取图表统计数据。
     * <p>
     * 返回订单趋势、收录趋势、按管理员分布、按分类分布、按域名分布、
     * 按币种分布及订单摘要等多项图表所需数据。
     * </p>
     *
     * @return 图表数据 Map，包含 order_trend、index_trend、orders_by_admin、sites_by_admin 等字段
     */
    @GetMapping("/charts")
    @PreAuthorize("hasAuthority('dashboard:overview')")
    public Result<Map<String, Object>> charts(@RequestParam(required = false) String userGroup) {
        return Result.ok(dashboardService.getChartData(userGroup));
    }

    /** Revenue conversion, personal commission and leader commission summary. */
    @GetMapping("/revenue-summary")
    @PreAuthorize("hasAuthority('dashboard:overview')")
    public Result<Map<String, Object>> revenueSummary(@RequestParam(required = false) String userGroup,
                                                       @RequestParam(required = false) String startDate,
                                                       @RequestParam(required = false) String endDate,
                                                       @RequestParam(required = false) String siteCreatedMonth) {
        return Result.ok(revenueSummaryService.summarize(userGroup, startDate, endDate, siteCreatedMonth));
    }
}
