package com.cyberflow.admin.crawler.config;

import com.cyberflow.admin.crawler.scheduler.OrderCrawlJob;
import com.cyberflow.admin.crawler.scheduler.SiteCrawlJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Value("${cyberflow.crawler.site-cron}")
    private String siteCron;

    @Value("${cyberflow.crawler.order-cron}")
    private String orderCron;

    @Bean
    public JobDetail siteCrawlJobDetail() {
        return JobBuilder.newJob(SiteCrawlJob.class)
                .withIdentity("siteCrawlJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger siteCrawlTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(siteCrawlJobDetail())
                .withIdentity("siteCrawlTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(siteCron))
                .build();
    }

    @Bean
    public JobDetail orderCrawlJobDetail() {
        return JobBuilder.newJob(OrderCrawlJob.class)
                .withIdentity("orderCrawlJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger orderCrawlTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(orderCrawlJobDetail())
                .withIdentity("orderCrawlTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(orderCron))
                .build();
    }
}
