package com.cyberflow.admin.crawler.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.task.entity.CrawlCursor;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CrawlCursorMapper extends BaseMapper<CrawlCursor> {
}
