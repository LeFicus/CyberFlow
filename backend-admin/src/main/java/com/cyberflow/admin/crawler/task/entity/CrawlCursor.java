package com.cyberflow.admin.crawler.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("crawl_cursor")
public class CrawlCursor {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String cursorKey;
    private String cursorValue;
    private LocalDateTime lastSyncAt;
    private LocalDateTime updatedAt;
}
