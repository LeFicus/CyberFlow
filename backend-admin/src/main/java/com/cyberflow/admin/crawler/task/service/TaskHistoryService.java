package com.cyberflow.admin.crawler.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.mapper.TaskHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    /** Read the large log body only for an explicit full-file download. */
    public TaskHistory getByTaskIdWithLog(String taskId) {
        return taskHistoryMapper.selectOne(
                new LambdaQueryWrapper<TaskHistory>().eq(TaskHistory::getTaskId, taskId)
        );
    }

    public Map<String, Object> getLogChunk(String taskId, int offset, int limit) {
        return taskHistoryMapper.selectLogChunk(taskId, offset, limit);
    }

    public Map<String, Object> getLogTail(String taskId, int tailLength) {
        return taskHistoryMapper.selectLogTail(taskId, tailLength);
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
