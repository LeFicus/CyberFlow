package com.cyberflow.admin.crawler.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyberflow.admin.crawler.config.entity.CrawlerRuntimeConfig;
import com.cyberflow.admin.crawler.config.entity.CrawlerScheduleConfig;
import com.cyberflow.admin.crawler.config.mapper.CrawlerRuntimeConfigMapper;
import com.cyberflow.admin.crawler.config.mapper.CrawlerScheduleConfigMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 爬虫平台、策略和定时配置服务。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CrawlerConfigService {

    private static final String MASK = "******";

    private final CrawlerRuntimeConfigMapper runtimeConfigMapper;
    private final CrawlerScheduleConfigMapper scheduleConfigMapper;
    private final ObjectMapper objectMapper;
    private final Scheduler scheduler;

    public Map<String, Object> getRuntimeConfig(boolean masked) {
        Map<String, Object> result = defaultRuntimeConfig();
        List<CrawlerRuntimeConfig> rows = runtimeConfigMapper.selectList(
            new LambdaQueryWrapper<CrawlerRuntimeConfig>().orderByAsc(CrawlerRuntimeConfig::getConfigGroup)
        );
        for (CrawlerRuntimeConfig row : rows) {
            Map<String, Object> group = group(result, row.getConfigGroup());
            Object value = decodeValue(row.getConfigValue());
            if (masked && row.getSensitive() != null && row.getSensitive() == 1 && value != null && !String.valueOf(value).isBlank()) {
                value = MASK;
            }
            group.put(row.getConfigKey(), value);
        }
        return result;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateRuntimeConfig(Map<String, Object> body) {
        for (Map.Entry<String, Object> groupEntry : body.entrySet()) {
            if (!(groupEntry.getValue() instanceof Map<?, ?> rawGroup)) {
                continue;
            }
            for (Map.Entry<?, ?> entry : rawGroup.entrySet()) {
                String group = groupEntry.getKey();
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if (MASK.equals(value)) {
                    continue;
                }
                upsertRuntime(group, key, value, isSensitiveKey(key));
            }
        }
        return getRuntimeConfig(true);
    }

    public Map<String, Object> getAdminPlatform() {
        Map<String, Object> cfg = getRuntimeConfig(false);
        return group(cfg, "adminApi");
    }

    public Map<String, Object> getPaymentPlatform() {
        Map<String, Object> cfg = getRuntimeConfig(false);
        return group(cfg, "paymentApi");
    }

    public Map<String, Object> getSiteStrategy() {
        Map<String, Object> cfg = getRuntimeConfig(false);
        return group(cfg, "siteStrategy");
    }

    public Map<String, Object> getOrderStrategy() {
        Map<String, Object> cfg = getRuntimeConfig(false);
        return group(cfg, "orderStrategy");
    }

    public List<CrawlerScheduleConfig> listSchedules() {
        return scheduleConfigMapper.selectList(
            new LambdaQueryWrapper<CrawlerScheduleConfig>().orderByAsc(CrawlerScheduleConfig::getTaskType)
        );
    }

    public CrawlerScheduleConfig getSchedule(String taskType) {
        return scheduleConfigMapper.selectById(taskType);
    }

    public boolean isScheduleEnabled(String taskType) {
        CrawlerScheduleConfig config = getSchedule(taskType);
        return config != null && config.getEnabled() != null && config.getEnabled() == 1;
    }

    @Transactional
    public CrawlerScheduleConfig updateSchedule(String taskType, Map<String, Object> body) {
        CrawlerScheduleConfig config = getSchedule(taskType);
        if (config == null) {
            config = new CrawlerScheduleConfig();
            config.setTaskType(taskType);
            config.setCronExpression(defaultCron(taskType));
            config.setEnabled(1);
            scheduleConfigMapper.insert(config);
        }
        if (body.containsKey("cronExpression")) {
            config.setCronExpression(String.valueOf(body.get("cronExpression")));
        }
        if (body.containsKey("enabled")) {
            config.setEnabled(Boolean.TRUE.equals(body.get("enabled")) || "1".equals(String.valueOf(body.get("enabled"))) ? 1 : 0);
        }
        scheduleConfigMapper.updateById(config);
        reschedule(taskType, config.getCronExpression());
        return config;
    }

    public void markTriggered(String taskType) {
        CrawlerScheduleConfig config = getSchedule(taskType);
        if (config != null) {
            config.setLastTriggeredAt(LocalDateTime.now());
            scheduleConfigMapper.updateById(config);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applyStoredSchedules() {
        try {
            for (CrawlerScheduleConfig config : listSchedules()) {
                reschedule(config.getTaskType(), config.getCronExpression());
            }
        } catch (RuntimeException e) {
            log.warn("Unable to load crawler schedules from database; using application defaults", e);
        }
    }

    private void reschedule(String taskType, String cronExpression) {
        String triggerName = switch (taskType) {
            case "site_crawl" -> "siteCrawlTrigger";
            case "order_crawl" -> "orderCrawlTrigger";
            default -> null;
        };
        if (triggerName == null) {
            return;
        }
        try {
            TriggerKey triggerKey = TriggerKey.triggerKey(triggerName);
            if (scheduler.checkExists(triggerKey)) {
                Trigger current = scheduler.getTrigger(triggerKey);
                scheduler.rescheduleJob(triggerKey, TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(current.getJobKey())
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                    .build());
            }
        } catch (SchedulerException ignored) {
            // The stored cron still applies on the next application restart if runtime reschedule fails.
        }
    }

    private void upsertRuntime(String group, String key, Object value, boolean sensitive) {
        CrawlerRuntimeConfig existing = runtimeConfigMapper.selectOne(
            new LambdaQueryWrapper<CrawlerRuntimeConfig>()
                .eq(CrawlerRuntimeConfig::getConfigGroup, group)
                .eq(CrawlerRuntimeConfig::getConfigKey, key)
        );
        if (existing == null) {
            existing = new CrawlerRuntimeConfig();
            existing.setConfigGroup(group);
            existing.setConfigKey(key);
            existing.setSensitive(sensitive ? 1 : 0);
            existing.setConfigValue(encodeValue(value));
            runtimeConfigMapper.insert(existing);
        } else {
            existing.setConfigValue(encodeValue(value));
            existing.setSensitive(sensitive ? 1 : 0);
            runtimeConfigMapper.updateById(existing);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> group(Map<String, Object> root, String key) {
        return (Map<String, Object>) root.computeIfAbsent(key, ignored -> new LinkedHashMap<String, Object>());
    }

    private Map<String, Object> defaultRuntimeConfig() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("adminApi", new LinkedHashMap<>(Map.of(
            "baseUrl", "",
            "username", "",
            "password", "",
            "verifySsl", true
        )));
        root.put("paymentApi", new LinkedHashMap<>(Map.of(
            "baseUrl", "",
            "account", "",
            "password", "",
            "verifySsl", true
        )));
        root.put("siteStrategy", new LinkedHashMap<>(Map.of(
            "skipSiteCheck", true,
            "fetchAdminLoginUrl", false,
            "filterBuiltOnly", false,
            "pageSize", 100
        )));
        root.put("orderStrategy", new LinkedHashMap<>(Map.of(
            "filterCardNumberExclude", new ArrayList<>(List.of("400000******0000", "411111******1111", "411111111111")),
            "pageSize", 100,
            "initialOrderId", "0"
        )));
        root.put("revenue", new LinkedHashMap<>(Map.of(
            "exchangeRate", 6.73,
            "rateFactor", 0.42,
            "leaderCommissionRate", 0.02,
            "commissionTiers", new ArrayList<>(List.of(
                Map.of("threshold", 30000, "rate", 0.03),
                Map.of("threshold", 80000, "rate", 0.05),
                Map.of("threshold", "", "rate", 0.08)
            ))
        )));
        return root;
    }

    private String defaultCron(String taskType) {
        return "order_crawl".equals(taskType) ? "0 0 3 * * ?" : "0 0 2 * * ?";
    }

    private boolean isSensitiveKey(String key) {
        return key.toLowerCase().contains("password");
    }

    private String encodeValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private Object decodeValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<Object>() {});
            } catch (JsonProcessingException ignored) {
                return value;
            }
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Boolean.parseBoolean(trimmed);
        }
        return value;
    }
}
