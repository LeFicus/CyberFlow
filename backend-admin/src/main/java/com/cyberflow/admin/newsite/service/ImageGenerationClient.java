package com.cyberflow.admin.newsite.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal adapter for OpenAI-compatible POST /images/generations endpoints. */
@Component
@Slf4j
public class ImageGenerationClient {
    static final int MAX_IMAGE_BYTES = 25 * 1024 * 1024;
    private final RestClient restClient;

    public ImageGenerationClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(180_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public GeneratedImage generate(Request request) {
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            throw new IllegalStateException("未配置图像 AI API Key，请先保存图像 AI 配置");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", required(request.model(), "图像模型"));
        body.put("prompt", required(request.prompt(), "图像提示词"));
        body.put("n", 1);
        body.put("size", request.size());
        body.put("quality", request.quality());
        if (request.transparent()) body.put("background", "transparent");

        try {
            JsonNode response = restClient.post()
                    .uri(generationsUrl(request.baseUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + request.apiKey().trim())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode item = response == null ? null : response.path("data").path(0);
            if (item == null || item.isMissingNode()) throw new IllegalStateException("图像 AI 未返回图片");
            String encoded = item.path("b64_json").asText("").trim();
            byte[] bytes = encoded.isBlank() ? download(item.path("url").asText("")) : decode(encoded);
            if (bytes.length == 0) throw new IllegalStateException("图像 AI 返回了空图片");
            return new GeneratedImage(bytes, "image/png", item.path("revised_prompt").asText(""));
        } catch (RestClientResponseException e) {
            String requestId = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("x-request-id");
            log.warn("Image generation failed: status={}, requestId={}", e.getStatusCode(), requestId);
            throw new IllegalStateException("图像 AI 调用失败：HTTP " + e.getStatusCode().value()
                    + (requestId == null ? "" : "（request_id=" + requestId + "）"), e);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("图像 AI 请求超时或网络不可用", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("图像 AI 返回内容无法解析", e);
        }
    }

    static String generationsUrl(String baseUrl) {
        String value = required(baseUrl, "图像 AI Base URL").replaceAll("/+$", "");
        if (value.endsWith("/images/generations")) return value;
        return value + "/images/generations";
    }

    private static byte[] decode(String value) {
        if (value.length() > (MAX_IMAGE_BYTES * 4L / 3L) + 16_384L) {
            throw new IllegalStateException("图像 AI 返回文件超过 25MB 限制");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length > MAX_IMAGE_BYTES) throw new IllegalStateException("图像 AI 返回文件超过 25MB 限制");
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("图像 AI 返回了无效的 Base64 图片", e);
        }
    }

    private static byte[] download(String rawUrl) throws Exception {
        URI uri = URI.create(required(rawUrl, "图像下载地址"));
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("图像 AI 返回了不安全的下载地址");
        }
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "image/*");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) throw new IllegalStateException("下载生成图片失败：HTTP " + status);
        long length = connection.getContentLengthLong();
        if (length > MAX_IMAGE_BYTES) throw new IllegalStateException("图像 AI 返回文件超过 25MB 限制");
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int read; (read = input.read(buffer)) >= 0;) {
                total += read;
                if (total > MAX_IMAGE_BYTES) throw new IllegalStateException("图像 AI 返回文件超过 25MB 限制");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalStateException(label + "不能为空");
        return value.trim();
    }

    public record Request(String baseUrl, String apiKey, String model, String quality,
                          String prompt, String size, boolean transparent) {}
    public record GeneratedImage(byte[] bytes, String mimeType, String revisedPrompt) {}
}
