package com.cyberflow.admin.dashboard.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.dashboard.model.ProductFilter;
import com.cyberflow.admin.dashboard.service.ProductExportJobService;
import com.cyberflow.admin.dashboard.service.ProductQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@RestController
@Slf4j
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class ProductWorkspaceController {
    private final ProductQueryService queries;
    private final ProductExportJobService exports;
    public record SearchRequest(ProductFilter filters, Long beforeId, Long snapshotId, Integer size) {}
    public record ExportRequest(ProductFilter filters, String format, Integer maxRows, Integer partRows, Long snapshotId) {}
    private ProductFilter filter(ProductFilter f) { return f == null ? new ProductFilter() : f; }

    @PostMapping("/products/search")
    @PreAuthorize("hasAuthority('dashboard:product:view')")
    public Result<?> search(@RequestBody SearchRequest request) {
        return Result.ok(queries.search(filter(request.filters), request.beforeId, request.snapshotId, request.size == null ? 50 : request.size));
    }
    @PostMapping("/products/count")
    @PreAuthorize("hasAuthority('dashboard:product:view')")
    public Result<?> count(@RequestBody SearchRequest request) {
        try { return Result.ok(Map.of("total", queries.count(filter(request.filters), request.snapshotId))); }
        catch (org.springframework.dao.QueryTimeoutException ex) { return Result.fail(400, "统计耗时过长，请缩小筛选范围；不影响翻页和导出"); }
    }
    @GetMapping("/products/domain-options")
    @PreAuthorize("hasAuthority('dashboard:product:view')")
    public Result<?> domains(@RequestParam(required = false) String keyword) { return Result.ok(queries.domainOptions(keyword)); }

    @PostMapping("/product-exports")
    @PreAuthorize("hasAuthority('dashboard:product:view')")
    public Result<?> create(@RequestBody ExportRequest request) throws IOException {
        return Result.ok(exports.create(filter(request.filters), request.format == null ? "csv" : request.format,
                request.maxRows == null ? 100_000 : request.maxRows, request.partRows == null ? 50_000 : request.partRows, request.snapshotId));
    }
    @GetMapping("/product-exports")
    @PreAuthorize("hasAuthority('dashboard:product:view')")
    public Result<?> list() { return Result.ok(exports.list()); }
    @GetMapping("/product-exports/{id}")
    @PreAuthorize("hasAuthority('dashboard:product:view')")
    public Result<?> get(@PathVariable String id) { return Result.ok(exports.get(id)); }
    @PostMapping("/product-exports/{id}/cancel")
    @PreAuthorize("hasAuthority('dashboard:product:view')")
    public Result<?> cancel(@PathVariable String id) throws IOException { exports.cancel(id); return Result.ok(); }
    @PostMapping("/product-exports/{id}/ticket")
    @PreAuthorize("hasAuthority('dashboard:product:view')")
    public Result<?> ticket(@PathVariable String id) { return Result.ok(exports.ticket(id)); }

    /** Single-use, short-lived capability; validates the owner's current account/scope before streaming. */
    @GetMapping("/product-exports/download")
    public void download(@RequestParam String ticket, HttpServletResponse response) throws IOException {
        var file = exports.consumeTicket(ticket);
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=products-" + file.getFileName());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setContentLengthLong(Files.size(file));
        try (var input = Files.newInputStream(file)) {
            long transferred = input.transferTo(response.getOutputStream());
            log.info("Product export download completed: file={}, bytes={}", file.getFileName(), transferred);
        }
    }
}
