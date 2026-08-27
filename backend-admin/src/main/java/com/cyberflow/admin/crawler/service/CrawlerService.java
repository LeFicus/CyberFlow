package com.cyberflow.admin.crawler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyberflow.admin.crawler.messaging.TaskMessagePublisher;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.crawler.task.entity.CrawlCursor;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.mapper.CrawlCursorMapper;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 爬虫任务调度业务服务。
 * <p>
 * 负责接收前端或调度器的请求，将各类爬虫任务通过消息队列发布出去，
 * 并记录任务历史到数据库。支持站点爬取、站点索引、订单爬取和商品爬取四种任务类型。
 * </p>
 *
 * @author CyberFlow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    /** 任务消息发布器，负责将任务发送到 RabbitMQ */
    private final TaskMessagePublisher publisher;

    /** 任务历史服务，负责持久化任务执行记录 */
    private final TaskHistoryService taskHistoryService;

    /** 爬虫平台、策略和定时配置服务 */
    private final CrawlerConfigService crawlerConfigService;

    /** 爬取光标映射器 */
    private final CrawlCursorMapper cursorMapper;

    /**
     * 触发站点爬取任务。
     * <p>
     * 使用一天前的时间作为增量爬取光标（lastUpdatedAt），
     * 将任务发布到消息队列并记录到任务历史表。
     * </p>
     *
     * @param username 目标站点登录用户名
     * @param password 目标站点登录密码
     * @return 包含 task_id 和状态信息的 Map
     */
    public Map<String, Object> triggerSiteCrawler() {
        String lastUpdatedAt = cursorValue("site_crawler", LocalDateTime.now().minusDays(1).toString());
        String taskId = publisher.createTaskId();
        saveTaskHistory(taskId, "site_crawl", "manual", null);
        try {
            publisher.publishSiteCrawl(taskId, crawlerConfigService.getAdminPlatform(),
                    crawlerConfigService.getSiteStrategy(), lastUpdatedAt, "manual");
        } catch (RuntimeException ex) {
            taskHistoryService.markDispatchFailed(taskId, ex.getMessage());
            throw ex;
        }
        return Map.of("task_id", taskId, "status", "Task dispatched");
    }

    /**
     * 触发站点索引/收录任务。
     * <p>
     * 使用一天前的时间作为增量爬取光标（lastRecordedAt），
     * 将任务发布到消息队列并记录到任务历史表。
     * </p>
     *
     * @param username 目标站点登录用户名
     * @param password 目标站点登录密码
     * @return 包含 task_id 和状态信息的 Map
     */
    public Map<String, Object> triggerSiteIndexCrawler() {
        String lastRecordedAt = cursorValue("site_index_crawler", LocalDateTime.now().minusDays(1).toString());
        String taskId = publisher.createTaskId();
        saveTaskHistory(taskId, "site_index", "manual", null);
        try {
            publisher.publishSiteIndexCrawl(taskId, crawlerConfigService.getAdminPlatform(),
                    crawlerConfigService.getSiteStrategy(), lastRecordedAt, "manual");
        } catch (RuntimeException ex) {
            taskHistoryService.markDispatchFailed(taskId, ex.getMessage());
            throw ex;
        }
        return Map.of("task_id", taskId, "status", "Task dispatched");
    }

    /**
     * 触发订单爬取任务。
     * <p>
     * 以 maxOrderId="0" 作为增量光标从最初开始爬取，
     * 将任务发布到消息队列并记录到任务历史表。
     * </p>
     *
     * @param startTime 订单查询开始时间（当前未在消息中使用）
     * @param endTime   订单查询结束时间（当前未在消息中使用）
     * @return 包含 task_id 和状态信息的 Map
     */
    public Map<String, Object> triggerOrderCrawler(String rawUserGroup) {
        String userGroup = normalizeUserGroup(rawUserGroup);
        String maxOrderId = cursorValue(
            "order_crawler_" + userGroup,
            String.valueOf(crawlerConfigService.getOrderStrategy().getOrDefault("initialOrderId", "0"))
        );
        String taskId = publisher.createTaskId();
        saveTaskHistory(taskId, "order_crawl", "manual", "group-" + userGroup);
        try {
            publisher.publishOrderCrawl(taskId, crawlerConfigService.getPaymentPlatform(userGroup),
                    crawlerConfigService.getOrderStrategy(), maxOrderId, "manual", userGroup);
        } catch (RuntimeException ex) {
            taskHistoryService.markDispatchFailed(taskId, ex.getMessage());
            throw ex;
        }
        return Map.of("task_id", taskId, "user_group", userGroup, "status", "Task dispatched");
    }

    public Map<String, Object> triggerAllOrderCrawlers() {
        Map<String, Object> groupA = triggerOrderCrawler("A");
        Map<String, Object> groupB = triggerOrderCrawler("B");
        return Map.of("A", groupA, "B", groupB, "status", "Both group tasks dispatched");
    }

    /**
     * 根据任务 ID 查询任务执行状态。
     *
     * @param taskId 任务唯一标识
     * @return 任务状态 Map，包含 task_id、state、result（rows_affected、error）等字段；
     *         任务不存在时返回 state=UNKNOWN
     */
    public Map<String, Object> getTaskStatus(String taskId) {
        TaskHistory task = taskHistoryService.getByTaskId(taskId);
        if (task == null) {
            return Map.of("task_id", taskId, "state", "UNKNOWN");
        }
        return Map.of(
            "task_id", task.getTaskId(),
            "state", task.getStatus(),
            "progress", task.getProgress() != null ? task.getProgress() : 0,
            "progress_message", task.getProgressMessage() != null ? task.getProgressMessage() : "等待任务执行",
            "result", Map.of(
                "rows_affected", task.getRowsAffected() != null ? task.getRowsAffected() : 0,
                "error", task.getErrorMsg() != null ? task.getErrorMsg() : ""
            )
        );
    }

    /**
     * 触发指定站点配置的商品爬取任务。
     *
     * @param siteConfigId 站点配置 ID
     * @param domain       目标站点域名
     * @param type         站点类型，目前仅支持 shopify
     * @param category     商品分类
     * @param triggeredBy  触发者用户 ID
     * @return 包含 task_id 和状态信息的 Map
     */
    public Map<String, Object> triggerProductCrawl(Long siteConfigId, String domain, String type,
                                                   String category, String productRole, Long triggeredBy) {
        return triggerProductCrawl(siteConfigId, domain, type, category, productRole, triggeredBy, Map.of());
    }

    public Map<String, Object> triggerProductCrawl(Long siteConfigId, String domain, String type,
                                                   String category, String productRole, Long triggeredBy,
                                                   Map<String, Object> crawlOptions) {
        java.util.Set<String> optionKeys = java.util.Set.of(
            "max_product_price_usd", "require_description", "require_image", "currency"
        );
        if (!optionKeys.containsAll(crawlOptions.keySet())) {
            throw new IllegalArgumentException("Unsupported crawl_options keys");
        }
        for (String key : java.util.List.of("require_description", "require_image")) {
            if (crawlOptions.containsKey(key) && !(crawlOptions.get(key) instanceof Boolean)) {
                throw new IllegalArgumentException(key + " must be boolean");
            }
        }
        Object maximum = crawlOptions.get("max_product_price_usd");
        if (maximum != null && (!(maximum instanceof Number number)
                || !Double.isFinite(number.doubleValue()) || number.doubleValue() <= 0)) {
            throw new IllegalArgumentException("max_product_price_usd must be positive or null");
        }
        Object currency = crawlOptions.get("currency");
        if (currency != null && (!(currency instanceof String code) || !code.matches("(?i)([a-z]{3})?"))) {
            throw new IllegalArgumentException("currency must be an ISO code or empty");
        }
        if (type == null || !ProductEngines.SUPPORTED.contains(type.toLowerCase())) {
            return Map.of("status", "Rejected", "message", "Unsupported product crawl engine");
        }
        String taskId = publisher.createTaskId();
        saveTaskHistory(taskId, "product_crawl", "manual", String.valueOf(triggeredBy));
        try {
            publisher.publishProductCrawl(taskId, siteConfigId, domain, type, category, productRole, triggeredBy, crawlOptions);
        } catch (RuntimeException ex) {
            taskHistoryService.markDispatchFailed(taskId, ex.getMessage());
            throw ex;
        }
        return Map.of("task_id", taskId, "status", "Task dispatched");
    }

    /**
     * 保存任务历史记录到数据库，初始状态设为 PENDING。
     *
     * @param taskId      任务唯一标识
     * @param type        任务类型（site_crawl / site_index / order_crawl / product_crawl）
     * @param triggerType 触发方式（manual 手动 / cron 定时）
     * @param triggeredBy 触发者标识
     */
    private void saveTaskHistory(String taskId, String type, String triggerType, String triggeredBy) {
        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setType(type);
        history.setTriggerType(triggerType);
        history.setTriggeredBy(triggeredBy);
        history.setStatus("PENDING");
        taskHistoryService.save(history);
    }

    private String cursorValue(String cursorKey, String defaultValue) {
        CrawlCursor cursor = cursorMapper.selectOne(
            new LambdaQueryWrapper<CrawlCursor>().eq(CrawlCursor::getCursorKey, cursorKey)
        );
        return cursor != null ? cursor.getCursorValue() : defaultValue;
    }

    private static String normalizeUserGroup(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!java.util.Set.of("A", "B").contains(normalized)) {
            throw new IllegalArgumentException("userGroup must be A or B");
        }
        return normalized;
    }
}
