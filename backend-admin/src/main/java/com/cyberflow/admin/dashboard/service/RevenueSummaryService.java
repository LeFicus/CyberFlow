package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.dashboard.mapper.RevenueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.*;

/** Implements the accounting rules from monthly_revenue_conversion.py on live data. */
@Service
@RequiredArgsConstructor
public class RevenueSummaryService {
    private final RevenueMapper revenueMapper;
    private final CrawlerConfigService configService;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    public Map<String, Object> summarize(String rawUserGroup, String startDate, String endDate) {
        return summarize(rawUserGroup, startDate, endDate, null);
    }

    public Map<String, Object> summarize(String rawUserGroup, String startDate, String endDate,
                                         String siteCreatedMonth) {
        String userGroup = normalizeGroup(rawUserGroup);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        String effectiveStart = startDate == null || startDate.isBlank()
                ? today.withDayOfMonth(1).toString() : startDate;
        String effectiveEnd = endDate == null || endDate.isBlank()
                ? today.toString() : endDate;
        String effectiveSiteCreatedMonth = normalizeMonth(siteCreatedMonth, today);
        Map<String, Object> config = configService.getRevenueConfig();
        Map<String, List<String>> mergeMap = stringListMap(config.get("userMergeMap"));
        Map<String, String> teacherMap = stringMap(config.get("teacherMap"));
        Map<String, String> leaderMap = stringMap(config.get("leaderConfig"));

        Map<String, AccountStats> accounts = new LinkedHashMap<>();
        for (Map<String, Object> row : revenueMapper.adminOrderStats(userGroup, effectiveStart, effectiveEnd)) {
            AccountStats stats = accounts.computeIfAbsent(text(row, "admin_name"), AccountStats::new);
            stats.group = text(row, "user_group");
            stats.totalOrders = number(row.get("total_orders")).longValue();
            stats.successfulOrders = number(row.get("successful_orders")).longValue();
            stats.originalAmount = number(row.get("original_amount"));
        }
        for (Map<String, Object> row : revenueMapper.adminSiteStats(userGroup, effectiveStart, effectiveEnd)) {
            AccountStats stats = accounts.computeIfAbsent(text(row, "admin_name"), AccountStats::new);
            stats.group = text(row, "user_group");
            stats.siteCount = number(row.get("site_count")).longValue();
        }

        Map<String, PersonStats> people = new LinkedHashMap<>();
        for (AccountStats account : accounts.values()) {
            String realName = realName(account.adminName, mergeMap);
            PersonStats person = people.computeIfAbsent(realName, PersonStats::new);
            person.groups.add(account.group);
            person.accounts.add(account.adminName);
            person.totalOrders += account.totalOrders;
            person.successfulOrders += account.successfulOrders;
            person.siteCount += account.siteCount;
            person.originalAmount = person.originalAmount.add(account.originalAmount);
        }

        // Reference behavior: intern orders stay with the intern; only paid amount is synchronized to mentor.
        for (AccountStats account : accounts.values()) {
            for (Map.Entry<String, String> rule : teacherMap.entrySet()) {
                if (account.adminName.endsWith(rule.getValue())) {
                    PersonStats mentor = people.get(rule.getKey());
                    if (mentor != null) mentor.syncedAmount = mentor.syncedAmount.add(account.originalAmount);
                    break;
                }
            }
        }

        List<Map<String, Object>> personal = new ArrayList<>();
        for (PersonStats person : people.values()) {
            BigDecimal successAmount = person.originalAmount.add(person.syncedAmount);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("user_group", String.join(",", person.groups));
            item.put("real_name", person.realName);
            item.put("accounts", String.join(",", person.accounts));
            item.put("total_orders", person.totalOrders);
            item.put("successful_orders", person.successfulOrders);
            item.put("deduplicated_orders", person.totalOrders);
            item.put("original_amount", money(person.originalAmount));
            item.put("synced_amount", money(person.syncedAmount));
            item.put("successful_amount", money(successAmount));
            item.put("site_count", person.siteCount);
            item.put("conversion_rate", percent(person.totalOrders, person.siteCount));
            item.put("commission_rmb", money(commission(successAmount, config)));
            personal.add(item);
        }
        personal.sort(Comparator.comparing((Map<String, Object> row) -> text(row, "user_group"))
                .thenComparing(row -> number(row.get("deduplicated_orders")), Comparator.reverseOrder()));

        List<Map<String, Object>> leaders = new ArrayList<>();
        for (String group : List.of("A", "B")) {
            if (userGroup != null && !userGroup.equals(group)) continue;
            List<AccountStats> members = accounts.values().stream().filter(a -> group.equals(a.group)).toList();
            BigDecimal originalAmount = members.stream().map(a -> a.originalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            long sites = members.stream().mapToLong(a -> a.siteCount).sum();
            long orders = members.stream().mapToLong(a -> a.totalOrders).sum();
            BigDecimal leaderCommission = originalAmount
                    .multiply(decimal(config.get("exchangeRate"), "6.73"))
                    .multiply(decimal(config.get("rateFactor"), "0.42"))
                    .multiply(decimal(config.get("leaderCommissionRate"), "0.02"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("user_group", group);
            item.put("leader_name", leaderMap.getOrDefault(group, group + "组组长"));
            item.put("member_count", members.stream().map(a -> realName(a.adminName, mergeMap)).distinct().count());
            item.put("site_count", sites);
            item.put("deduplicated_orders", orders);
            item.put("original_amount", money(originalAmount));
            item.put("conversion_rate", percent(orders, sites));
            item.put("leader_commission_rmb", money(leaderCommission));
            leaders.add(item);
        }

        // Orders are matched to the owning site's domain.  Do not use the order
        // platform/group here: a site can contain orders imported by the other
        // platform and those orders must still contribute to the site's cohort.
        Map<String, DomainOrderStats> domainOrders = new HashMap<>();
        for (Map<String, Object> row : revenueMapper.revenueOrdersByDomain(effectiveStart, effectiveEnd)) {
            DomainOrderStats stats = domainOrders.computeIfAbsent(domain(text(row, "product_host")), ignored -> new DomainOrderStats());
            stats.totalOrders += number(row.get("total_orders")).longValue();
            stats.successfulOrders += number(row.get("successful_orders")).longValue();
            stats.successfulAmount = stats.successfulAmount.add(number(row.get("successful_amount")));
        }
        Map<String, MonthlyStats> monthlyStats = new LinkedHashMap<>();
        for (Map<String, Object> site : revenueMapper.revenueSites(userGroup, effectiveSiteCreatedMonth)) {
            String admin = text(site, "admin_name");
            String group = text(site, "user_group");
            String month = text(site, "site_month");
            MonthlyStats stats = monthlyStats.computeIfAbsent(group + "|" + month + "|" + admin,
                    ignored -> new MonthlyStats(group, month, admin));
            stats.siteCount++;
            DomainOrderStats order = domainOrders.get(domain(text(site, "site_domain")));
            if (order != null && order.totalOrders > 0) {
                stats.totalOrders += order.totalOrders;
                stats.successfulOrders += order.successfulOrders;
                stats.successfulAmount = stats.successfulAmount.add(order.successfulAmount);
                stats.orderedSiteCount++;
            }
        }
        List<Map<String, Object>> monthly = new ArrayList<>();
        for (MonthlyStats stats : monthlyStats.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("site_month", stats.month);
            item.put("user_group", stats.group);
            item.put("admin_name", stats.adminName);
            item.put("real_name", realName(stats.adminName, mergeMap));
            item.put("site_count", stats.siteCount);
            item.put("total_orders", stats.totalOrders);
            item.put("deduplicated_orders", stats.totalOrders);
            item.put("ordered_site_count", stats.orderedSiteCount);
            item.put("successful_orders", stats.successfulOrders);
            item.put("successful_amount", money(stats.successfulAmount));
            item.put("order_conversion_rate", percent(stats.totalOrders, stats.siteCount));
            item.put("site_conversion_rate", percent(stats.orderedSiteCount, stats.siteCount));
            // Keep the old field for existing clients; it now means order conversion.
            item.put("conversion_rate", percent(stats.totalOrders, stats.siteCount));
            monthly.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_group", userGroup == null ? "ALL" : userGroup);
        result.put("start_date", effectiveStart);
        result.put("end_date", effectiveEnd);
        result.put("site_created_month", effectiveSiteCreatedMonth);
        result.put("parameters", Map.of(
                "exchange_rate", decimal(config.get("exchangeRate"), "6.73"),
                "rate_factor", decimal(config.get("rateFactor"), "0.42"),
                "leader_commission_rate", decimal(config.get("leaderCommissionRate"), "0.02"),
                "commission_tiers", config.getOrDefault("commissionTiers", List.of())
        ));
        result.put("personal_performance", personal);
        result.put("leader_summary", leaders);
        result.put("monthly_conversion", monthly);
        return result;
    }

    private BigDecimal commission(BigDecimal usd, Map<String, Object> config) {
        BigDecimal base = usd.multiply(decimal(config.get("exchangeRate"), "6.73"))
                .multiply(decimal(config.get("rateFactor"), "0.42"));
        Object rawTiers = config.get("commissionTiers");
        if (rawTiers instanceof List<?> tiers) {
            for (Object rawTier : tiers) {
                if (!(rawTier instanceof Map<?, ?> tier)) continue;
                String thresholdText = Objects.toString(tier.get("threshold"), "").trim();
                BigDecimal rate = decimal(tier.get("rate"), "0");
                if (thresholdText.isEmpty() || base.compareTo(decimal(thresholdText, "0")) <= 0) {
                    return base.multiply(rate);
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private static String realName(String adminName, Map<String, List<String>> mergeMap) {
        for (Map.Entry<String, List<String>> entry : mergeMap.entrySet()) {
            if (entry.getValue().contains(adminName)) return entry.getKey();
        }
        return adminName;
    }

    private static Map<String, String> stringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) map.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return result;
    }

    private static Map<String, List<String>> stringListMap(Object value) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (v instanceof Collection<?> collection) result.put(String.valueOf(k), collection.stream().map(String::valueOf).toList());
            });
        }
        return result;
    }

    private static String normalizeGroup(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        String group = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("A", "B").contains(group)) throw new IllegalArgumentException("userGroup must be A, B or empty");
        return group;
    }

    private static String normalizeMonth(String value, LocalDate fallbackDate) {
        if (value != null && !value.isBlank()) {
            try {
                return YearMonth.parse(value.trim()).toString();
            } catch (RuntimeException ignored) {
                // Invalid manual input falls back to the current business month.
            }
        }
        return YearMonth.from(fallbackDate).toString();
    }

    private static String text(Map<String, Object> row, String key) { return Objects.toString(row.get(key), ""); }
    private static String domain(String value) {
        String result = Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
        int scheme = result.indexOf("://");
        if (scheme >= 0) result = result.substring(scheme + 3);
        int slash = result.indexOf('/');
        if (slash >= 0) result = result.substring(0, slash);
        int port = result.indexOf(':');
        if (port >= 0) result = result.substring(0, port);
        return result.startsWith("www.") ? result.substring(4) : result;
    }
    private static BigDecimal number(Object value) { return decimal(value, "0"); }
    private static BigDecimal decimal(Object value, String fallback) {
        try { return new BigDecimal(Objects.toString(value, fallback)); }
        catch (NumberFormatException ignored) { return new BigDecimal(fallback); }
    }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal percent(long numerator, long denominator) {
        return denominator == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static final class AccountStats {
        final String adminName;
        String group = "";
        long totalOrders;
        long successfulOrders;
        long siteCount;
        BigDecimal originalAmount = BigDecimal.ZERO;
        AccountStats(String adminName) { this.adminName = adminName; }
    }

    private static final class PersonStats {
        final String realName;
        final Set<String> groups = new TreeSet<>();
        final Set<String> accounts = new TreeSet<>();
        long totalOrders;
        long successfulOrders;
        long siteCount;
        BigDecimal originalAmount = BigDecimal.ZERO;
        BigDecimal syncedAmount = BigDecimal.ZERO;
        PersonStats(String realName) { this.realName = realName; }
    }

    private static final class MonthlyStats {
        final String group;
        final String month;
        final String adminName;
        long siteCount;
        long totalOrders;
        long orderedSiteCount;
        long successfulOrders;
        BigDecimal successfulAmount = BigDecimal.ZERO;
        MonthlyStats(String group, String month, String adminName) {
            this.group = group;
            this.month = month;
            this.adminName = adminName;
        }
    }

    private static final class DomainOrderStats {
        long totalOrders;
        long successfulOrders;
        BigDecimal successfulAmount = BigDecimal.ZERO;
    }
}
