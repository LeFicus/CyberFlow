package com.cyberflow.admin.crawler.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyberflow.admin.crawler.config.RabbitMQConfig;
import com.cyberflow.admin.crawler.task.entity.CrawlCursor;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.mapper.CrawlCursorMapper;
import com.cyberflow.admin.crawler.task.mapper.TaskHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskResultConsumer {

    private final TaskHistoryMapper taskHistoryMapper;
    private final CrawlCursorMapper cursorMapper;

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMQConfig.QUEUE_TASK_RESULT)
    public void handleResult(Map<String, Object> result) {
        String taskId = (String) result.get("task_id");
        String status = (String) result.get("status");
        log.info("Received task result: {} -> {}", taskId, status);

        // Update task_history
        TaskHistory history = taskHistoryMapper.selectOne(
            new LambdaQueryWrapper<TaskHistory>().eq(TaskHistory::getTaskId, taskId)
        );
        if (history != null) {
            history.setStatus("success".equals(status) ? "SUCCESS" : "FAILED");
            history.setRowsAffected(result.get("rows_affected") != null
                ? ((Number) result.get("rows_affected")).intValue() : 0);
            history.setDurationMs(result.get("duration_ms") != null
                ? ((Number) result.get("duration_ms")).longValue() : null);
            history.setFinishedAt(LocalDateTime.now());
            if (result.get("error") != null) {
                history.setErrorMsg(result.get("error").toString());
            }
            taskHistoryMapper.updateById(history);
        }

        // Update crawl_cursor
        Map<String, Object> newCursor = (Map<String, Object>) result.get("new_cursor");
        if (newCursor != null && history != null) {
            String cursorKey = switch (history.getType()) {
                case "site_crawl" -> "site_crawler";
                case "site_index" -> "site_index_crawler";
                case "order_crawl" -> "order_crawler";
                default -> null;
            };
            if (cursorKey != null) {
                CrawlCursor cursor = cursorMapper.selectOne(
                    new LambdaQueryWrapper<CrawlCursor>().eq(CrawlCursor::getCursorKey, cursorKey)
                );
                if (cursor != null) {
                    String cursorValue = null;
                    if (newCursor.containsKey("max_order_id")) {
                        cursorValue = String.valueOf(newCursor.get("max_order_id"));
                    } else if (newCursor.containsKey("last_updated_at")) {
                        cursorValue = (String) newCursor.get("last_updated_at");
                    } else if (newCursor.containsKey("last_recorded_at")) {
                        cursorValue = (String) newCursor.get("last_recorded_at");
                    }
                    if (cursorValue != null) {
                        cursor.setCursorValue(cursorValue);
                        cursor.setLastSyncAt(LocalDateTime.now());
                        cursorMapper.updateById(cursor);
                    }
                    history.setCursorAfter(cursorValue);
                    taskHistoryMapper.updateById(history);
                }
            }
        }
    }
}
