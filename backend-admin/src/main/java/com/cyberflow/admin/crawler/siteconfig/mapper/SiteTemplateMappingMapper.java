package com.cyberflow.admin.crawler.siteconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.siteconfig.entity.SiteTemplateMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站点-模板映射数据访问接口。
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，提供基本的 CRUD 操作。
 * </p>
 *
 * @author CyberFlow
 */
@Mapper
public interface SiteTemplateMappingMapper extends BaseMapper<SiteTemplateMapping> {
}
