package com.cyberflow.admin.crawler.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

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

    /** Read only a bounded log slice so live polling never transfers the full LONGTEXT. */
    @Select("SELECT task_id AS taskId, status, " +
            "CHAR_LENGTH(COALESCE(crawl_log, '')) AS totalLength, " +
            "SUBSTRING(COALESCE(crawl_log, ''), #{offset} + 1, #{limit}) AS chunk " +
            "FROM task_history WHERE task_id = #{taskId} LIMIT 1")
    Map<String, Object> selectLogChunk(@Param("taskId") String taskId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /** Open a historical log at its tail instead of rendering an unbounded document. */
    @Select("SELECT task_id AS taskId, status, " +
            "CHAR_LENGTH(COALESCE(crawl_log, '')) AS totalLength, " +
            "RIGHT(COALESCE(crawl_log, ''), #{tailLength}) AS chunk " +
            "FROM task_history WHERE task_id = #{taskId} LIMIT 1")
    Map<String, Object> selectLogTail(@Param("taskId") String taskId,
                                      @Param("tailLength") int tailLength);
}
