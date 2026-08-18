package com.cyberflow.admin.crawler.config.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.config.entity.CrawlerScheduleConfig;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.crawler.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 爬虫运行配置和定时配置管理接口。
 */
@RestController
@RequestMapping("/admin/crawler/config")
@RequiredArgsConstructor
public class CrawlerConfigController {

    private final CrawlerConfigService crawlerConfigService;
    private final CrawlerService crawlerService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:order:view', 'crawler:collect:start')")
    public Result<Map<String, Object>> getRuntimeConfig() {
        Map<String, Object> result = crawlerConfigService.getRuntimeConfig(true);
        result.remove("revenue");
        return Result.ok(result);
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('crawler:site:config:update', 'crawler:order:config', 'crawler:site:start', 'crawler:collect:start')")
    public Result<Map<String, Object>> updateRuntimeConfig(@RequestBody Map<String, Object> body) {
        return Result.ok(crawlerConfigService.updateRuntimeConfig(body));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAnyAuthority('crawler:revenue:view', 'crawler:revenue:update')")
    public Result<Map<String, Object>> getRevenueConfig() {
        return Result.ok(crawlerConfigService.getRevenueConfig());
    }

    @PutMapping("/revenue")
    @PreAuthorize("hasAuthority('crawler:revenue:update')")
    public Result<Map<String, Object>> updateRevenueConfig(@RequestBody Map<String, Object> body) {
        return Result.ok(crawlerConfigService.updateRevenueConfig(body));
    }

    @GetMapping("/schedules")
    @PreAuthorize("hasAnyAuthority('crawler:schedule:view', 'crawler:schedule:update', 'crawler:schedule:trigger', 'crawler:site:start', 'crawler:order:view', 'crawler:collect:start')")
    public Result<List<CrawlerScheduleConfig>> listSchedules() {
        return Result.ok(crawlerConfigService.listSchedules());
    }

    @PutMapping("/schedules/{taskType}")
    @PreAuthorize("hasAnyAuthority('crawler:schedule:update', 'crawler:site:config:update', 'crawler:order:config', 'crawler:site:start', 'crawler:collect:start')")
    public Result<CrawlerScheduleConfig> updateSchedule(@PathVariable String taskType, @RequestBody Map<String, Object> body) {
        return Result.ok(crawlerConfigService.updateSchedule(taskType, body));
    }

    @PostMapping("/schedules/{taskType}/trigger")
    @PreAuthorize("hasAnyAuthority('crawler:schedule:trigger', 'crawler:site:start', 'crawler:collect:start', 'crawler:order:config')")
    public Result<Map<String, Object>> trigger(@PathVariable String taskType) {
        return switch (taskType) {
            case "site_crawl" -> Result.ok(crawlerService.triggerSiteCrawler());
            case "site_index" -> Result.ok(crawlerService.triggerSiteIndexCrawler());
            case "order_crawl" -> Result.ok(crawlerService.triggerAllOrderCrawlers());
            default -> Result.fail("Unsupported task type: " + taskType);
        };
    }
}
