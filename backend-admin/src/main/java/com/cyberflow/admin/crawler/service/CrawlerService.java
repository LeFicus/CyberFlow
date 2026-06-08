package com.cyberflow.admin.crawler.service;

import com.cyberflow.admin.crawler.client.CrawlerApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final CrawlerApiClient apiClient;

    // 内存追踪最近任务
    private final Map<String, Map<String, Object>> taskTracker = new ConcurrentHashMap<>();

    public Map<String, Object> triggerSiteCrawler(String username, String password) {
        var result = apiClient.triggerSiteCrawler(username, password);
        trackTask(result, "site", username);
        return result;
    }

    public Map<String, Object> triggerSiteIndexCrawler(String username, String password) {
        var result = apiClient.triggerSiteIndexCrawler(username, password);
        trackTask(result, "site_index", username);
        return result;
    }

    public Map<String, Object> triggerOrderCrawler(String startTime, String endTime) {
        var result = apiClient.triggerOrderCrawler(startTime, endTime);
        trackTask(result, "order", "");
        return result;
    }

    public Map<String, Object> getTaskStatus(String taskId) {
        var status = apiClient.getTaskStatus(taskId);
        // 更新追踪
        if (taskTracker.containsKey(taskId)) {
            var tracked = taskTracker.get(taskId);
            tracked.put("state", status.get("state"));
            tracked.put("result", status.get("result"));
        }
        return status;
    }

    public Map<String, Map<String, Object>> getRecentTasks() {
        return Map.copyOf(taskTracker);
    }

    private void trackTask(Map<String, Object> dispatchResult, String type, String user) {
        String taskId = (String) dispatchResult.get("task_id");
        if (taskId != null) {
            var info = new ConcurrentHashMap<String, Object>();
            info.put("task_id", taskId);
            info.put("type", type);
            info.put("user", user);
            info.put("state", "PENDING");
            info.put("created_at", System.currentTimeMillis());
            taskTracker.put(taskId, info);
        }
    }
}
