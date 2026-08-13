package com.cyberflow.admin.crawler.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.crawler.messaging.TaskMessagePublisher;
import com.cyberflow.admin.crawler.task.entity.CrawlCursor;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.mapper.CrawlCursorMapper;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Quartz 定时站点爬取任务。
 * <p>
 * 由 Quartz 调度器按 cron 表达式定期触发，从数据库中读取上次爬取的光标位置（lastUpdatedAt），
 * 然后通过消息队列发布站点爬取任务，实现增量爬取。
 * </p>
 *
 * @author CyberFlow
 * @see OrderCrawlJob
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteCrawlJob implements Job {

    /** 任务消息发布器 */
    private final TaskMessagePublisher publisher;

    /** 爬取光标映射器，用于读取上次爬取的断点位置 */
    private final CrawlCursorMapper cursorMapper;

    /** 任务历史服务，用于记录任务执行记录 */
    private final TaskHistoryService taskHistoryService;

    /** 爬虫运行配置服务 */
    private final CrawlerConfigService crawlerConfigService;

    /**
     * Quartz 调度器触发的执行方法。
     * <p>
     * 从 crawl_cursor 表读取上次同步的光标值，
     * 若未找到则默认取一天前的时间作为起始点，然后发布消息并记录任务历史。
     * </p>
     *
     * @param context Quartz 任务执行上下文
     */
    @Override
    public void execute(JobExecutionContext context) {
        log.info("Quartz triggered: site crawl job");
        if (!crawlerConfigService.isScheduleEnabled("site_crawl")) {
            log.info("Site crawl schedule is disabled");
            return;
        }

        CrawlCursor cursor = cursorMapper.selectOne(
            new LambdaQueryWrapper<CrawlCursor>().eq(CrawlCursor::getCursorKey, "site_crawler")
        );
        String lastUpdatedAt = cursor != null
            ? cursor.getCursorValue()
            : LocalDateTime.now().minusDays(1).toString();

        String taskId = publisher.publishSiteCrawl(
            crawlerConfigService.getAdminPlatform(),
            crawlerConfigService.getSiteStrategy(),
            lastUpdatedAt,
            "cron"
        );

        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setType("site_crawl");
        history.setTriggerType("cron");
        history.setStatus("PENDING");
        history.setCursorBefore(lastUpdatedAt);
        taskHistoryService.save(history);
        crawlerConfigService.markTriggered("site_crawl");

        log.info("Site crawl task dispatched: {}", taskId);
    }
}
