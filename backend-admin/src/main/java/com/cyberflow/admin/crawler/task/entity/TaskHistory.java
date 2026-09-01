package com.cyberflow.admin.crawler.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务历史实体类，对应数据表 task_history。
 * <p>
 * 记录每一次爬虫任务的完整生命周期信息，包括任务标识、类型、
 * 触发方式、执行状态、影响行数、耗时、错误信息和光标变更记录等。
 * 是爬虫任务执行追踪和问题排查的核心数据模型。
 * </p>
 *
 * @author CyberFlow
 */
@Data
@TableName("task_history")
public class TaskHistory {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务唯一标识（UUID），用于关联消息队列中的任务消息 */
    private String taskId;

    /** 任务类型：site_crawl（站点爬取）、site_index（站点收录）、order_crawl（订单爬取）、product_crawl（商品爬取） */
    private String type;

    /** 触发方式：manual（手动触发）、cron（定时触发） */
    private String triggerType;

    /** 触发者标识，手动触发时为用户名或用户 ID，定时触发时可能为 null */
    private String triggeredBy;

    /** 任务执行状态：PENDING（等待中）、RUNNING（执行中）、SUCCESS（成功）、FAILED（失败） */
    private String status;

    /** 当前采集进度，范围 0-100。 */
    private Integer progress;

    /** 面向操作员展示的当前执行阶段。 */
    private String progressMessage;

    /** 任务执行前的爬取光标值，用于追踪增量爬取的起始位置 */
    private String cursorBefore;

    /** 任务执行后的爬取光标值，记录本次爬取到达的最终位置 */
    private String cursorAfter;

    /** 本次任务影响的数据库行数，如插入/更新的记录数 */
    private Integer rowsAffected;

    /** 任务失败时的错误信息，成功时为 null */
    private String errorMsg;

    /** 旧版日志字段；新日志以不可变分块写入 task_crawl_log。 */
    private String crawlLog;

    /** 任务执行耗时，单位毫秒 */
    private Long durationMs;

    /** 任务开始执行的时间 */
    private LocalDateTime startedAt;

    /** 任务执行完成的时间 */
    private LocalDateTime finishedAt;

    /** 任务创建时间（消息发布到队列的时间） */
    private LocalDateTime createdAt;
}
