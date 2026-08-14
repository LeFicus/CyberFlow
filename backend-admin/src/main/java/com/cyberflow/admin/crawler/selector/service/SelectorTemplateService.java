package com.cyberflow.admin.crawler.selector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.crawler.selector.entity.SelectorTemplate;
import com.cyberflow.admin.crawler.selector.mapper.SelectorTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 选择器模板业务服务。
 * <p>
 * 提供选择器模板的增删改查及克隆等核心业务逻辑。
 * 系统模板（isSystem=1）受保护不可删除。
 * </p>
 *
 * @author CyberFlow
 */
@Service
@RequiredArgsConstructor
public class SelectorTemplateService {

    private static final String DEFAULT_PRICE_REGEX = "[\\d.,]+";

    /** 选择器模板数据访问映射器 */
    private final SelectorTemplateMapper mapper;

    /**
     * 查询模板列表，支持按平台过滤并按平台和名称排序。
     *
     * @param platform 平台标识，为 null 或空时返回所有模板
     * @return 模板实体列表
     */
    public List<SelectorTemplate> list(String platform) {
        LambdaQueryWrapper<SelectorTemplate> wrapper = new LambdaQueryWrapper<>();
        if (platform != null && !platform.isEmpty()) {
            wrapper.eq(SelectorTemplate::getPlatform, platform);
        }
        wrapper.orderByAsc(SelectorTemplate::getPlatform).orderByAsc(SelectorTemplate::getName);
        return mapper.selectList(wrapper);
    }

    public Page<SelectorTemplate> page(String platform, int pageNum, int pageSize) {
        LambdaQueryWrapper<SelectorTemplate> wrapper = new LambdaQueryWrapper<>();
        if (platform != null && !platform.isBlank()) {
            wrapper.eq(SelectorTemplate::getPlatform, platform);
        }
        wrapper.orderByAsc(SelectorTemplate::getPlatform).orderByAsc(SelectorTemplate::getName);
        return mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 根据 ID 查询单个模板。
     *
     * @param id 模板主键 ID
     * @return 模板实体，未找到时返回 null
     */
    public SelectorTemplate getById(Long id) {
        return mapper.selectById(id);
    }

    /**
     * 创建新的选择器模板。
     *
     * @param template 模板实体
     * @return 创建后的模板（含自增 ID）
     */
    public SelectorTemplate create(SelectorTemplate template) {
        template.setPriceRegex(normalizePriceRegex(template.getPriceRegex()));
        mapper.insert(template);
        return template;
    }

    /**
     * 更新指定 ID 的选择器模板。
     *
     * @param id       模板主键 ID
     * @param template 更新后的模板数据
     * @return 更新后的模板实体（从数据库重新查询）
     */
    public SelectorTemplate update(Long id, SelectorTemplate template) {
        template.setId(id);
        template.setPriceRegex(normalizePriceRegex(template.getPriceRegex()));
        mapper.updateById(template);
        return mapper.selectById(id);
    }

    /**
     * 删除指定 ID 的选择器模板。
     * <p>
     * 系统模板（isSystem=1）不允许删除，会抛出运行时异常。
     * </p>
     *
     * @param id 模板主键 ID
     * @throws RuntimeException 尝试删除系统模板时抛出
     */
    public void delete(Long id) {
        SelectorTemplate template = mapper.selectById(id);
        if (template != null && template.getIsSystem() != null && template.getIsSystem() == 1) {
            throw new RuntimeException("Cannot delete system template");
        }
        mapper.deleteById(id);
    }

    /**
     * 克隆指定 ID 的选择器模板。
     * <p>
     * 创建原模板的完整副本，名称为 "原名称 (copy)"，isSystem 标记为 0（用户模板）。
     * </p>
     *
     * @param id 原模板主键 ID
     * @return 克隆后的新模板实体
     * @throws RuntimeException 原模板不存在时抛出
     */
    public SelectorTemplate clone(Long id) {
        SelectorTemplate original = mapper.selectById(id);
        if (original == null) throw new RuntimeException("Template not found");
        SelectorTemplate copy = new SelectorTemplate();
        copy.setName(original.getName() + " (copy)");
        copy.setPlatform(original.getPlatform());
        copy.setTitleSelector(original.getTitleSelector());
        copy.setPriceSelector(original.getPriceSelector());
        copy.setPriceRegex(normalizePriceRegex(original.getPriceRegex()));
        copy.setDescriptionSelector(original.getDescriptionSelector());
        copy.setImagesSelector(original.getImagesSelector());
        copy.setCurrency(original.getCurrency());
        copy.setBreadcrumbLinksSelector(original.getBreadcrumbLinksSelector());
        copy.setBreadcrumbLastSelector(original.getBreadcrumbLastSelector());
        copy.setSiteMapSelector(original.getSiteMapSelector());
        copy.setIsSystem(0);
        mapper.insert(copy);
        return copy;
    }

    /** Prevent malformed custom patterns from making every parsed price zero. */
    private String normalizePriceRegex(String value) {
        if (value == null || value.isBlank()) return null;
        String pattern = value.trim();
        try {
            Pattern compiled = Pattern.compile(pattern);
            var moneyMatch = compiled.matcher("$1,234.56");
            var integerMatch = compiled.matcher("1234");
            if ((moneyMatch.find() && moneyMatch.group().chars().anyMatch(Character::isDigit))
                    || (integerMatch.find() && integerMatch.group().chars().anyMatch(Character::isDigit))) {
                return pattern;
            }
        } catch (PatternSyntaxException ignored) {
            // Fall through to the known-safe default.
        }
        return DEFAULT_PRICE_REGEX;
    }
}
