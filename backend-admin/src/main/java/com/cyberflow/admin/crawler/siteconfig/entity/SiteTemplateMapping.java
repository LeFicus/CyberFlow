package com.cyberflow.admin.crawler.siteconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 站点-模板映射实体类，对应数据表 site_template_mapping。
 * <p>
 * 建立爬取站点配置与选择器模板之间的多对多关联关系。
 * 一个站点可以绑定多个模板（用于解析不同类型的商品页面），
 * 每个映射可携带额外的选择器配置（extraSelectors）。
 * </p>
 *
 * @author CyberFlow
 */
@Data
@TableName("site_template_mapping")
public class SiteTemplateMapping {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的站点配置 ID */
    private Long siteConfigId;

    /** 关联的选择器模板 ID */
    private Long templateId;

    /** 额外的选择器配置（JSON 格式），用于覆盖或扩展模板默认选择器 */
    private String extraSelectors;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
