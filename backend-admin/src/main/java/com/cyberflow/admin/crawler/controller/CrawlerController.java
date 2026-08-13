package com.cyberflow.admin.crawler.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 爬虫任务触发 REST 控制器。
 * <p>
 * 提供手动触发站点爬取、数据收录、订单爬取等任务的 HTTP 接口，
 * 所有接口均需要相应的权限才能访问。
 * </p>
 *
 * <h3>权限列表</h3>
 * <ul>
 *   <li>crawler:site:start - 站点爬取</li>
 *   <li>crawler:collect:start - 数据收录</li>
 *   <li>crawler:order:start - 订单爬取</li>
 * </ul>
 *
 * @author CyberFlow
 */
@RestController
@RequestMapping("/admin/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    /** 爬虫业务服务 */
    private final CrawlerService crawlerService;

    /**
     * 手动触发站点爬取任务。
     *
     * @return 包含 task_id 和状态信息的执行结果
     */
    @PostMapping("/site/start")
    @PreAuthorize("hasAuthority('crawler:site:start')")
    public Result<Map<String, Object>> startSiteCrawler() {
        return Result.ok(crawlerService.triggerSiteCrawler());
    }

    /**
     * 手动触发站点数据收录/索引任务。
     *
     * @return 包含 task_id 和状态信息的执行结果
     */
    @PostMapping("/site/collect")
    @PreAuthorize("hasAuthority('crawler:collect:start')")
    public Result<Map<String, Object>> collectSite() {
        return Result.ok(crawlerService.triggerSiteIndexCrawler());
    }

    /**
     * 手动触发订单爬取任务。
     *
     * @return 包含 task_id 和状态信息的执行结果
     */
    @PostMapping("/order/start")
    @PreAuthorize("hasAuthority('crawler:order:start')")
    public Result<Map<String, Object>> startOrderCrawler() {
        return Result.ok(crawlerService.triggerOrderCrawler());
    }

    /**
     * 查询指定任务的执行状态。
     *
     * @param taskId 任务唯一标识
     * @return 任务状态信息，包含 task_id、state、result 等字段
     */
    @GetMapping("/status/{taskId}")
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:collect:start', 'crawler:order:start', 'crawler:site:config:crawl')")
    public Result<Map<String, Object>> status(@PathVariable String taskId) {
        return Result.ok(crawlerService.getTaskStatus(taskId));
    }
}
