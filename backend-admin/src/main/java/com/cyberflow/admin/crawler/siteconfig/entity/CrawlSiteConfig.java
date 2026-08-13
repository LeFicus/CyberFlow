package com.cyberflow.admin.crawler.siteconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 爬取站点配置实体类，对应数据表 crawl_site_config。
 * <p>
 * 用于存储待爬取的目标站点信息，包括域名、站点类型、商品分类等。
 * 每个站点配置可以与多个选择器模板通过 site_template_mapping 表关联。
 * </p>
 *
 * @author CyberFlow
 */
@Data
@TableName("crawl_site_config")
public class CrawlSiteConfig {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标站点域名，如 example.com */
    private String domain;

    /** 站点引擎；除 Shopify 外当前统一复用 WooCommerce 选择器。 */
    private String type;

    /** 商品分类，如 "电子产品"、"服装" 等 */
    private String category;

    /** 站点状态，active-激活、inactive-停用 */
    private String status;

    /** 创建者用户 ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
