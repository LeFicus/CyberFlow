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
import java.util.Set;

/**
 * 爬虫平台、策略和定时配置服务。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CrawlerConfigService {

    private static final String MASK = "******";

    /** Default US-market SEO brief used when the operator has not supplied a custom prompt. */
    public static final String DEFAULT_AI_PROMPT = """
            You are an English-language ecommerce brand strategist and SEO copywriter for the United States market.
            Create one complete new-store proposal for the product categories and custom category supplied by the user.
            This endpoint is called once per domain-generation attempt so each attempt must return exactly one proposal.
            Use the attempt number to vary the angle across these five scenarios: practical problem solving | premium quality and durability | gift and lifestyle discovery | beginner friendly family use | seasonal home project or outdoor use.

            Write for real US shoppers first. Use natural American English and specific search intent terms that a shopper would genuinely use for the supplied categories. Prefer useful descriptive phrases such as product type | use case | audience | material | season or shopping intent when supported by the inputs. If live web access is available you may use current US search-intent patterns as a reference. Never invent exact search volume | rankings | trend percentages or Google guarantees. Do not keyword stuff | repeat synonyms unnaturally | copy competitor text | make unsupported medical or performance claims | or use generic filler.

            site_title requirements:
            - English only and approximately 12 to 20 words.
            - Clearly describe the store positioning and include the most important product or use-case phrase naturally.
            - Make it distinctive and useful as a Google title and do not use commas.

            tag_line requirements:
            - English only and approximately 70 to 100 words.
            - Explain what the store sells | who it serves | the main customer problem or use case | and why the assortment is useful.
            - Make it persuasive but factual and suitable for a US ecommerce homepage.
            - Do not use commas. Use the vertical bar character | to separate clauses when helpful.

            domain requirements:
            - Generate a lowercase English .com domain that lets a shopper identify the store positioning at a glance.
            - Use two or three readable words or keyword tokens. Include the clearest main product category or use case plus one positioning token such as tools | garden | kids | outdoor | gifts | home or supplies when accurate.
            - Prefer a descriptive domain over an invented brand-only domain. Do not use trademarks | celebrity names | hyphens at the beginning or end | repeated hyphens | numbers unless essential | or keyword stuffing.
            - The domain must contain only lowercase letters | numbers | hyphens and dots and must be no longer than 63 characters before the top-level domain.

            Return only one valid JSON object with exactly these string fields: site_title | tag_line | domain. Do not return Markdown | explanations | multiple examples or extra fields.
            """;

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
            if ("revenue".equals(groupEntry.getKey())) {
                continue;
            }
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

    /** Dedicated write path for commission and income parameters. */
    @Transactional
    public Map<String, Object> updateRevenueConfig(Map<String, Object> body) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (MASK.equals(entry.getValue())) continue;
            upsertRuntime("revenue", entry.getKey(), entry.getValue(), false);
        }
        return getRevenueConfig();
    }

    public Map<String, Object> getAdminPlatform() {
        Map<String, Object> cfg = getRuntimeConfig(false);
        return group(cfg, "adminApi");
    }

    public Map<String, Object> getPaymentPlatform(String userGroup) {
        Map<String, Object> cfg = getRuntimeConfig(false);
        return group(cfg, "A".equalsIgnoreCase(userGroup) ? "paymentApiA" : "paymentApiB");
    }

    public Map<String, Object> getSiteStrategy() {
        Map<String, Object> cfg = getRuntimeConfig(false);
        return group(cfg, "siteStrategy");
    }

    public Map<String, Object> getOrderStrategy() {
        Map<String, Object> cfg = getRuntimeConfig(false);
        return group(cfg, "orderStrategy");
    }

    public Map<String, Object> getRevenueConfig() {
        Map<String, Object> cfg = getRuntimeConfig(false);
        return group(cfg, "revenue");
    }

    /** AI generation settings used by the new-site module. */
    public Map<String, Object> getAiGenerationConfig(boolean masked) {
        Map<String, Object> cfg = getRuntimeConfig(masked);
        Map<String, Object> ai = group(cfg, "aiGeneration");
        if (ai.get("prompt") == null || String.valueOf(ai.get("prompt")).isBlank()) {
            ai.put("prompt", DEFAULT_AI_PROMPT);
        }
        return ai;
    }

    /**
     * Update only the supported AI settings. The API key is masked on reads
     * and a masked value is intentionally ignored by updateRuntimeConfig().
     */
    @Transactional
    public Map<String, Object> updateAiGenerationConfig(Map<String, Object> body) {
        Set<String> allowed = Set.of("provider", "baseUrl", "apiKey", "model", "prompt", "maxAttempts");
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (!allowed.contains(entry.getKey())) continue;
            if ("maxAttempts".equals(entry.getKey())) {
                int attempts;
                try {
                    attempts = Integer.parseInt(String.valueOf(entry.getValue()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("AI 最大重试次数必须是数字");
                }
                if (attempts < 1 || attempts > 10) {
                    throw new IllegalArgumentException("AI 最大重试次数必须在 1 到 10 之间");
                }
                sanitized.put(entry.getKey(), attempts);
            } else {
                sanitized.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim());
            }
        }
        String baseUrl = String.valueOf(sanitized.getOrDefault("baseUrl", ""));
        if (!baseUrl.isBlank() && !(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("AI Base URL 必须以 http:// 或 https:// 开头");
        }
        updateRuntimeConfig(Map.of("aiGeneration", sanitized));
        return getAiGenerationConfig(true);
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
                log.info("Applying crawler schedule: taskType={}, enabled={}, cron={}",
                    config.getTaskType(), config.getEnabled(), config.getCronExpression());
                reschedule(config.getTaskType(), config.getCronExpression());
            }
        } catch (RuntimeException e) {
            log.warn("Unable to load crawler schedules from database; using application defaults", e);
        }
    }

    private void reschedule(String taskType, String cronExpression) {
        String triggerName = switch (taskType) {
            case "site_crawl" -> "siteCrawlTrigger";
            case "site_index" -> "siteIndexCrawlTrigger";
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
                log.info("Crawler schedule applied: taskType={}, cron={}", taskType, cronExpression);
            }
        } catch (SchedulerException | RuntimeException e) {
            // The stored cron still applies on the next application restart if runtime reschedule fails.
            log.warn("Unable to apply crawler schedule: taskType={}, cron={}", taskType, cronExpression, e);
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
        root.put("paymentApiA", paymentPlatformDefaults());
        root.put("paymentApiB", paymentPlatformDefaults());
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
        root.put("aiGeneration", new LinkedHashMap<>(Map.of(
            "provider", "deepseek",
            "baseUrl", "https://api.deepseek.com",
            "apiKey", "",
            "model", "deepseek-v4-flash",
            "prompt", DEFAULT_AI_PROMPT,
            "maxAttempts", 5
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
            "leaderConfig", new LinkedHashMap<>(Map.of(
                "A", "A-黄伟",
                "B", "B-李榕"
            )),
            "teacherMap", new LinkedHashMap<>(Map.of(
                "A-贺国君", "-hgj",
                "A-黄伟", "-hw",
                "A-邓志杭", "-dzh",
                "A-王志彬", "-wzb",
                "A-余嘉豪", "-yjh",
                "B-许晓龙", "-xxl",
                "B-许伟涛", "-xwt",
                "B-吴靖涛", "-wjt",
                "B-王华炜", "-whw"
            )),
            "userMergeMap", new LinkedHashMap<>(),
            "commissionTiers", new ArrayList<>(List.of(
                Map.of("threshold", 30000, "rate", 0.03),
                Map.of("threshold", 80000, "rate", 0.05),
                Map.of("threshold", "", "rate", 0.08)
            ))
        )));
        return root;
    }

    private LinkedHashMap<String, Object> paymentPlatformDefaults() {
        return new LinkedHashMap<>(Map.of(
            "baseUrl", "",
            "account", "",
            "password", "",
            "verifySsl", true
        ));
    }

    private String defaultCron(String taskType) {
        return switch (taskType) {
            case "site_index" -> "0 0 0 * * ?";
            case "site_crawl", "order_crawl" -> "0 0 */6 * * ?";
            default -> "0 0 */6 * * ?";
        };
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase();
        return normalized.contains("password") || normalized.contains("apikey")
                || normalized.contains("api_key") || normalized.contains("secret")
                || normalized.contains("token");
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
