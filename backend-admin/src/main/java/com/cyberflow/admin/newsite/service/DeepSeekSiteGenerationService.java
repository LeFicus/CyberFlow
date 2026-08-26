package com.cyberflow.admin.newsite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Calls the configured OpenAI-compatible chat completion endpoint. */
@Service
@RequiredArgsConstructor
public class DeepSeekSiteGenerationService {

    private final ObjectMapper objectMapper;
    private final CrawlerConfigService crawlerConfigService;

    @Value("${cyberflow.site-generation.deepseek-api-key:}")
    private String apiKey;

    @Value("${cyberflow.site-generation.deepseek-base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${cyberflow.site-generation.deepseek-model:deepseek-v4-flash}")
    private String model;

    @Value("${cyberflow.site-generation.prompt:}")
    private String configuredPrompt;

    private final RestClient restClient = createRestClient();

    public int maxAttempts() {
        Object value = crawlerConfigService.getAiGenerationConfig(false).get("maxAttempts");
        try {
            return Math.max(1, Math.min(10, Integer.parseInt(String.valueOf(value))));
        } catch (Exception ignored) {
            return 5;
        }
    }

    public GeneratedSite generate(String customCategory, String mainProductCategory,
                                  String supplementProductCategory, int attempt) {
        Map<String, Object> config = crawlerConfigService.getAiGenerationConfig(false);
        String configuredApiKey = text(config.get("apiKey"));
        String effectiveApiKey = configuredApiKey.isBlank() ? apiKey : configuredApiKey;
        String effectiveBaseUrl = firstNonBlank(text(config.get("baseUrl")), baseUrl);
        String effectiveModel = firstNonBlank(text(config.get("model")), model);
        String effectivePrompt = text(config.get("prompt"));
        String provider = text(config.get("provider")).toLowerCase(Locale.ROOT);
        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            throw new IllegalStateException("未配置 AI API Key，请先在新站点管理中保存 AI 配置");
        }

        String systemPrompt = effectivePrompt.isBlank() ? (configuredPrompt == null || configuredPrompt.isBlank()
                ? CrawlerConfigService.DEFAULT_AI_PROMPT
                : configuredPrompt) : effectivePrompt;
        String userPrompt = "请根据以下信息生成一个新电商站点：\n"
                + "自定义分类：" + customCategory + "\n"
                + "主产品分类：" + mainProductCategory + "\n"
                + "副产品分类：" + supplementProductCategory + "\n"
                + "这是第 " + attempt + " 次候选生成，请尽量生成与前次不同的英文域名。\n"
                + "输出 JSON，例如：{\"site_title\":\"...\",\"tag_line\":\"...\",\"domain\":\"example.com\"}";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", effectiveModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", 0.9);
        body.put("max_tokens", 600);
        body.put("stream", false);
        // DeepSeek V4 enables reasoning by default. The generator needs the
        // final JSON content, not a reasoning trace, so use non-thinking mode.
        if ("deepseek".equals(provider)) {
            body.put("thinking", Map.of("type", "disabled"));
        }

        try {
            JsonNode response = restClient.post()
                    .uri(completionsUrl(effectiveBaseUrl))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + effectiveApiKey.trim())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode message = response == null ? null : response.path("choices").path(0).path("message");
            String content = extractContent(message);
            if (content.isBlank()) {
                String finishReason = response == null ? "" : response.path("choices").path(0)
                        .path("finish_reason").asText("");
                throw new IllegalStateException("AI 服务未返回有效内容"
                        + (finishReason.isBlank() ? "" : "（finish_reason=" + finishReason + "）"));
            }

            JsonNode json = objectMapper.readTree(stripJsonFence(content));
            String title = required(json, "site_title");
            String tagLine = required(json, "tag_line");
            String domain = required(json, "domain").toLowerCase().trim();
            return new GeneratedSite(title, tagLine, domain);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("AI 服务调用失败：HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("AI 服务请求超时或网络不可用", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("AI 服务返回内容无法解析", e);
        }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalStateException("AI 服务返回缺少字段：" + field);
        return value;
    }

    private static String extractContent(JsonNode message) {
        if (message == null || message.isMissingNode()) return "";
        JsonNode content = message.path("content");
        if (content.isTextual()) return content.asText("").trim();
        if (content.isArray()) {
            StringBuilder result = new StringBuilder();
            content.forEach(item -> {
                if (item.isTextual()) {
                    result.append(item.asText());
                } else if (item.isObject()) {
                    String text = item.path("text").asText("");
                    if (!text.isBlank()) result.append(text);
                }
            });
            return result.toString().trim();
        }
        return "";
    }

    private static String stripJsonFence(String content) {
        String value = content.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        return value.trim();
    }

    private static String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return RestClient.builder().requestFactory(factory).build();
    }

    private static String completionsUrl(String value) {
        String url = trimTrailingSlash(value);
        return url.endsWith("/chat/completions") ? url : url + "/chat/completions";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    public record GeneratedSite(String siteTitle, String tagLine, String domain) {
    }
}
