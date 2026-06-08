package com.cyberflow.admin.crawler.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("task_history")
public class TaskHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private String type;
    private String triggerType;
    private String triggeredBy;
    private String status;
    private String cursorBefore;
    private String cursorAfter;
    private Integer rowsAffected;
    private String errorMsg;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
