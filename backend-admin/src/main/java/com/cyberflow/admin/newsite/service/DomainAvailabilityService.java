package com.cyberflow.admin.newsite.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;

/**
 * Checks registration state through RDAP, the standard WHOIS successor.
 * A 404 means the registry has no registration object and is treated as
 * potentially purchasable; a final registrar checkout must still confirm it.
 */
@Service
public class DomainAvailabilityService {

    private final RestClient restClient;

    @Value("${cyberflow.site-generation.rdap-url:https://rdap.org/domain/{domain}}")
    private String rdapUrl;

    public DomainAvailabilityService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public DomainCheckResult check(String domain) {
        try {
            String url = rdapUrl.replace("{domain}", domain);
            return restClient.get().uri(URI.create(url))
                    .header("Accept", "application/rdap+json, application/json")
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) return new DomainCheckResult("available", "rdap", "未找到注册记录");
                        if (status >= 200 && status < 300) return new DomainCheckResult("taken", "rdap", "域名已注册");
                        return new DomainCheckResult("unknown", "rdap", "查询服务返回 HTTP " + status);
                    });
        } catch (Exception e) {
            return new DomainCheckResult("unknown", "rdap", "域名查询失败：" + e.getMessage());
        }
    }

    public record DomainCheckResult(String status, String provider, String message) {
        public boolean available() {
            return "available".equals(status);
        }
    }
}
