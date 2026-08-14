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

/**
 * 任务结果消息消费者。
 * <p>
 * 监听 RabbitMQ 的任务结果队列（task.result），当爬虫执行完毕后，
 * 接收返回的结果消息并更新数据库中的任务历史状态和爬取光标位置。
 * </p>
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>解析结果消息中的 task_id 和 status</li>
 *   <li>更新 task_history 表：状态、影响行数、耗时、结束时间、错误信息</li>
 *   <li>根据 new_cursor 更新 crawl_cursor 表的光标值</li>
 *   <li>在 task_history 中记录光标变更前后的值</li>
 * </ol>
 *
 * @author CyberFlow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskResultConsumer {

    /** 任务历史映射器，用于更新任务执行状态 */
    private final TaskHistoryMapper taskHistoryMapper;

    /** 爬取光标映射器，用于更新增量爬取的断点位置 */
    private final CrawlCursorMapper cursorMapper;

    /**
     * 处理爬虫任务执行结果。
     * <p>
     * 接收从 RabbitMQ 投递的结果消息，更新任务历史状态并同步爬取光标。
     * 结果消息中包含 task_id、status、rows_affected、duration_ms、error、new_cursor 等字段。
     * </p>
     *
     * @param result 爬虫执行结果消息，键值对结构
     */
    @SuppressWarnings("unchecked")
    @RabbitListener(queues = RabbitMQConfig.QUEUE_TASK_RESULT)
    public void handleResult(Map<String, Object> result) {
        String taskId = (String) result.get("task_id");
        String status = (String) result.get("status");
        log.info("Received task result: {} -> {}", taskId, status);

        // 更新 task_history 表中的任务状态
        TaskHistory history = taskHistoryMapper.selectOne(
            new LambdaQueryWrapper<TaskHistory>().eq(TaskHistory::getTaskId, taskId)
        );
        if (history != null) {
            // An operator pause must not be overwritten by a late result message.
            if ("PAUSED".equalsIgnoreCase(history.getStatus())
                    || "CANCELLED".equalsIgnoreCase(history.getStatus())) {
                log.info("Ignoring result for operator-controlled task {} in {} state", taskId, history.getStatus());
                return;
            }
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

        // 更新 crawl_cursor 表中的爬取光标
        Map<String, Object> newCursor = (Map<String, Object>) result.get("new_cursor");
        if (newCursor != null && history != null) {
            // 根据任务类型确定光标键名
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
                    // 从 new_cursor 中提取对应的光标值
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
                    // 记录任务执行后的光标值
                    history.setCursorAfter(cursorValue);
                    taskHistoryMapper.updateById(history);
                }
            }
        }
    }
}
