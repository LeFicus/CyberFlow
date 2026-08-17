package com.cyberflow.admin.crawler.task.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
    @PreAuthorize("hasAuthority('crawler:history:view')")
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
    @PreAuthorize("hasAnyAuthority('crawler:history:view', 'crawler:task:control')")
    public Result<?> tasks(@RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int size,
                           @RequestParam(required = false) String type) {
        return Result.ok(taskHistoryService.list(page, size, type));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('crawler:history:view')")
    public Result<?> summary() {
        return Result.ok(taskHistoryService.summary());
    }

    /** Pause a pending/running task; consumers suspend cooperatively. */
    @PostMapping("/tasks/{taskId}/pause")
    @PreAuthorize("hasAuthority('crawler:task:control')")
    public Result<?> pause(@PathVariable String taskId) {
        return Result.ok(taskHistoryService.pause(taskId));
    }

    /** Resume a task previously paused by the operator. */
    @PostMapping("/tasks/{taskId}/resume")
    @PreAuthorize("hasAuthority('crawler:task:control')")
    public Result<?> resume(@PathVariable String taskId) {
        return Result.ok(taskHistoryService.resume(taskId));
    }

    /** Delete a task history record and request cancellation from an active consumer. */
    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('crawler:task:delete')")
    public Result<?> delete(@PathVariable String taskId) {
        taskHistoryService.delete(taskId);
        return Result.ok();
    }

    /** Incrementally query a bounded section of a task log. */
    @GetMapping("/tasks/{taskId}/log")
    @PreAuthorize("hasAuthority('crawler:history:view')")
    public Result<?> taskLog(@PathVariable String taskId,
                             @RequestParam(defaultValue = "0") int offset,
                             @RequestParam(defaultValue = "65536") int limit,
                             @RequestParam(required = false) Integer tail) {
        int safeLimit = Math.max(1, Math.min(limit, 65536));
        Map<String, Object> row = tail == null
                ? taskHistoryService.getLogChunk(taskId, Math.max(0, offset), safeLimit)
                : taskHistoryService.getLogTail(taskId, Math.max(1, Math.min(tail, 200000)));
        if (row == null) {
            return Result.fail("Task not found");
        }
        String chunk = row.get("chunk") == null ? "" : String.valueOf(row.get("chunk"));
        int totalLength = ((Number) row.getOrDefault("totalLength", 0)).intValue();
        int startOffset = tail == null ? Math.max(0, offset) : Math.max(0, totalLength - chunk.length());
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", row.get("taskId"));
        result.put("status", row.get("status"));
        result.put("chunk", chunk);
        result.put("nextOffset", Math.min(totalLength, startOffset + chunk.length()));
        result.put("totalLength", totalLength);
        result.put("truncated", startOffset > 0);
        result.put("hasMore", startOffset + chunk.length() < totalLength);
        return Result.ok(result);
    }

    /** Download the complete log without inserting it into the live viewer DOM. */
    @GetMapping("/tasks/{taskId}/log/download")
    @PreAuthorize("hasAuthority('crawler:history:view')")
    public void downloadTaskLog(@PathVariable String taskId, HttpServletResponse response) throws IOException {
        var task = taskHistoryService.getByTaskIdWithLog(taskId);
        if (task == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Task not found");
            return;
        }
        String prefix = switch (task.getType()) {
            case "site_crawl" -> "site-crawl";
            case "site_index" -> "site-index";
            case "order_crawl" -> "order-crawl";
            case "product_crawl" -> "product-crawl";
            default -> "crawl-task";
        };
        String fileName = prefix + "-" + taskId + ".log";
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" +
                URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        response.getWriter().write(task.getCrawlLog() == null ? "" : task.getCrawlLog());
    }
}
