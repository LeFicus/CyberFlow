package com.cyberflow.admin.crawler.selector.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("selector_template")
public class SelectorTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String platform;
    private String titleSelector;
    private String priceSelector;
    private String priceRegex;
    private String descriptionSelector;
    private String imagesSelector;
    private String currency;
    private String breadcrumbLinksSelector;
    private String breadcrumbLastSelector;
    private String siteMapSelector;
    private Integer isSystem;
    private LocalDateTime createdAt;
}
