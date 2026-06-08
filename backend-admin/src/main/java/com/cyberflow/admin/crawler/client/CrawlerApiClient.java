package com.cyberflow.admin.crawler.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class CrawlerApiClient {

    @Value("${cyberflow.crawler-api.base-url}")
    private String baseUrl;

    @Value("${cyberflow.crawler-api.internal-token}")
    private String internalToken;

    private final RestTemplate restTemplate = new RestTemplate();

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public Map<String, Object> triggerSiteCrawler(String username, String password) {
        log.info("Triggering site crawler: user={}", username);
        var headers = buildHeaders();
        var body = Map.of("username", username, "password", password);
        return post("/crawler/site/start", body);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public Map<String, Object> triggerSiteIndexCrawler(String username, String password) {
        log.info("Triggering site index crawler: user={}", username);
        var headers = buildHeaders();
        var body = Map.of("username", username, "password", password);
        return post("/crawler/site/collect", body);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public Map<String, Object> triggerOrderCrawler(String startTime, String endTime) {
        log.info("Triggering order crawler: {} ~ {}", startTime, endTime);
        var headers = buildHeaders();
        var body = Map.of("start_time", startTime, "end_time", endTime);
        return post("/crawler/order/start", body);
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Map<String, Object> getTaskStatus(String taskId) {
        var headers = buildHeaders();
        var url = baseUrl + "/crawler/status/" + taskId;
        var entity = new HttpEntity<>(headers);
        var resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        return resp.getBody();
    }

    private HttpHeaders buildHeaders() {
        var headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, String> body) {
        var entity = new HttpEntity<>(body, buildHeaders());
        return restTemplate.postForObject(baseUrl + path, entity, Map.class);
    }
}
