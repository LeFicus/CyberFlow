package com.cyberflow.admin.crawler.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.cursor.Cursor;

import java.time.LocalDateTime;
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

    /** Aggregate the small set of counters needed by the data-sync console. */
    @Select("SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN status IN ('PENDING','RUNNING') THEN 1 ELSE 0 END), 0) AS activeCount, " +
            "COALESCE(SUM(CASE WHEN status='SUCCESS' AND finished_at BETWEEN CURRENT_DATE() AND CURRENT_TIMESTAMP() THEN 1 ELSE 0 END), 0) AS successToday, " +
            "COALESCE(SUM(CASE WHEN status='FAILED' AND finished_at BETWEEN CURRENT_DATE() AND CURRENT_TIMESTAMP() THEN 1 ELSE 0 END), 0) AS failedToday " +
            "FROM task_history")
    Map<String, Object> selectOverviewCounts();

    /** Count active work by task type so the UI can prevent duplicate dispatches. */
    @Select("SELECT type, COUNT(*) AS activeCount FROM task_history " +
            "WHERE status IN ('PENDING','RUNNING','PAUSED') GROUP BY type")
    List<Map<String, Object>> selectActiveCountsByType();

    /** Count active order tasks by their site-group scope (group-A, group-B, ...). */
    @Select("SELECT triggered_by AS scope, COUNT(*) AS activeCount FROM task_history " +
            "WHERE type='order_crawl' AND status IN ('PENDING','RUNNING','PAUSED') " +
            "GROUP BY triggered_by")
    List<Map<String, Object>> selectActiveOrderCountsByScope();

    /** Return only one newest, log-free row for each task type. */
    @Select("SELECT h.id, h.task_id, h.type, h.trigger_type, h.triggered_by, h.status, " +
            "h.progress, h.progress_message, h.cursor_before, h.cursor_after, h.rows_affected, " +
            "h.error_msg, h.duration_ms, h.started_at, h.finished_at, h.created_at " +
            "FROM task_history h INNER JOIN " +
            "(SELECT type, MAX(id) AS id FROM task_history GROUP BY type) newest ON newest.id=h.id " +
            "ORDER BY h.created_at DESC")
    List<TaskHistory> selectLatestTasksByType();

    @Select("SELECT MAX(finished_at) FROM task_history WHERE status='SUCCESS'")
    LocalDateTime selectLatestSuccessAt();

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
            "WHERE endOffset > #{startOffset} AND startOffset < #{endOffset} ORDER BY id")
    List<Map<String, Object>> selectLogSegments(@Param("taskId") String taskId,
                                                 @Param("startOffset") long startOffset,
                                                 @Param("endOffset") long endOffset);

    /** Stream complete downloads without materializing the task log in memory. */
    @Select("SELECT content FROM task_crawl_log WHERE task_id=#{taskId} ORDER BY id")
    @Options(fetchSize = 100)
    Cursor<Map<String, Object>> streamLogSegments(@Param("taskId") String taskId);
}
