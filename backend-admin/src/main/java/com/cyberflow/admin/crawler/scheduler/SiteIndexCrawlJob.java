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

/** Dispatches the daily site-indexing statistics snapshot. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteIndexCrawlJob implements Job {

    private final TaskMessagePublisher publisher;
    private final CrawlCursorMapper cursorMapper;
    private final TaskHistoryService taskHistoryService;
    private final CrawlerConfigService crawlerConfigService;

    @Override
    public void execute(JobExecutionContext context) {
        if (!crawlerConfigService.isScheduleEnabled("site_index")) {
            log.info("Site index schedule is disabled");
            return;
        }
        CrawlCursor cursor = cursorMapper.selectOne(
            new LambdaQueryWrapper<CrawlCursor>().eq(CrawlCursor::getCursorKey, "site_index_crawler")
        );
        String lastRecordedAt = cursor != null
            ? cursor.getCursorValue()
            : LocalDateTime.now().minusDays(1).toString();

        String taskId = publisher.publishSiteIndexCrawl(
            crawlerConfigService.getAdminPlatform(),
            crawlerConfigService.getSiteStrategy(),
            lastRecordedAt,
            "cron"
        );
        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setType("site_index");
        history.setTriggerType("cron");
        history.setStatus("PENDING");
        history.setCursorBefore(lastRecordedAt);
        taskHistoryService.save(history);
        crawlerConfigService.markTriggered("site_index");
        log.info("Site index crawl task dispatched: {}", taskId);
    }
}
