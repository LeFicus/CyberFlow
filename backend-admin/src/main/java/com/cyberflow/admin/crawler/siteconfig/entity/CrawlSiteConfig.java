package com.cyberflow.admin.crawler.siteconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("crawl_site_config")
public class CrawlSiteConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String domain;
    private String type;
    private String category;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
