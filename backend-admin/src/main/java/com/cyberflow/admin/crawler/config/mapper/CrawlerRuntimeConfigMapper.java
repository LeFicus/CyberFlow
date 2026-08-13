package com.cyberflow.admin.crawler.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.config.entity.CrawlerRuntimeConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CrawlerRuntimeConfigMapper extends BaseMapper<CrawlerRuntimeConfig> {
}
