package com.cyberflow.admin.crawler.service;

import com.cyberflow.admin.crawler.messaging.TaskMessagePublisher;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final TaskMessagePublisher publisher;
    private final TaskHistoryService taskHistoryService;

    public Map<String, Object> triggerSiteCrawler(String username, String password) {
        String lastUpdatedAt = LocalDateTime.now().minusDays(1).toString();
        String taskId = publisher.publishSiteCrawl(username, password, lastUpdatedAt);
        saveTaskHistory(taskId, "site_crawl", "manual", null);
        return Map.of("task_id", taskId, "status", "Task dispatched");
    }

    public Map<String, Object> triggerSiteIndexCrawler(String username, String password) {
        String lastRecordedAt = LocalDateTime.now().minusDays(1).toString();
        String taskId = publisher.publishSiteIndexCrawl(username, password, lastRecordedAt);
        saveTaskHistory(taskId, "site_index", "manual", null);
        return Map.of("task_id", taskId, "status", "Task dispatched");
    }

    public Map<String, Object> triggerOrderCrawler(String startTime, String endTime) {
        String maxOrderId = "0";
        String taskId = publisher.publishOrderCrawl(maxOrderId);
        saveTaskHistory(taskId, "order_crawl", "manual", null);
        return Map.of("task_id", taskId, "status", "Task dispatched");
    }

    public Map<String, Object> getTaskStatus(String taskId) {
        TaskHistory task = taskHistoryService.getByTaskId(taskId);
        if (task == null) {
            return Map.of("task_id", taskId, "state", "UNKNOWN");
        }
        return Map.of(
            "task_id", task.getTaskId(),
            "state", task.getStatus(),
            "result", Map.of(
                "rows_affected", task.getRowsAffected() != null ? task.getRowsAffected() : 0,
                "error", task.getErrorMsg() != null ? task.getErrorMsg() : ""
            )
        );
    }

    public Map<String, Object> triggerProductCrawl(Long siteConfigId, String domain, String type, String category, Long triggeredBy) {
        String taskId = publisher.publishProductCrawl(siteConfigId, domain, type, category, triggeredBy);
        saveTaskHistory(taskId, "product_crawl", "manual", String.valueOf(triggeredBy));
        return Map.of("task_id", taskId, "status", "Task dispatched");
    }

    private void saveTaskHistory(String taskId, String type, String triggerType, String triggeredBy) {
        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setType(type);
        history.setTriggerType(triggerType);
        history.setTriggeredBy(triggeredBy);
        history.setStatus("PENDING");
        taskHistoryService.save(history);
    }
}
