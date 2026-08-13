package com.cyberflow.admin.crawler.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.task.entity.CrawlCursor;
import org.apache.ibatis.annotations.Mapper;

/**
 * 爬取光标数据访问接口。
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，提供基本的 CRUD 操作，
 * 用于管理增量爬取的断点光标记录。
 * </p>
 *
 * @author CyberFlow
 */
@Mapper
public interface CrawlCursorMapper extends BaseMapper<CrawlCursor> {
}
