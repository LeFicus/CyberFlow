package com.cyberflow.admin.crawler.selector.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 选择器模板实体类，对应数据表 selector_template。
 * <p>
 * 定义了一套完整的 CSS/XPath 选择器规则，用于从不同电商平台的商品页面中
 * 提取标题、价格、描述、图片、面包屑导航、站点地图等关键信息。
 * 支持系统预置模板（isSystem=1）和用户自定义模板（isSystem=0）。
 * </p>
 *
 * @author CyberFlow
 */
@Data
@TableName("selector_template")
public class SelectorTemplate {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板名称，用于标识和搜索 */
    private String name;

    /** 模板类型：Shopify 专用或非 Shopify 共用的 WooCommerce 选择器。 */
    private String platform;

    /** 商品标题选择器（CSS/XPATH） */
    private String titleSelector;

    /** 商品价格选择器（CSS/XPATH） */
    private String priceSelector;

    /** 价格正则表达式，用于从价格文本中提取纯数字 */
    private String priceRegex;

    /** 商品描述选择器（CSS/XPATH） */
    private String descriptionSelector;

    /** 商品图片选择器（CSS/XPATH） */
    private String imagesSelector;

    /** 货币单位，如 USD、CNY、EUR */
    private String currency;

    /** 面包屑导航链接选择器 */
    private String breadcrumbLinksSelector;

    /** 面包屑导航末级（当前页面）选择器 */
    private String breadcrumbLastSelector;

    /** 站点地图入口选择器 */
    private String siteMapSelector;

    /** 是否为系统模板：1-系统预置（不可删除），0-用户创建 */
    private Integer isSystem;

    /** 模板创建时间 */
    private LocalDateTime createdAt;
}
