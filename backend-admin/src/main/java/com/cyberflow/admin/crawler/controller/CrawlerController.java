package com.cyberflow.admin.crawler.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    private final CrawlerService crawlerService;

    @PostMapping("/site/start")
    @PreAuthorize("hasAuthority('crawler:site:start')")
    public Result<Map<String, Object>> startSiteCrawler(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        return Result.ok(crawlerService.triggerSiteCrawler(username, password));
    }

    @PostMapping("/site/collect")
    @PreAuthorize("hasAuthority('crawler:collect:start')")
    public Result<Map<String, Object>> collectSite(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        return Result.ok(crawlerService.triggerSiteIndexCrawler(username, password));
    }

    @PostMapping("/order/start")
    @PreAuthorize("hasAuthority('crawler:order:start')")
    public Result<Map<String, Object>> startOrderCrawler(@RequestBody Map<String, String> body) {
        String startTime = body.get("start_time");
        String endTime = body.get("end_time");
        return Result.ok(crawlerService.triggerOrderCrawler(startTime, endTime));
    }

    @GetMapping("/status/{taskId}")
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:collect:start', 'crawler:order:start')")
    public Result<Map<String, Object>> status(@PathVariable String taskId) {
        return Result.ok(crawlerService.getTaskStatus(taskId));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:collect:start', 'crawler:order:start')")
    public Result<Map<String, Map<String, Object>>> tasks() {
        return Result.ok(crawlerService.getRecentTasks());
    }
}
