package com.cyberflow.admin.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared, validated filter contract for preview, count and asynchronous exports. */
@Data
public class ProductFilter {
    private List<String> domains = List.of();
    private List<String> customCategories = List.of();
    private List<String> productCategories = List.of();
    private List<String> productRoles = List.of();
    private String name = "";
    private String nameMatch = "prefix";
    private String sku = "";
    private String language = "";
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private LocalDate startDate;
    private LocalDate endDate;

    public ProductFilter normalized() {
        ProductFilter f = new ProductFilter();
        f.domains = values(domains, 255).stream().map(v -> v.toLowerCase(Locale.ROOT)
                .replaceFirst("^https?://", "").replaceFirst("/.*$", "")).distinct().toList();
        if (f.domains.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("来源域名不能为空");
        f.customCategories = values(customCategories, 100);
        f.productCategories = values(productCategories, 500);
        f.productRoles = values(productRoles, 20);
        if (!Set.of("main", "supplement").containsAll(f.productRoles)) throw new IllegalArgumentException("无效的产品标签");
        f.name = text(name, 200); f.sku = text(sku, 255); f.language = text(language, 10);
        f.nameMatch = nameMatch == null ? "prefix" : nameMatch;
        if (!Set.of("prefix", "contains").contains(f.nameMatch)) throw new IllegalArgumentException("无效的名称匹配方式");
        f.minPrice = minPrice; f.maxPrice = maxPrice;
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0))
            throw new IllegalArgumentException("请检查价格范围");
        f.startDate = startDate; f.endDate = endDate;
        if (startDate != null && endDate != null && startDate.isAfter(endDate))
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        return f;
    }

    private static List<String> values(List<String> input, int maxLength) {
        if (input == null) return List.of();
        if (input.size() > 50) throw new IllegalArgumentException("每项筛选最多选择 50 个值");
        return input.stream().map(v -> text(v, maxLength)).filter(v -> !v.isBlank()).distinct().toList();
    }
    private static String text(String input, int maxLength) {
        String value = input == null ? "" : input.trim();
        if (value.length() > maxLength) throw new IllegalArgumentException("筛选内容过长");
        return value;
    }
    public static String escapeLike(String value) { return value.replace("!", "!!").replace("%", "!%").replace("_", "!_"); }
    @JsonIgnore public String getNamePattern() { return ("contains".equals(nameMatch) ? "%" : "") + escapeLike(name) + "%"; }
    @JsonIgnore public String getSkuPattern() { return escapeLike(sku) + "%"; }
    @JsonIgnore public List<String> getCategoryPatterns() { return productCategories.stream().map(v -> "%" + escapeLike(v) + "%").toList(); }
    @JsonIgnore public java.time.LocalDateTime getEndExclusive() { return endDate == null ? null : endDate.plusDays(1).atStartOfDay(); }
}
