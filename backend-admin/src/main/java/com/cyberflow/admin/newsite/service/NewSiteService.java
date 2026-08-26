package com.cyberflow.admin.newsite.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyberflow.admin.dashboard.mapper.EcommerceProductMapper;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.newsite.entity.NewSite;
import com.cyberflow.admin.newsite.mapper.NewSiteMapper;
import com.cyberflow.admin.newsite.model.NewSiteCreateRequest;
import com.cyberflow.admin.newsite.model.NewSiteBatchCreateRequest;
import com.cyberflow.admin.system.entity.SysUser;
import com.cyberflow.admin.system.mapper.SysUserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewSiteService {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    private static final Set<String> VALID_STATUSES = Set.of("pending_review", "enabled", "disabled");

    private final NewSiteMapper newSiteMapper;
    private final EcommerceProductMapper productMapper;
    private final DeepSeekSiteGenerationService generationService;
    private final DomainAvailabilityService domainAvailabilityService;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final CrawlerConfigService crawlerConfigService;

    public Map<String, Object> aiConfig() {
        return crawlerConfigService.getAiGenerationConfig(true);
    }

    public Map<String, Object> updateAiConfig(Map<String, Object> body) {
        return crawlerConfigService.updateAiGenerationConfig(body == null ? Map.of() : body);
    }

    public Page<NewSite> page(int page, int size, String status, String keyword) {
        Page<NewSite> resultPage = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        QueryWrapper<NewSite> query = new QueryWrapper<>();
        if (status != null && !status.isBlank()) query.eq("status", status.trim().toLowerCase(Locale.ROOT));
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper
                    .like("domain", keyword.trim())
                    .or().like("custom_category", keyword.trim())
                    .or().like("site_title", keyword.trim()));
        }
        query.orderByDesc("created_at").orderByDesc("id");
        return newSiteMapper.selectPage(resultPage, query);
    }

    public Map<String, Object> options() {
        List<String> rawCategories = productMapper.listDistinctProductCategories();
        Set<String> categories = new LinkedHashSet<>();
        for (String raw : rawCategories) {
            if (raw == null) continue;
            Arrays.stream(raw.split("\\|{1,3}|[,;\\n]"))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .forEach(categories::add);
        }
        List<String> sourceDomains = productMapper.listDistinctSourceDomains().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return Map.of("productCategories", categories.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                "sourceDomains", sourceDomains);
    }

    @Transactional
    public List<NewSite> createBatch(NewSiteBatchCreateRequest request) {
        List<NewSiteCreateRequest> requests = request.getSites();
        if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("至少创建一个新站点");
        if (requests.size() > 50) throw new IllegalArgumentException("单次最多创建 50 个新站点");

        Set<String> existingSupplementKeys = new LinkedHashSet<>();
        List<NewSite> existingSites = newSiteMapper.selectList(
                new QueryWrapper<NewSite>().select("supplement_product_categories", "supplement_product_category"));
        for (NewSite site : existingSites) {
            categoryKeys(readCategories(site.getSupplementProductCategories(), site.getSupplementProductCategory()))
                    .forEach(existingSupplementKeys::add);
        }

        Set<String> batchKeys = new LinkedHashSet<>();
        for (NewSiteCreateRequest input : requests) {
            List<String> supplementCategories = normalizeCategories(
                    input.getSupplementProductCategories(), "副产品分类");
            for (String key : categoryKeys(supplementCategories)) {
                if (!batchKeys.add(key)) {
                    throw new IllegalArgumentException("本次批量创建中存在重复的副产品分类：" + key);
                }
                if (existingSupplementKeys.contains(key)) {
                    throw new IllegalArgumentException("副产品分类已被已有新站点使用：" + key);
                }
            }
        }

        Long createdBy = currentUserId();
        List<NewSite> created = new ArrayList<>();
        Set<String> batchDomains = new LinkedHashSet<>();
        for (int index = 0; index < requests.size(); index++) {
            NewSiteCreateRequest input = requests.get(index);
            String customCategory = required(input.getCustomCategory(), "自定义分类");
            List<String> mainCategories = normalizeCategories(input.getMainProductCategories(), "主产品分类");
            List<String> supplementCategories = normalizeCategories(input.getSupplementProductCategories(), "副产品分类");
            List<String> sources = normalizeSources(input.getSourceDomains());

            NewSite generated = generateAvailableSite(
                    customCategory, String.join("、", mainCategories),
                    String.join("、", supplementCategories), batchDomains);
            generated.setCustomCategory(customCategory);
            generated.setMainProductCategories(toJson(mainCategories));
            generated.setSupplementProductCategories(toJson(supplementCategories));
            generated.setMainProductCategory(String.join("、", mainCategories));
            generated.setSupplementProductCategory(String.join("、", supplementCategories));
            generated.setSupplementProductCategoryKey(String.join("|", categoryKeys(supplementCategories)));
            generated.setSourceDomains(toJson(sources));
            generated.setStatus("pending_review");
            generated.setCreatedBy(createdBy);
            newSiteMapper.insert(generated);
            created.add(generated);
            existingSupplementKeys.addAll(categoryKeys(supplementCategories));
        }
        return created;
    }

    @Transactional
    public NewSite updateStatus(Long id, String rawStatus) {
        String status = rawStatus == null ? "" : rawStatus.trim().toLowerCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException("不支持的站点状态：" + rawStatus);
        }
        NewSite site = newSiteMapper.selectById(id);
        if (site == null) throw new IllegalArgumentException("新站点不存在：" + id);
        site.setStatus(status);
        newSiteMapper.updateById(site);
        return newSiteMapper.selectById(id);
    }

    private NewSite generateAvailableSite(String customCategory, String mainCategory,
                                          String supplementCategory, Set<String> batchDomains) {
        IllegalStateException lastError = null;
        int attempts = generationService.maxAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                DeepSeekSiteGenerationService.GeneratedSite candidate = generationService.generate(
                        customCategory, mainCategory, supplementCategory, attempt);
                String domain = normalizeDomain(candidate.domain());
                if (batchDomains.contains(domain) || domainExists(domain)) continue;
                DomainAvailabilityService.DomainCheckResult check = domainAvailabilityService.check(domain);
                if (!check.available()) {
                    lastError = new IllegalStateException("候选域名不可购买或无法确认：" + domain + "（" + check.message() + "）");
                    continue;
                }

                NewSite site = new NewSite();
                site.setDomain(domain);
                site.setSiteTitle(candidate.siteTitle().trim());
                site.setTagLine(candidate.tagLine().trim());
                site.setDomainCheckStatus(check.status());
                site.setDomainCheckProvider(check.provider());
                site.setGenerationAttempts(attempt);
                batchDomains.add(domain);
                return site;
            } catch (IllegalStateException e) {
                lastError = e;
            }
        }
        throw new IllegalArgumentException("未能生成可购买域名，已达到重试上限 " + attempts
                + (lastError == null ? "" : "：" + lastError.getMessage()));
    }

    private boolean domainExists(String domain) {
        return newSiteMapper.selectCount(new QueryWrapper<NewSite>().eq("domain", domain)) > 0;
    }

    private Long currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = userMapper.selectByUsername(username);
        return user == null ? null : user.getId();
    }

    private List<String> normalizeSources(Collection<String> values) {
        if (values == null) throw new IllegalArgumentException("至少选择一个源站点");
        List<String> result = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (result.isEmpty()) throw new IllegalArgumentException("至少选择一个源站点");
        return result;
    }

    private List<String> normalizeCategories(Collection<String> values, String label) {
        if (values == null) throw new IllegalArgumentException("至少选择一个" + label);
        List<String> result = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        if (result.isEmpty()) throw new IllegalArgumentException("至少选择一个" + label);

        Set<String> keys = new LinkedHashSet<>();
        for (String value : result) {
            if (!keys.add(categoryKey(value))) {
                throw new IllegalArgumentException(label + "存在重复项：" + value);
            }
        }
        return result;
    }

    private List<String> readCategories(String json, String legacyValue) {
        if (json != null && !json.isBlank()) {
            try {
                var node = objectMapper.readTree(json);
                if (node.isArray()) {
                    List<String> values = new ArrayList<>();
                    node.elements().forEachRemaining(value -> {
                        if (!value.asText().isBlank()) values.add(value.asText().trim());
                    });
                    if (!values.isEmpty()) return values;
                }
            } catch (JsonProcessingException ignored) {
                // Fall back to the legacy display column for partially migrated rows.
            }
        }
        if (legacyValue == null || legacyValue.isBlank()) return List.of();
        return Arrays.stream(legacyValue.split("[、,;\\n|]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private Set<String> categoryKeys(Collection<String> values) {
        return values.stream().map(this::categoryKey).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("源站点数据序列化失败", e);
        }
    }

    private String normalizeDomain(String value) {
        String domain = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceFirst("^https?://", "").replaceFirst("/.*$", "");
        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new IllegalStateException("AI 生成了非法域名：" + value);
        }
        return domain;
    }

    private String categoryKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }
}
