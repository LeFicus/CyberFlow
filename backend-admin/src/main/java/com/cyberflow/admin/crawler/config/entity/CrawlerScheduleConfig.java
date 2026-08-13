package com.cyberflow.admin.crawler.config.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爬虫定时任务配置，对应 crawler_schedule_config。
 */
@Data
@TableName("crawler_schedule_config")
public class CrawlerScheduleConfig {

    @TableId
    private String taskType;

    private String cronExpression;

    private Integer enabled;

    private LocalDateTime lastTriggeredAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
