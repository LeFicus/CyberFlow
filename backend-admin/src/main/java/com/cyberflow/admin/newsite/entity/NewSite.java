package com.cyberflow.admin.newsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 生成的新站点记录。
 *
 * <p>该表描述的是待建设/运营的新站点，不是商品采集用的 crawl_site_config。</p>
 */
@Data
@TableName("new_site")
public class NewSite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String domain;
    private String customCategory;
    /** JSON array of selected main-product categories. */
    private String mainProductCategories;
    /** JSON array of selected supplement-product categories. */
    private String supplementProductCategories;
    /** Legacy display column retained for compatibility with older clients. */
    private String mainProductCategory;
    /** Legacy display column retained for compatibility with older clients. */
    private String supplementProductCategory;
    /** Legacy normalized key column; overlap validation is performed in the service. */
    private String supplementProductCategoryKey;
    /** JSON array of source domains. */
    private String sourceDomains;
    private String siteTitle;
    private String tagLine;
    /** pending_review / enabled / disabled */
    private String status;
    private String domainCheckStatus;
    private String domainCheckProvider;
    private Integer generationAttempts;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
