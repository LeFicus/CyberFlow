package com.cyberflow.admin.dashboard.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('dashboard:overview')")
    public Result<Map<String, Object>> overview() {
        return Result.ok(dashboardService.getOverview());
    }

    @GetMapping("/sites")
    @PreAuthorize("hasAuthority('dashboard:site:view')")
    public Result<Map<String, Object>> sites(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(required = false) String adminName,
                                             @RequestParam(required = false) String themeName) {
        return Result.ok(dashboardService.getSites(page, size, adminName, themeName));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('dashboard:order:view')")
    public Result<Map<String, Object>> orders(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate,
                                              @RequestParam(required = false) String adminName) {
        return Result.ok(dashboardService.getOrders(page, size, startDate, endDate, adminName));
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('dashboard:product:view')")
    public Result<Map<String, Object>> products(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) String domain) {
        return Result.ok(dashboardService.getProducts(page, size, domain));
    }

    @GetMapping("/charts")
    @PreAuthorize("hasAuthority('dashboard:overview')")
    public Result<Map<String, Object>> charts() {
        return Result.ok(dashboardService.getChartData());
    }
}
