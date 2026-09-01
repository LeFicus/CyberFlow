package com.cyberflow.admin.crawler.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cursor.Cursor;

import java.util.List;
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

    /** Read task state and the append-only log's logical character length. */
    @Select("SELECT h.task_id AS taskId, h.status, " +
            "COALESCE((SELECT SUM(l.content_length) FROM task_crawl_log l " +
            "WHERE l.task_id=h.task_id), 0) AS totalLength " +
            "FROM task_history h WHERE h.task_id=#{taskId} LIMIT 1")
    Map<String, Object> selectLogMetadata(@Param("taskId") String taskId);

    /** Fetch only immutable chunks overlapping the requested logical range. */
    @Select("WITH ordered_log AS (" +
            "SELECT id, content, " +
            "COALESCE(SUM(content_length) OVER (ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0) AS startOffset, " +
            "SUM(content_length) OVER (ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS endOffset " +
            "FROM task_crawl_log WHERE task_id=#{taskId}) " +
            "SELECT content, startOffset, endOffset FROM ordered_log " +
            "WHERE endOffset &gt; #{startOffset} AND startOffset &lt; #{endOffset} ORDER BY id")
    List<Map<String, Object>> selectLogSegments(@Param("taskId") String taskId,
                                                 @Param("startOffset") long startOffset,
                                                 @Param("endOffset") long endOffset);

    /** Stream complete downloads without materializing the task log in memory. */
    @Select("SELECT content FROM task_crawl_log WHERE task_id=#{taskId} ORDER BY id")
    @Options(fetchSize = 100)
    Cursor<Map<String, Object>> streamLogSegments(@Param("taskId") String taskId);
}
