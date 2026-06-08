package com.cyberflow.admin.crawler.selector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyberflow.admin.crawler.selector.entity.SelectorTemplate;
import com.cyberflow.admin.crawler.selector.mapper.SelectorTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SelectorTemplateService {

    private final SelectorTemplateMapper mapper;

    public List<SelectorTemplate> list(String platform) {
        LambdaQueryWrapper<SelectorTemplate> wrapper = new LambdaQueryWrapper<>();
        if (platform != null && !platform.isEmpty()) {
            wrapper.eq(SelectorTemplate::getPlatform, platform);
        }
        wrapper.orderByAsc(SelectorTemplate::getPlatform).orderByAsc(SelectorTemplate::getName);
        return mapper.selectList(wrapper);
    }

    public SelectorTemplate getById(Long id) {
        return mapper.selectById(id);
    }

    public SelectorTemplate create(SelectorTemplate template) {
        mapper.insert(template);
        return template;
    }

    public SelectorTemplate update(Long id, SelectorTemplate template) {
        template.setId(id);
        mapper.updateById(template);
        return mapper.selectById(id);
    }

    public void delete(Long id) {
        SelectorTemplate template = mapper.selectById(id);
        if (template != null && template.getIsSystem() != null && template.getIsSystem() == 1) {
            throw new RuntimeException("Cannot delete system template");
        }
        mapper.deleteById(id);
    }

    public SelectorTemplate clone(Long id) {
        SelectorTemplate original = mapper.selectById(id);
        if (original == null) throw new RuntimeException("Template not found");
        SelectorTemplate copy = new SelectorTemplate();
        copy.setName(original.getName() + " (copy)");
        copy.setPlatform(original.getPlatform());
        copy.setTitleSelector(original.getTitleSelector());
        copy.setPriceSelector(original.getPriceSelector());
        copy.setPriceRegex(original.getPriceRegex());
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
}
