package com.cyberflow.admin.crawler.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyberflow.admin.crawler.messaging.TaskMessagePublisher;
import com.cyberflow.admin.crawler.task.entity.CrawlCursor;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.mapper.CrawlCursorMapper;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SiteCrawlJob implements Job {

    private final TaskMessagePublisher publisher;
    private final CrawlCursorMapper cursorMapper;
    private final TaskHistoryService taskHistoryService;

    @Value("${CRAWLER_USERNAME:}")
    private String crawlerUsername;

    @Value("${CRAWLER_PASSWORD:}")
    private String crawlerPassword;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("Quartz triggered: site crawl job");

        CrawlCursor cursor = cursorMapper.selectOne(
            new LambdaQueryWrapper<CrawlCursor>().eq(CrawlCursor::getCursorKey, "site_crawler")
        );
        String lastUpdatedAt = cursor != null
            ? cursor.getCursorValue()
            : LocalDateTime.now().minusDays(1).toString();

        String taskId = publisher.publishSiteCrawl(crawlerUsername, crawlerPassword, lastUpdatedAt);

        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setType("site_crawl");
        history.setTriggerType("cron");
        history.setStatus("PENDING");
        history.setCursorBefore(lastUpdatedAt);
        taskHistoryService.save(history);

        log.info("Site crawl task dispatched: {}", taskId);
    }
}
