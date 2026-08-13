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

/**
 * Quartz 定时订单爬取任务。
 * <p>
 * 由 Quartz 调度器按 cron 表达式定期触发，从数据库中读取上次爬取的最大订单 ID 作为光标，
 * 然后通过消息队列发布订单爬取任务，实现增量爬取。
 * </p>
 *
 * @author CyberFlow
 * @see SiteCrawlJob
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCrawlJob implements Job {

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
     * 从 crawl_cursor 表读取上次同步的最大订单 ID，
     * 若未找到则默认从 0 开始，然后发布消息并记录任务历史。
     * </p>
     *
     * @param context Quartz 任务执行上下文
     */
    @Override
    public void execute(JobExecutionContext context) {
        log.info("Quartz triggered: order crawl job");
        if (!crawlerConfigService.isScheduleEnabled("order_crawl")) {
            log.info("Order crawl schedule is disabled");
            return;
        }

        CrawlCursor cursor = cursorMapper.selectOne(
            new LambdaQueryWrapper<CrawlCursor>().eq(CrawlCursor::getCursorKey, "order_crawler")
        );
        String maxOrderId = cursor != null
            ? cursor.getCursorValue()
            : String.valueOf(crawlerConfigService.getOrderStrategy().getOrDefault("initialOrderId", "0"));

        String taskId = publisher.publishOrderCrawl(
            crawlerConfigService.getPaymentPlatform(),
            crawlerConfigService.getOrderStrategy(),
            maxOrderId,
            "cron"
        );

        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setType("order_crawl");
        history.setTriggerType("cron");
        history.setStatus("PENDING");
        history.setCursorBefore(maxOrderId);
        taskHistoryService.save(history);
        crawlerConfigService.markTriggered("order_crawl");

        log.info("Order crawl task dispatched: {}", taskId);
    }
}
