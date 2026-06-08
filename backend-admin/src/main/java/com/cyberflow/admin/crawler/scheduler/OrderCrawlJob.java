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
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCrawlJob implements Job {

    private final TaskMessagePublisher publisher;
    private final CrawlCursorMapper cursorMapper;
    private final TaskHistoryService taskHistoryService;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("Quartz triggered: order crawl job");

        CrawlCursor cursor = cursorMapper.selectOne(
            new LambdaQueryWrapper<CrawlCursor>().eq(CrawlCursor::getCursorKey, "order_crawler")
        );
        String maxOrderId = cursor != null ? cursor.getCursorValue() : "0";

        String taskId = publisher.publishOrderCrawl(maxOrderId);

        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setType("order_crawl");
        history.setTriggerType("cron");
        history.setStatus("PENDING");
        history.setCursorBefore(maxOrderId);
        taskHistoryService.save(history);

        log.info("Order crawl task dispatched: {}", taskId);
    }
}
