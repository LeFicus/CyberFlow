package com.cyberflow.admin.crawler.messaging;

import com.cyberflow.admin.crawler.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public String publishSiteCrawl(String username, String password, String lastUpdatedAt) {
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> message = Map.of(
            "task_id", taskId,
            "type", "site_crawl",
            "trigger", "cron",
            "timestamp", Instant.now().toString(),
            "payload", Map.of(
                "username", username,
                "password", password,
                "cursor", Map.of("last_updated_at", lastUpdatedAt)
            )
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_TASKS, RabbitMQConfig.RK_SITE, message);
        log.info("Published site crawl task: {}", taskId);
        return taskId;
    }

    public String publishSiteIndexCrawl(String username, String password, String lastRecordedAt) {
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> message = Map.of(
            "task_id", taskId,
            "type", "site_index",
            "trigger", "cron",
            "timestamp", Instant.now().toString(),
            "payload", Map.of(
                "username", username,
                "password", password,
                "cursor", Map.of("last_recorded_at", lastRecordedAt)
            )
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_TASKS, RabbitMQConfig.RK_SITE, message);
        log.info("Published site index crawl task: {}", taskId);
        return taskId;
    }

    public String publishOrderCrawl(String maxOrderId) {
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> message = Map.of(
            "task_id", taskId,
            "type", "order_crawl",
            "trigger", "cron",
            "timestamp", Instant.now().toString(),
            "payload", Map.of(
                "cursor", Map.of("max_order_id", maxOrderId)
            )
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_TASKS, RabbitMQConfig.RK_ORDER, message);
        log.info("Published order crawl task: {}", taskId);
        return taskId;
    }

    public String publishProductCrawl(Long siteConfigId, String domain, String type, String category, Long triggeredBy) {
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> message = Map.of(
            "task_id", taskId,
            "type", "product_crawl",
            "trigger", "manual",
            "triggered_by", String.valueOf(triggeredBy),
            "timestamp", Instant.now().toString(),
            "payload", Map.of(
                "site_config_id", siteConfigId,
                "domain", domain,
                "type", type,
                "category", category
            )
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_TASKS, RabbitMQConfig.RK_PRODUCT, message);
        log.info("Published product crawl task: {}", taskId);
        return taskId;
    }
}
