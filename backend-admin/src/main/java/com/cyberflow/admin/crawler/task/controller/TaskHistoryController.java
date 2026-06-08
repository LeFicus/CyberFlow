package com.cyberflow.admin.crawler.task.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/crawler")
@RequiredArgsConstructor
public class TaskHistoryController {

    private final TaskHistoryService taskHistoryService;

    @GetMapping("/status/{taskId}")
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:collect:start', 'crawler:order:start')")
    public Result<?> status(@PathVariable String taskId) {
        var task = taskHistoryService.getByTaskId(taskId);
        if (task == null) {
            return Result.fail("Task not found");
        }
        return Result.ok(Map.of(
            "task_id", task.getTaskId(),
            "state", task.getStatus(),
            "result", Map.of(
                "rows_affected", task.getRowsAffected() != null ? task.getRowsAffected() : 0,
                "error", task.getErrorMsg() != null ? task.getErrorMsg() : ""
            )
        ));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyAuthority('crawler:site:start', 'crawler:collect:start', 'crawler:order:start')")
    public Result<?> tasks(@RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(taskHistoryService.list(page, size));
    }
}
