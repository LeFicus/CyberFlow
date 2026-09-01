package com.cyberflow.admin.crawler.task.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.mapper.TaskHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.cursor.Cursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务历史业务服务。
 * <p>
 * 提供任务历史的保存、按 taskId 查询和分页列表等核心操作。
 * </p>
 *
 * @author CyberFlow
 */
@Service
@RequiredArgsConstructor
public class TaskHistoryService {

    /** 任务历史数据访问接口 */
    private final TaskHistoryMapper taskHistoryMapper;

    /**
     * 根据任务 ID 查询任务历史记录。
     *
     * @param taskId 任务唯一标识（UUID）
     * @return 任务历史实体，未找到时返回 null
     */
    public TaskHistory getByTaskId(String taskId) {
        QueryWrapper<TaskHistory> wrapper = new QueryWrapper<>();
        wrapper.select(TaskHistory.class, field -> !"crawl_log".equals(field.getColumn()));
        wrapper.eq("task_id", taskId);
        return taskHistoryMapper.selectOne(wrapper);
    }

    public Map<String, Object> getLogChunk(String taskId, long offset, int limit) {
        Map<String, Object> metadata = taskHistoryMapper.selectLogMetadata(taskId);
        return metadata == null ? null : buildLogChunk(taskId, Math.max(0, offset), limit, metadata);
    }

    public Map<String, Object> getLogTail(String taskId, int tailLength) {
        Map<String, Object> metadata = taskHistoryMapper.selectLogMetadata(taskId);
        if (metadata == null) return null;
        long totalLength = number(metadata.get("totalLength"));
        return buildLogChunk(taskId, Math.max(0, totalLength - tailLength), tailLength, metadata);
    }

    /** Stream an entire append-only log directly to the HTTP response writer. */
    @Transactional(readOnly = true)
    public void writeLog(String taskId, Writer writer) throws IOException {
        try (Cursor<Map<String, Object>> segments = taskHistoryMapper.streamLogSegments(taskId)) {
            for (Map<String, Object> segment : segments) {
                Object content = segment.get("content");
                if (content != null) writer.write(String.valueOf(content));
            }
        }
    }

    private Map<String, Object> buildLogChunk(String taskId, long offset, int limit,
                                               Map<String, Object> metadata) {
        long totalLength = number(metadata.get("totalLength"));
        long safeOffset = Math.min(Math.max(0, offset), totalLength);
        long endOffset = safeOffset + Math.min(totalLength - safeOffset, Math.max(1, limit));
        StringBuilder chunk = new StringBuilder(Math.max(0, limit));
        if (safeOffset < endOffset) {
            for (Map<String, Object> segment : taskHistoryMapper.selectLogSegments(taskId, safeOffset, endOffset)) {
                String content = segment.get("content") == null ? "" : String.valueOf(segment.get("content"));
                long segmentStart = number(segment.get("startOffset"));
                int codePoints = content.codePointCount(0, content.length());
                int from = (int) Math.max(0, safeOffset - segmentStart);
                int to = (int) Math.min(codePoints, endOffset - segmentStart);
                if (from < to) {
                    int fromIndex = content.offsetByCodePoints(0, from);
                    int toIndex = content.offsetByCodePoints(0, to);
                    chunk.append(content, fromIndex, toIndex);
                }
            }
        }
        long nextOffset = Math.min(totalLength,
                safeOffset + chunk.codePointCount(0, chunk.length()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", metadata.get("taskId"));
        result.put("status", metadata.get("status"));
        result.put("chunk", chunk.toString());
        result.put("nextOffset", nextOffset);
        result.put("totalLength", totalLength);
        result.put("truncated", safeOffset > 0);
        result.put("hasMore", nextOffset < totalLength);
        return result;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * 分页查询任务历史列表，按创建时间倒序排列。
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果对象，包含 records、total、current 等字段
     */
    public Page<TaskHistory> list(int pageNum, int pageSize, String type) {
        Page<TaskHistory> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TaskHistory> wrapper = new QueryWrapper<>();
        // 日志可能很大，列表接口不读取正文；按 taskId 查看时再单独获取。
        wrapper.select(TaskHistory.class, field -> !"crawl_log".equals(field.getColumn()));
        if (type != null && !type.isBlank() && !"all".equalsIgnoreCase(type)) {
            wrapper.eq("type", type.trim());
        }
        wrapper.orderByDesc("created_at");
        return taskHistoryMapper.selectPage(page, wrapper);
    }

    /** Return independent totals for each crawler type for the task-history tabs. */
    public Map<String, Long> summary() {
        List<Map<String, Object>> rows = taskHistoryMapper.selectMaps(
                new QueryWrapper<TaskHistory>().select("type", "COUNT(*) AS total").groupBy("type")
        );
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("all", 0L);
        for (Map<String, Object> row : rows) {
            String type = String.valueOf(row.get("type"));
            long total = ((Number) row.get("total")).longValue();
            result.put(type, total);
            result.put("all", result.get("all") + total);
        }
        return result;
    }

    /**
     * 保存任务历史记录（插入新记录）。
     *
     * @param taskHistory 任务历史实体
     */
    public void save(TaskHistory taskHistory) {
        taskHistoryMapper.insert(taskHistory);
    }

    /** Mark a task that could not be handed to RabbitMQ as failed. */
    public void markDispatchFailed(String taskId, String message) {
        TaskHistory task = getByTaskId(taskId);
        if (task == null) return;
        task.setStatus("FAILED");
        task.setProgress(0);
        task.setProgressMessage("任务下发失败");
        task.setErrorMsg(message);
        task.setFinishedAt(LocalDateTime.now());
        taskHistoryMapper.updateById(task);
    }

    /** Pause a pending or running task. The consumer cooperatively suspends its work. */
    public TaskHistory pause(String taskId) {
        TaskHistory task = requireTask(taskId);
        if (!isActive(task.getStatus())) {
            throw new IllegalStateException("Only pending or running tasks can be paused");
        }
        task.setStatus("PAUSED");
        task.setProgressMessage("任务已暂停，等待继续");
        task.setErrorMsg(null);
        taskHistoryMapper.updateById(task);
        return task;
    }

    /** Resume a task previously put into PAUSED state. */
    public TaskHistory resume(String taskId) {
        TaskHistory task = requireTask(taskId);
        if (!"PAUSED".equalsIgnoreCase(task.getStatus())) {
            throw new IllegalStateException("Only paused tasks can be resumed");
        }
        task.setStatus(task.getStartedAt() == null ? "PENDING" : "RUNNING");
        task.setProgressMessage("任务已继续");
        taskHistoryMapper.updateById(task);
        return task;
    }

    /** Remove a task record; active consumers observe the missing record and stop. */
    public void delete(String taskId) {
        TaskHistory task = getByTaskId(taskId);
        // DELETE is idempotent: repeated clicks or a concurrent consumer
        // cleanup should still return success instead of a 500 error.
        if (task == null) return;
        if (isActive(task.getStatus())) {
            task.setStatus("CANCELLED");
            task.setFinishedAt(LocalDateTime.now());
            task.setProgressMessage("任务已删除并取消");
            taskHistoryMapper.updateById(task);
        }
        taskHistoryMapper.deleteById(task.getId());
    }

    private TaskHistory requireTask(String taskId) {
        TaskHistory task = getByTaskId(taskId);
        if (task == null) throw new IllegalArgumentException("Task not found");
        return task;
    }

    private boolean isActive(String status) {
        return "PENDING".equalsIgnoreCase(status)
                || "RUNNING".equalsIgnoreCase(status);
    }
}
