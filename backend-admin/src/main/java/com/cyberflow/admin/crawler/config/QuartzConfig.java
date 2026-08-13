package com.cyberflow.admin.crawler.config;

import com.cyberflow.admin.crawler.scheduler.OrderCrawlJob;
import com.cyberflow.admin.crawler.scheduler.SiteCrawlJob;
import com.cyberflow.admin.crawler.scheduler.SiteIndexCrawlJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz 定时任务调度配置类。
 * <p>
 * 定义站点爬取和订单爬取两个定时任务的 JobDetail 与 Trigger，
 * 通过 {@code application.properties} 中的 cron 表达式控制执行周期。
 * </p>
 *
 * <h3>配置属性</h3>
 * <ul>
 *   <li>{@code cyberflow.crawler.site-cron} - 站点爬取 cron 表达式</li>
 *   <li>{@code cyberflow.crawler.order-cron} - 订单爬取 cron 表达式</li>
 * </ul>
 *
 * @author CyberFlow
 * @see SiteCrawlJob
 * @see OrderCrawlJob
 */
@Configuration
public class QuartzConfig {

    /** 站点爬取任务的 cron 表达式，从配置文件注入 */
    @Value("${cyberflow.crawler.site-cron}")
    private String siteCron;

    /** 订单爬取任务的 cron 表达式，从配置文件注入 */
    @Value("${cyberflow.crawler.order-cron}")
    private String orderCron;

    @Value("${cyberflow.crawler.index-cron}")
    private String indexCron;

    /**
     * 创建站点爬取任务的 JobDetail，设置为持久化存储。
     *
     * @return JobDetail 实例
     */
    @Bean
    public JobDetail siteCrawlJobDetail() {
        return JobBuilder.newJob(SiteCrawlJob.class)
                .withIdentity("siteCrawlJob")
                .storeDurably()
                .build();
    }

    /**
     * 创建站点爬取任务的触发器，使用 cron 表达式调度。
     *
     * @return Trigger 实例
     */
    @Bean
    public Trigger siteCrawlTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(siteCrawlJobDetail())
                .withIdentity("siteCrawlTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(siteCron))
                .build();
    }

    @Bean
    public JobDetail siteIndexCrawlJobDetail() {
        return JobBuilder.newJob(SiteIndexCrawlJob.class)
                .withIdentity("siteIndexCrawlJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger siteIndexCrawlTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(siteIndexCrawlJobDetail())
                .withIdentity("siteIndexCrawlTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(indexCron))
                .build();
    }

    /**
     * 创建订单爬取任务的 JobDetail，设置为持久化存储。
     *
     * @return JobDetail 实例
     */
    @Bean
    public JobDetail orderCrawlJobDetail() {
        return JobBuilder.newJob(OrderCrawlJob.class)
                .withIdentity("orderCrawlJob")
                .storeDurably()
                .build();
    }

    /**
     * 创建订单爬取任务的触发器，使用 cron 表达式调度。
     *
     * @return Trigger 实例
     */
    @Bean
    public Trigger orderCrawlTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(orderCrawlJobDetail())
                .withIdentity("orderCrawlTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(orderCron))
                .build();
    }
}
