package com.cyberflow.admin.crawler.siteconfig.controller;

import com.cyberflow.admin.common.Result;
import com.cyberflow.admin.crawler.service.CrawlerService;
import com.cyberflow.admin.crawler.siteconfig.entity.CrawlSiteConfig;
import com.cyberflow.admin.crawler.siteconfig.entity.SiteTemplateMapping;
import com.cyberflow.admin.crawler.siteconfig.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/crawler/site-config")
@RequiredArgsConstructor
public class SiteConfigController {

    private final SiteConfigService siteConfigService;
    private final CrawlerService crawlerService;

    @GetMapping
    @PreAuthorize("hasAuthority('crawler:site:config:list')")
    public Result<?> list() {
        return Result.ok(siteConfigService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crawler:site:config:list')")
    public Result<?> get(@PathVariable Long id) {
        var config = siteConfigService.getById(id);
        var mappings = siteConfigService.getMappings(id);
        return Result.ok(Map.of("config", config, "mappings", mappings));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crawler:site:config:create')")
    public Result<?> create(@RequestBody Map<String, Object> body) {
        CrawlSiteConfig config = parseConfig(body);
        List<SiteTemplateMapping> mappings = parseMappings(body);
        return Result.ok(siteConfigService.create(config, mappings));
    }

    @PostMapping("/{id}/crawl")
    @PreAuthorize("hasAuthority('crawler:site:config:crawl')")
    public Result<?> triggerCrawl(@PathVariable Long id, @RequestBody Map<String, String> body) {
        CrawlSiteConfig config = siteConfigService.getById(id);
        if (config == null) return Result.fail("Site config not found");
        Long userId = Long.valueOf(body.getOrDefault("user_id", "0"));
        return Result.ok(crawlerService.triggerProductCrawl(
            config.getId(), config.getDomain(), config.getType(),
            config.getCategory(), userId
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crawler:site:config:delete')")
    public Result<?> delete(@PathVariable Long id) {
        siteConfigService.delete(id);
        return Result.ok();
    }

    @SuppressWarnings("unchecked")
    private CrawlSiteConfig parseConfig(Map<String, Object> body) {
        Map<String, Object> configData = (Map<String, Object>) body.get("config");
        CrawlSiteConfig c = new CrawlSiteConfig();
        if (configData != null) {
            c.setDomain((String) configData.get("domain"));
            c.setType((String) configData.get("type"));
            c.setCategory((String) configData.getOrDefault("category", "未知分类"));
        }
        c.setStatus("active");
        return c;
    }

    @SuppressWarnings("unchecked")
    private List<SiteTemplateMapping> parseMappings(Map<String, Object> body) {
        List<Map<String, Object>> rawList = (List<Map<String, Object>>) body.get("mappings");
        if (rawList == null) return new ArrayList<>();
        List<SiteTemplateMapping> result = new ArrayList<>();
        for (Map<String, Object> m : rawList) {
            SiteTemplateMapping sm = new SiteTemplateMapping();
            sm.setTemplateId(Long.valueOf(m.get("template_id").toString()));
            if (m.get("extra_selectors") != null) {
                sm.setExtraSelectors(m.get("extra_selectors").toString());
            }
            result.add(sm);
        }
        return result;
    }
}
