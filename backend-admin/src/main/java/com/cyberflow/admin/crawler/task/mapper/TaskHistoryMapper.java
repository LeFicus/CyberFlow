package com.cyberflow.admin.crawler.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务历史数据访问接口。
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，提供基本的 CRUD 和分页查询操作。
 * </p>
 *
 * @author CyberFlow
 */
@Mapper
public interface TaskHistoryMapper extends BaseMapper<TaskHistory> {
}
