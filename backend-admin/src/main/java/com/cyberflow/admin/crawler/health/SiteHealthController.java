package com.cyberflow.admin.crawler.health;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Operator endpoints for the latest site-health snapshot. */
@RestController
@RequestMapping("/admin/crawler/site-health")
@RequiredArgsConstructor
public class SiteHealthController {
    private final SiteHealthCheckService service;
    private final CrawlerConfigService configService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('crawler:health:view', 'crawler:schedule:view')")
    public Result<?> list(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String reason,
                          @RequestParam(required = false) String userGroup,
                          @RequestParam(required = false) String adminName,
                          @RequestParam(required = false) String keyword) {
        return Result.ok(service.page(page, size, status, reason, userGroup, adminName, keyword));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('crawler:health:view', 'crawler:schedule:view')")
    public Result<?> summary(@RequestParam(required = false) String status,
                             @RequestParam(required = false) String reason,
                             @RequestParam(required = false) String userGroup,
                             @RequestParam(required = false) String adminName,
                             @RequestParam(required = false) String keyword) {
        return Result.ok(service.summary(status, reason, userGroup, adminName, keyword));
    }

    @GetMapping("/filters")
    @PreAuthorize("hasAnyAuthority('crawler:health:view', 'crawler:schedule:view')")
    public Result<?> filters() { return Result.ok(service.filterOptions()); }

    @GetMapping("/config")
    @PreAuthorize("hasAnyAuthority('crawler:health:view', 'crawler:schedule:view')")
    public Result<?> config() { return Result.ok(configService.getSiteHealthStrategy()); }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('crawler:schedule:update')")
    public Result<?> updateConfig(@RequestBody java.util.Map<String, Object> body) {
        return Result.ok(configService.updateSiteHealthStrategy(body));
    }

    @PostMapping("/trigger")
    @PreAuthorize("hasAnyAuthority('crawler:health:trigger', 'crawler:schedule:trigger')")
    public Result<?> trigger() { return Result.ok(service.trigger("manual")); }
}
