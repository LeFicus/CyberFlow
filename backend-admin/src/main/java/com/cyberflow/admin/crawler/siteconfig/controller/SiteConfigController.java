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
import java.util.regex.Pattern;

/**
 * 站点配置管理 REST 控制器。
 * <p>
 * 提供爬取站点配置的增删查功能，以及关联模板映射的管理和手动触发爬取。
 * 站点配置包含目标域名、站点类型、商品分类等基础信息，
 * 通过模板映射与选择器模板建立关联。
 * </p>
 *
 * <h3>权限列表</h3>
 * <ul>
 *   <li>crawler:site:config:list   - 查看站点配置列表和详情</li>
 *   <li>crawler:site:config:create - 创建站点配置</li>
 *   <li>crawler:site:config:crawl  - 手动触发站点爬取</li>
 *   <li>crawler:site:config:delete - 删除站点配置</li>
 * </ul>
 *
 * @author CyberFlow
 */
@RestController
@RequestMapping("/admin/crawler/site-config")
@RequiredArgsConstructor
public class SiteConfigController {

    private static final List<String> SUPPORTED_ENGINES = List.of(
        "shopify", "woocommerce", "bigcommerce", "opencart", "magento",
        "prestashop", "shopline", "ecwid", "wix", "squarespace", "custom"
    );
    private static final Pattern PROHIBITED_CATEGORY = Pattern.compile(
            "保健品|保健|食品|枪支|枪械|弹药|武器|毒品|烟酒|烟草|烟具|酒精|服装|服饰|成人",
        Pattern.CASE_INSENSITIVE
    );

    /** 站点配置业务服务 */
    private final SiteConfigService siteConfigService;

    /** 爬虫调度服务 */
    private final CrawlerService crawlerService;

    /**
     * 查询所有站点配置列表，按创建时间倒序。
     *
     * @return 站点配置列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('crawler:site:config:list')")
    public Result<?> list(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "10") int size) {
        return Result.ok(siteConfigService.page(page, size));
    }

    /**
     * 根据 ID 获取站点配置详情及其关联的模板映射。
     *
     * @param id 站点配置 ID
     * @return 包含 config（站点配置）和 mappings（模板映射列表）的 Map
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crawler:site:config:list')")
    public Result<?> get(@PathVariable Long id) {
        var config = siteConfigService.getById(id);
        var mappings = siteConfigService.getMappings(id);
        return Result.ok(Map.of("config", config, "mappings", mappings));
    }

    /**
     * 创建新的站点配置及其模板映射。
     *
     * @param body 请求体，包含 config（站点配置）和 mappings（模板映射列表）
     * @return 创建后的站点配置
     */
    @PostMapping
    @PreAuthorize("hasAuthority('crawler:site:config:create')")
    public Result<?> create(@RequestBody Map<String, Object> body) {
        CrawlSiteConfig config = parseConfig(body);
        if (isProhibitedCategory(config.getCategory())) {
            return Result.fail("该商品类目不允许使用：保健品、食品、枪支弹药、毒品、烟酒、服装及成人类目");
        }
        if (!SUPPORTED_ENGINES.contains(config.getType())) {
            return Result.fail("Unsupported product engine: " + config.getType());
        }
        List<SiteTemplateMapping> mappings = parseMappings(body);
        return Result.ok(siteConfigService.create(config, mappings));
    }

    /**
     * 更新已有站点配置及其模板映射。
     *
     * @param id   站点配置 ID
     * @param body 请求体，结构与创建接口一致
     * @return 更新后的站点配置
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('crawler:site:config:create')")
    public Result<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CrawlSiteConfig existing = siteConfigService.getById(id);
        if (existing == null) {
            return Result.fail("Site config not found");
        }
        CrawlSiteConfig config = parseConfig(body);
        if (isProhibitedCategory(config.getCategory())) {
            return Result.fail("该商品类目不允许使用：保健品、食品、枪支弹药、毒品、烟酒、服装及成人类目");
        }
        if (!SUPPORTED_ENGINES.contains(config.getType())) {
            return Result.fail("Unsupported product engine: " + config.getType());
        }
        config.setStatus(existing.getStatus());
        List<SiteTemplateMapping> mappings = parseMappings(body);
        return Result.ok(siteConfigService.update(id, config, mappings));
    }

    /**
     * 手动触发指定站点配置的商品爬取任务。
     *
     * @param id   站点配置 ID
     * @param body 请求体，可选包含 user_id 字段标识触发者
     * @return 包含 task_id 和状态信息的执行结果
     */
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

    /**
     * 删除指定 ID 的站点配置及其关联的模板映射。
     *
     * @param id 站点配置 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crawler:site:config:delete')")
    public Result<?> delete(@PathVariable Long id) {
        siteConfigService.delete(id);
        return Result.ok();
    }

    /**
     * 从请求体中解析站点配置对象。
     *
     * @param body 包含 "config" 键的请求体 Map
     * @return 解析后的 CrawlSiteConfig 实例
     */
    @SuppressWarnings("unchecked")
    private CrawlSiteConfig parseConfig(Map<String, Object> body) {
        Map<String, Object> configData = (Map<String, Object>) body.get("config");
        CrawlSiteConfig c = new CrawlSiteConfig();
        if (configData != null) {
            c.setDomain((String) configData.get("domain"));
            String type = String.valueOf(configData.getOrDefault("type", "shopify")).toLowerCase();
            c.setType(type);
            Object categoryValue = configData.get("category");
            String rawCategory = categoryValue == null ? "未知分类" : String.valueOf(categoryValue).trim();
            if (rawCategory.contains("|||")) {
                rawCategory = rawCategory.substring(rawCategory.indexOf("|||") + 3);
            }
            c.setCategory(rawCategory);
        }
        c.setStatus("active");
        return c;
    }

    /**
     * 从请求体中解析模板映射列表。
     *
     * @param body 包含 "mappings" 键的请求体 Map
     * @return 解析后的 SiteTemplateMapping 列表，可能为空列表
     */
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

    private boolean isProhibitedCategory(String category) {
        return category != null && PROHIBITED_CATEGORY.matcher(category).find();
    }
}
