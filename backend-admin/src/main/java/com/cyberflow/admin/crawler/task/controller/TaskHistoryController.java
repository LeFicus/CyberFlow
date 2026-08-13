package com.cyberflow.admin.crawler.task.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 任务历史管理 REST 控制器。
 * <p>
 * 提供任务状态的查询和历史记录的分页浏览接口，
 * 支持按 taskId 精确查询任务执行详情和按分页浏览所有任务历史。
 * </p>
 *
 * @author CyberFlow
 */
@RestController
@RequestMapping("/admin/crawler/task-history")
@RequiredArgsConstructor
public class TaskHistoryController {

    /** 任务历史业务服务 */
    private final TaskHistoryService taskHistoryService;

    /**
     * 根据任务 ID 查询任务执行状态。
     *
     * @param taskId 任务唯一标识
     * @return 任务状态信息，包含 task_id、state、result（rows_affected、error）等字段
     */
    @GetMapping("/status/{taskId}")
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:collect:start', 'crawler:order:start', 'crawler:site:config:crawl')")
    public Result<?> status(@PathVariable String taskId) {
        var task = taskHistoryService.getByTaskId(taskId);
        if (task == null) {
            return Result.fail("Task not found");
        }
        return Result.ok(Map.of(
            "task_id", task.getTaskId(),
            "state", task.getStatus(),
            "progress", task.getProgress() != null ? task.getProgress() : 0,
            "progress_message", task.getProgressMessage() != null ? task.getProgressMessage() : "等待任务执行",
            "result", Map.of(
                "rows_affected", task.getRowsAffected() != null ? task.getRowsAffected() : 0,
                "error", task.getErrorMsg() != null ? task.getErrorMsg() : ""
            )
        ));
    }

    /**
     * 分页查询任务历史列表。
     *
     * @param page 页码，默认 1
     * @param size 每页大小，默认 20
     * @return 分页结果，包含 records（任务列表）、total（总数）、current（当前页）等字段
     */
    @GetMapping("/tasks")
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:collect:start', 'crawler:order:start', 'crawler:site:config:crawl')")
    public Result<?> tasks(@RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(taskHistoryService.list(page, size));
    }
}
