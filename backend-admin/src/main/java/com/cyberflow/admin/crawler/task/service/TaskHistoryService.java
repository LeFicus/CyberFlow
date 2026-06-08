package com.cyberflow.admin.crawler.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.mapper.TaskHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskHistoryService {

    private final TaskHistoryMapper taskHistoryMapper;

    public TaskHistory getByTaskId(String taskId) {
        return taskHistoryMapper.selectOne(
            new LambdaQueryWrapper<TaskHistory>().eq(TaskHistory::getTaskId, taskId)
        );
    }

    public Page<TaskHistory> list(int pageNum, int pageSize) {
        Page<TaskHistory> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TaskHistory> wrapper = new LambdaQueryWrapper<TaskHistory>()
            .orderByDesc(TaskHistory::getCreatedAt);
        return taskHistoryMapper.selectPage(page, wrapper);
    }

    public void save(TaskHistory taskHistory) {
        taskHistoryMapper.insert(taskHistory);
    }
}
