package com.cyberflow.admin.crawler.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.mapper.TaskHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    public Page<TaskHistory> list(int pageNum, int pageSize) {
        Page<TaskHistory> page = new Page<>(pageNum, pageSize);
        QueryWrapper<TaskHistory> wrapper = new QueryWrapper<>();
        // 日志可能很大，列表接口不读取正文；按 taskId 查看时再单独获取。
        wrapper.select(TaskHistory.class, field -> !"crawl_log".equals(field.getColumn()));
        wrapper.orderByDesc("created_at");
        return taskHistoryMapper.selectPage(page, wrapper);
    }

    /**
     * 保存任务历史记录（插入新记录）。
     *
     * @param taskHistory 任务历史实体
     */
    public void save(TaskHistory taskHistory) {
        taskHistoryMapper.insert(taskHistory);
    }
}
