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
        return Result.ok(crawlerConfigService.getRuntimeConfig(true));
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('crawler:site:config:update', 'crawler:order:config', 'crawler:site:start', 'crawler:collect:start')")
    public Result<Map<String, Object>> updateRuntimeConfig(@RequestBody Map<String, Object> body) {
        return Result.ok(crawlerConfigService.updateRuntimeConfig(body));
    }

    @GetMapping("/schedules")
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:order:view', 'crawler:collect:start')")
    public Result<List<CrawlerScheduleConfig>> listSchedules() {
        return Result.ok(crawlerConfigService.listSchedules());
    }

    @PutMapping("/schedules/{taskType}")
    @PreAuthorize("hasAnyAuthority('crawler:site:config:update', 'crawler:order:config', 'crawler:site:start', 'crawler:collect:start')")
    public Result<CrawlerScheduleConfig> updateSchedule(@PathVariable String taskType, @RequestBody Map<String, Object> body) {
        return Result.ok(crawlerConfigService.updateSchedule(taskType, body));
    }

    @PostMapping("/schedules/{taskType}/trigger")
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:collect:start', 'crawler:order:config')")
    public Result<Map<String, Object>> trigger(@PathVariable String taskType) {
        return switch (taskType) {
            case "site_crawl" -> Result.ok(crawlerService.triggerSiteCrawler());
            case "site_index" -> Result.ok(crawlerService.triggerSiteIndexCrawler());
            case "order_crawl" -> Result.ok(crawlerService.triggerAllOrderCrawlers());
            default -> Result.fail("Unsupported task type: " + taskType);
        };
    }
}
