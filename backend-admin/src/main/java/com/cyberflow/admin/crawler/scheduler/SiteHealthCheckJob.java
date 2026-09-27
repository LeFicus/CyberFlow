package com.cyberflow.admin.crawler.scheduler;

import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.crawler.health.SiteHealthCheckService;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/** Quartz entry point for the recurring site-health monitor. */
@Component
@Slf4j
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class SiteHealthCheckJob implements Job {
    private final SiteHealthCheckService service;
    private final TaskHistoryService taskHistoryService;
    private final CrawlerConfigService configService;

    @Override
    public void execute(JobExecutionContext context) {
        if (!configService.isScheduleEnabled("site_health")) return;
        if (taskHistoryService.hasActiveTask("site_health", null)) {
            log.info("Site health check already active; skipping scheduled run");
            return;
        }
        service.trigger("cron");
        configService.markTriggered("site_health");
    }
}
