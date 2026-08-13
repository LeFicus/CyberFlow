package com.cyberflow.admin.crawler.config.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爬虫运行配置项，对应 crawler_runtime_config。
 */
@Data
@TableName("crawler_runtime_config")
public class CrawlerRuntimeConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configGroup;

    private String configKey;

    private String configValue;

    @TableField("is_sensitive")
    private Integer sensitiveFlag;

    public Integer getSensitive() {
        return sensitiveFlag;
    }

    public void setSensitive(Integer sensitive) {
        this.sensitiveFlag = sensitive;
    }

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
