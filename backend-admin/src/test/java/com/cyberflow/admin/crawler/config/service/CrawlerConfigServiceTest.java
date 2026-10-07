package com.cyberflow.admin.crawler.config.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CrawlerConfigServiceTest {

    @Test
    void cannotCreateOrUpdateSiteHealthSchedule() {
        CrawlerConfigService service = new CrawlerConfigService(null, null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateSchedule("site_health", Map.of("enabled", true)));
    }
}
