package com.cyberflow.admin.crawler.siteconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyberflow.admin.crawler.siteconfig.entity.CrawlSiteConfig;
import com.cyberflow.admin.crawler.siteconfig.entity.SiteTemplateMapping;
import com.cyberflow.admin.crawler.siteconfig.mapper.CrawlSiteConfigMapper;
import com.cyberflow.admin.crawler.siteconfig.mapper.SiteTemplateMappingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteConfigService {

    private final CrawlSiteConfigMapper configMapper;
    private final SiteTemplateMappingMapper mappingMapper;

    public List<CrawlSiteConfig> list() {
        return configMapper.selectList(
            new LambdaQueryWrapper<CrawlSiteConfig>().orderByDesc(CrawlSiteConfig::getCreatedAt)
        );
    }

    public CrawlSiteConfig getById(Long id) {
        return configMapper.selectById(id);
    }

    public List<SiteTemplateMapping> getMappings(Long siteConfigId) {
        return mappingMapper.selectList(
            new LambdaQueryWrapper<SiteTemplateMapping>()
                .eq(SiteTemplateMapping::getSiteConfigId, siteConfigId)
        );
    }

    @Transactional
    public CrawlSiteConfig create(CrawlSiteConfig config, List<SiteTemplateMapping> mappings) {
        configMapper.insert(config);
        for (SiteTemplateMapping m : mappings) {
            m.setSiteConfigId(config.getId());
            mappingMapper.insert(m);
        }
        return config;
    }

    @Transactional
    public CrawlSiteConfig update(Long id, CrawlSiteConfig config, List<SiteTemplateMapping> mappings) {
        config.setId(id);
        configMapper.updateById(config);
        mappingMapper.delete(
            new LambdaQueryWrapper<SiteTemplateMapping>()
                .eq(SiteTemplateMapping::getSiteConfigId, id)
        );
        for (SiteTemplateMapping m : mappings) {
            m.setSiteConfigId(id);
            mappingMapper.insert(m);
        }
        return configMapper.selectById(id);
    }

    public void delete(Long id) {
        mappingMapper.delete(
            new LambdaQueryWrapper<SiteTemplateMapping>()
                .eq(SiteTemplateMapping::getSiteConfigId, id)
        );
        configMapper.deleteById(id);
    }
}
