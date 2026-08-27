package com.cyberflow.admin.crawler.siteconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.crawler.siteconfig.entity.CrawlSiteConfig;
import com.cyberflow.admin.crawler.siteconfig.entity.SiteTemplateMapping;
import com.cyberflow.admin.crawler.siteconfig.mapper.CrawlSiteConfigMapper;
import com.cyberflow.admin.crawler.siteconfig.mapper.SiteTemplateMappingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 站点配置业务服务。
 * <p>
 * 管理爬取站点配置及其关联的模板映射的增删改查操作。
 * 创建和更新操作使用事务保证站点配置与模板映射的原子性。
 * 删除站点配置时会同时删除所有关联的模板映射。
 * </p>
 *
 * @author CyberFlow
 */
@Service
@RequiredArgsConstructor
public class SiteConfigService {

    /** 站点配置数据访问接口 */
    private final CrawlSiteConfigMapper configMapper;

    /** 站点-模板映射数据访问接口 */
    private final SiteTemplateMappingMapper mappingMapper;
    private final com.cyberflow.admin.category.CustomCategoryService categories;

    /**
     * 查询所有站点配置，按创建时间倒序排列。
     *
     * @return 站点配置实体列表
     */
    public List<CrawlSiteConfig> list() {
        return configMapper.selectList(
            new LambdaQueryWrapper<CrawlSiteConfig>().orderByDesc(CrawlSiteConfig::getCreatedAt)
        );
    }

    public Page<CrawlSiteConfig> page(int pageNum, int pageSize) {
        return configMapper.selectPage(
            new Page<>(pageNum, pageSize),
            new LambdaQueryWrapper<CrawlSiteConfig>().orderByDesc(CrawlSiteConfig::getCreatedAt)
        );
    }

    /**
     * 根据 ID 查询站点配置。
     *
     * @param id 站点配置主键 ID
     * @return 站点配置实体，未找到时返回 null
     */
    public CrawlSiteConfig getById(Long id) {
        return configMapper.selectById(id);
    }

    /**
     * 查询指定站点配置关联的所有模板映射。
     *
     * @param siteConfigId 站点配置 ID
     * @return 模板映射实体列表
     */
    public List<SiteTemplateMapping> getMappings(Long siteConfigId) {
        return mappingMapper.selectList(
            new LambdaQueryWrapper<SiteTemplateMapping>()
                .eq(SiteTemplateMapping::getSiteConfigId, siteConfigId)
        );
    }

    /**
     * 创建站点配置及其模板映射（事务操作）。
     * <p>
     * 先插入站点配置获取自增 ID，再批量插入模板映射，
     * 若任一操作失败则整体回滚。
     * </p>
     *
     * @param config   站点配置实体
     * @param mappings 模板映射实体列表
     * @return 创建后的站点配置（含自增 ID）
     */
    @Transactional
    public CrawlSiteConfig create(CrawlSiteConfig config, List<SiteTemplateMapping> mappings) {
        categories.validateSelection(config.getCategory(), null);
        configMapper.insert(config);
        for (SiteTemplateMapping m : mappings) {
            m.setSiteConfigId(config.getId());
            mappingMapper.insert(m);
        }
        return config;
    }

    /**
     * 更新站点配置及其模板映射（事务操作）。
     * <p>
     * 采用"先删后插"策略，删除原有映射后重新插入新的映射列表。
     * </p>
     *
     * @param id       站点配置主键 ID
     * @param config   更新后的站点配置数据
     * @param mappings 更新后的模板映射列表
     * @return 更新后的站点配置（从数据库重新查询）
     */
    @Transactional
    public CrawlSiteConfig update(Long id, CrawlSiteConfig config, List<SiteTemplateMapping> mappings) {
        CrawlSiteConfig previous = configMapper.selectById(id);
        if (previous == null) throw new IllegalArgumentException("站点配置不存在");
        categories.validateSelection(config.getCategory(), previous.getCategory());
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

    /**
     * 删除站点配置及其所有关联的模板映射。
     * <p>
     * 先删除模板映射再删除站点配置，确保数据完整性。
     * </p>
     *
     * @param id 站点配置主键 ID
     */
    public void delete(Long id) {
        mappingMapper.delete(
            new LambdaQueryWrapper<SiteTemplateMapping>()
                .eq(SiteTemplateMapping::getSiteConfigId, id)
        );
        configMapper.deleteById(id);
    }
}
