package com.cyberflow.admin.crawler.selector.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.selector.entity.SelectorTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 选择器模板数据访问接口。
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，提供基本的 CRUD 操作，
 * 无需额外定义 SQL 方法即可满足常规查询需求。
 * </p>
 *
 * @author CyberFlow
 */
@Mapper
public interface SelectorTemplateMapper extends BaseMapper<SelectorTemplate> {
}
