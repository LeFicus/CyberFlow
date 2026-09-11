package com.cyberflow.admin.crawler.config.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiteGroupConfigurationTest {
    @Test
    void acceptsDatabaseDefinedGroupNamesWithoutAnAbcdAllowList() {
        assertEquals("业务一组", CrawlerConfigService.normalizeUserGroup(" 业务一组 "));
        assertEquals("TEAM_7", CrawlerConfigService.normalizeUserGroup("TEAM_7"));
        assertThrows(IllegalArgumentException.class,
                () -> CrawlerConfigService.normalizeUserGroup("bad group"));
    }
}
