package com.cyberflow.admin.crawler.siteconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("site_template_mapping")
public class SiteTemplateMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long siteConfigId;
    private Long templateId;
    private String extraSelectors;
    private LocalDateTime createdAt;
}
