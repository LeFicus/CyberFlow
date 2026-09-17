package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.common.DataScopeService;
import com.cyberflow.admin.dashboard.mapper.RevenueMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final DataScopeService dataScopeService;
    private final ObjectMapper objectMapper;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal BATCH_SITE_MIDDLE_THRESHOLD = new BigDecimal("50000");
    private static final BigDecimal BATCH_SITE_HIGH_THRESHOLD = new BigDecimal("150000");
    private static final BigDecimal BATCH_SITE_LOW_RATE = new BigDecimal("0.02");
    private static final BigDecimal BATCH_SITE_MIDDLE_RATE = new BigDecimal("0.04");
    private static final BigDecimal BATCH_SITE_HIGH_RATE = new BigDecimal("0.06");
    private static final BigDecimal LEADER_COMMISSION_RATE = new BigDecimal("0.02");
    private static final BigDecimal REGULAR_LOW_THRESHOLD = new BigDecimal("30000");
    private static final BigDecimal REGULAR_MIDDLE_THRESHOLD = new BigDecimal("80000");
    private static final BigDecimal REGULAR_LOW_RATE = new BigDecimal("0.03");
    private static final BigDecimal REGULAR_MIDDLE_RATE = new BigDecimal("0.05");
    private static final BigDecimal REGULAR_HIGH_RATE = new BigDecimal("0.08");

    public Map<String, Object> summarize(String rawUserGroup, String startDate, String endDate) {
        return summarize(rawUserGroup, startDate, endDate, null);
    }

    public Map<String, Object> summarize(String rawUserGroup, String startDate, String endDate,
                                         String siteCreatedMonth) {
        String userGroup = normalizeGroup(rawUserGroup);
        var scope = dataScopeService.current();
        String ownerName = scope.administrator() ? null : scope.ownerName();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        String effectiveStart = startDate == null || startDate.isBlank()
                ? today.withDayOfMonth(1).toString() : startDate;
        String effectiveEnd = endDate == null || endDate.isBlank()
                ? today.toString() : endDate;
        String siteCreatedBefore = today.withDayOfMonth(1).toString();
        String effectiveSiteCreatedMonth = normalizeMonth(siteCreatedMonth, today);
        Map<String, Object> config = configService.getRevenueConfig();
        Map<String, List<String>> mergeMap = stringListMap(config.get("userMergeMap"));
        Map<String, String> teacherMap = stringMap(config.get("teacherMap"));
        Map<String, String> leaderMap = stringMap(config.get("leaderConfig"));
        List<String> teacherSuffixes = scope.administrator()
                ? List.of()
                : teacherSuffixes(scope.ownerNames(), teacherMap, mergeMap);

        Map<String, AccountStats> accounts = loadAccounts(
                userGroup, ownerName, teacherSuffixes, effectiveStart, effectiveEnd, siteCreatedBefore);

        Map<String, PersonStats> people = new LinkedHashMap<>();
        for (AccountStats account : accounts.values()) {
            String realName = realName(account.adminName, mergeMap);
            PersonStats person = people.computeIfAbsent(realName, PersonStats::new);
            person.groups.add(account.group);
            person.accounts.add(account.adminName);
            person.totalOrders += account.totalOrders;
            person.validOrders += account.validOrders;
            person.successfulOrders += account.successfulOrders;
            person.standaloneOrders += account.standaloneOrders;
            person.batchOrders += account.batchOrders;
            person.copyOrders += account.copyOrders;
            person.siteCount += account.siteCount;
            person.batchSiteCount += account.batchSiteCount;
            mergeCounts(person.categorySites, account.categorySites);
            person.originalAmount = person.originalAmount.add(account.originalAmount);
            person.batchSiteAmount = person.batchSiteAmount.add(account.batchSiteAmount);
            if (!isTeacherSuffixAccount(account.adminName, teacherMap)) {
                person.commissionEligible = true;
            }
        }

        // A mentor may have no direct order/site row while the mapped intern
        // does. Create the mentor bucket from ownership so the intern amount
        // can still be synchronized into the mentor's commission.
        if (!scope.administrator()) {
            for (String owner : scope.ownerNames()) {
                PersonStats mentor = people.computeIfAbsent(realName(owner, mergeMap), PersonStats::new);
                mentor.commissionEligible = true;
            }
        }

        // Reference behavior: intern orders stay with the intern; only paid amount is synchronized to mentor.
        for (AccountStats account : accounts.values()) {
            for (Map.Entry<String, String> rule : teacherMap.entrySet()) {
                if (hasSuffix(account.adminName, rule.getValue())) {
                    PersonStats mentor = people.get(rule.getKey());
                    if (mentor == null) mentor = people.get(realName(rule.getKey(), mergeMap));
                    if (mentor != null) {
                        mentor.syncedAmount = mentor.syncedAmount.add(account.originalAmount);
                        mentor.syncedBatchSiteAmount = mentor.syncedBatchSiteAmount.add(account.batchSiteAmount);
                        mentor.commissionEligible = true;
                    }
                    break;
                }
            }
        }

        List<Map<String, Object>> personal = new ArrayList<>();
        Map<String, BigDecimal> personalCommissionByName = new HashMap<>();
        for (PersonStats person : people.values()) {
            BigDecimal successAmount = person.originalAmount.add(person.syncedAmount);
            BigDecimal batchSiteAmount = person.batchSiteAmount.add(person.syncedBatchSiteAmount);
            BigDecimal regularAmount = successAmount.subtract(batchSiteAmount).max(BigDecimal.ZERO);
            BigDecimal regularCommission = commission(regularAmount, config);
            BigDecimal batchCommissionBase = commissionBase(batchSiteAmount, config);
            BigDecimal batchCommissionRate = batchSiteCommissionRate(batchCommissionBase);
            BigDecimal batchCommission = batchCommissionBase.multiply(batchCommissionRate);
            BigDecimal totalCommission = regularCommission.add(batchCommission);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("user_group", String.join(",", person.groups));
            item.put("real_name", person.realName);
            item.put("accounts", String.join(",", person.accounts));
            item.put("total_orders", person.totalOrders);
            item.put("successful_orders", person.successfulOrders);
            item.put("deduplicated_orders", person.totalOrders);
            item.put("valid_deduplicated_orders", person.validOrders);
            item.put("original_amount", money(person.originalAmount));
            item.put("synced_amount", money(person.syncedAmount));
            item.put("successful_amount", money(successAmount));
            item.put("site_count", person.siteCount);
            item.put("batch_site_count", person.batchSiteCount);
            item.put("batch_site_amount", money(batchSiteAmount));
            item.put("batch_site_commission_base_rmb", money(batchCommissionBase));
            item.put("batch_site_commission_rate", batchCommissionRate);
            item.put("regular_commission_rmb", person.commissionEligible ? money(regularCommission) : null);
            item.put("batch_site_commission_rmb", person.commissionEligible ? money(batchCommission) : null);
            item.put("conversion_rate", percent(person.totalOrders, person.siteCount));
            item.put("commission_rmb", person.commissionEligible ? money(totalCommission) : null);
            item.put("total_member_commission_rmb", person.commissionEligible ? money(totalCommission) : null);
            item.put("classification_breakdown", classificationBreakdown(
                    person.standaloneOrders, person.batchOrders, person.copyOrders));
            item.put("category_breakdown", categoryBreakdown(person.categorySites));
            if (person.commissionEligible) personalCommissionByName.put(person.realName, money(totalCommission));
            personal.add(item);
        }
        sortByDeduplicatedOrders(personal);
        BigDecimal totalMemberCommission = personal.stream()
                .map(row -> number(row.get("total_member_commission_rmb")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Personal data remains owner-scoped, but a non-admin needs the
        // aggregate for their whole group in order to see a meaningful leader
        // summary. The group is derived from the scoped account data, so a
        // user cannot request another group's leader totals.
        List<Map<String, Object>> leaders = new ArrayList<>();
        if (scope.administrator() || scope.operator()) {
            String visibleLeaderGroup = scope.administrator() ? null : resolveGroup(accounts);
            Map<String, AccountStats> leaderAccounts = scope.administrator() || visibleLeaderGroup == null
                    ? accounts
                    : loadAccounts(visibleLeaderGroup, null, List.of(), effectiveStart, effectiveEnd, siteCreatedBefore);
            String leaderTotalsGroup = scope.administrator() ? userGroup : visibleLeaderGroup;
            Map<String, Map<String, Object>> groupOrderTotals = new HashMap<>();
            for (Map<String, Object> row : revenueMapper.groupOrderStats(
                    leaderTotalsGroup, effectiveStart, effectiveEnd)) {
                groupOrderTotals.put(text(row, "user_group"), row);
            }

            Set<String> currentGroups = new TreeSet<>(groupOrderTotals.keySet());
            leaderAccounts.values().stream().map(account -> account.group)
                    .filter(group -> group != null && !group.isBlank()).forEach(currentGroups::add);
            for (String group : currentGroups) {
                if (userGroup != null && !userGroup.equals(group)) continue;
                if (visibleLeaderGroup != null && !visibleLeaderGroup.equals(group)) continue;
                List<AccountStats> members = leaderAccounts.values().stream().filter(a -> group.equals(a.group)).toList();
                Map<String, Object> groupTotals = groupOrderTotals.getOrDefault(group, Map.of());
                BigDecimal originalAmount = number(groupTotals.get("original_amount"));
                String leaderName = leaderMap.getOrDefault(group, group + "组组长");
                BigDecimal leaderPersonalAmount = personalSuccessfulAmount(
                        leaderName, members, mergeMap, teacherMap);
                BigDecimal commissionBaseAmount = originalAmount.subtract(leaderPersonalAmount).max(BigDecimal.ZERO);
                long sites = members.stream().mapToLong(a -> a.siteCount).sum();
                long orders = number(groupTotals.get("total_orders")).longValue();
                BigDecimal leaderCommission = commissionBaseAmount
                        .multiply(decimal(config.get("exchangeRate"), "6.73"))
                        .multiply(decimal(config.get("rateFactor"), "0.42"))
                        .multiply(LEADER_COMMISSION_RATE);
                BigDecimal leaderPersonalCommission = personalCommissionByName.getOrDefault(leaderName, BigDecimal.ZERO);
                BigDecimal leaderTotalCommission = leaderCommission.add(leaderPersonalCommission);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("user_group", group);
                item.put("leader_name", leaderName);
                item.put("member_count", members.stream().map(a -> realName(a.adminName, mergeMap)).distinct().count());
                item.put("site_count", sites);
                item.put("deduplicated_orders", orders);
                item.put("valid_deduplicated_orders", number(groupTotals.get("valid_orders")).longValue());
                item.put("original_amount", money(originalAmount));
                item.put("leader_personal_amount", money(leaderPersonalAmount));
                item.put("commission_base_amount", money(commissionBaseAmount));
                item.put("conversion_rate", percent(orders, sites));
                item.put("leader_commission_rmb", money(leaderCommission));
                item.put("leader_personal_commission_rmb", money(leaderPersonalCommission));
                item.put("leader_total_commission_rmb", money(leaderTotalCommission));
                item.put("classification_breakdown", classificationBreakdown(
                        number(groupTotals.get("standalone_orders")).longValue(),
                        number(groupTotals.get("batch_orders")).longValue(),
                        number(groupTotals.get("copy_orders")).longValue()));
                Map<String, Long> groupCategorySites = new LinkedHashMap<>();
                members.forEach(member -> mergeCounts(groupCategorySites, member.categorySites));
                item.put("category_breakdown", categoryBreakdown(groupCategorySites));
                leaders.add(item);
            }
        }
        BigDecimal totalLeaderGroupCommission = leaders.stream()
                .map(row -> number(row.get("leader_commission_rmb")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLeaderCommission = leaders.stream()
                .map(row -> number(row.get("leader_total_commission_rmb")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Orders are matched to the owning site's domain.  Do not use the order
        // platform/group here: a site can contain orders imported by the other
        // platform and those orders must still contribute to the site's cohort.
        Map<String, DomainOrderStats> domainOrders = new HashMap<>();
        for (Map<String, Object> row : revenueMapper.revenueOrdersByDomain(effectiveStart, effectiveEnd, ownerName, teacherSuffixes)) {
            DomainOrderStats stats = domainOrders.computeIfAbsent(domain(text(row, "product_host")), ignored -> new DomainOrderStats());
            stats.totalOrders += number(row.get("total_orders")).longValue();
            stats.validOrders += number(row.get("valid_orders")).longValue();
            stats.successfulOrders += number(row.get("successful_orders")).longValue();
            stats.successfulAmount = stats.successfulAmount.add(number(row.get("successful_amount")));
        }
        Map<String, MonthlyStats> monthlyStats = new LinkedHashMap<>();
        for (Map<String, Object> site : revenueMapper.revenueSites(userGroup, ownerName, teacherSuffixes, effectiveSiteCreatedMonth)) {
            String admin = text(site, "admin_name");
            String group = text(site, "user_group");
            String month = text(site, "site_month");
            MonthlyStats stats = monthlyStats.computeIfAbsent(group + "|" + month + "|" + admin,
                    ignored -> new MonthlyStats(group, month, admin));
            stats.siteCount++;
            for (String category : siteCategories(site.get("cat_names"))) {
                stats.categorySites.merge(category, 1L, Long::sum);
            }
            DomainOrderStats order = domainOrders.get(domain(text(site, "site_domain")));
            if (order != null && order.totalOrders > 0) {
                stats.totalOrders += order.totalOrders;
                stats.validOrders += order.validOrders;
                stats.successfulOrders += order.successfulOrders;
                stats.successfulAmount = stats.successfulAmount.add(order.successfulAmount);
                stats.orderedSiteCount++;
                stats.classificationOrders[siteTag(site.get("site_tag"))] += order.totalOrders;
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
            item.put("valid_deduplicated_orders", stats.validOrders);
            item.put("ordered_site_count", stats.orderedSiteCount);
            item.put("successful_orders", stats.successfulOrders);
            item.put("successful_amount", money(stats.successfulAmount));
            item.put("order_conversion_rate", percent(stats.totalOrders, stats.siteCount));
            item.put("site_conversion_rate", percent(stats.orderedSiteCount, stats.siteCount));
            // Keep the old field for existing clients; it now means order conversion.
            item.put("conversion_rate", percent(stats.totalOrders, stats.siteCount));
            item.put("classification_breakdown", classificationBreakdown(
                    stats.classificationOrders[0], stats.classificationOrders[1], stats.classificationOrders[2]));
            item.put("category_breakdown", categoryBreakdown(stats.categorySites));
            monthly.add(item);
        }
        sortByDeduplicatedOrders(monthly);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_group", userGroup == null ? "ALL" : userGroup);
        result.put("start_date", effectiveStart);
        result.put("end_date", effectiveEnd);
        result.put("site_created_month", effectiveSiteCreatedMonth);
        result.put("site_created_before", siteCreatedBefore);
        result.put("parameters", Map.of(
                "exchange_rate", decimal(config.get("exchangeRate"), "6.73"),
                "rate_factor", decimal(config.get("rateFactor"), "0.42"),
                "leader_commission_rate", LEADER_COMMISSION_RATE,
                "batch_site_commission_tiers", batchSiteCommissionTiers(),
                "commission_tiers", regularCommissionTiers()
        ));
        result.put("total_member_commission_rmb", money(totalMemberCommission));
        result.put("total_leader_group_commission_rmb", money(totalLeaderGroupCommission));
        result.put("total_leader_commission_rmb", money(totalLeaderCommission));
        result.put("personal_performance", personal);
        result.put("leader_summary", leaders);
        result.put("monthly_conversion", monthly);
        return result;
    }

    static void sortByDeduplicatedOrders(List<Map<String, Object>> rows) {
        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> number(row.get("deduplicated_orders")), Comparator.reverseOrder())
                .thenComparing(row -> text(row, "user_group"))
                .thenComparing(row -> text(row, "real_name")));
    }

    private Map<String, AccountStats> loadAccounts(String userGroup, String ownerName,
                                                    List<String> teacherSuffixes,
                                                    String startDate, String endDate,
                                                    String siteCreatedBefore) {
        Map<String, AccountStats> accounts = new LinkedHashMap<>();
        for (Map<String, Object> row : revenueMapper.adminOrderStats(userGroup, ownerName, teacherSuffixes, startDate, endDate)) {
            AccountStats stats = accounts.computeIfAbsent(text(row, "admin_name"), AccountStats::new);
            stats.group = text(row, "user_group");
            stats.totalOrders = number(row.get("total_orders")).longValue();
            stats.validOrders = number(row.get("valid_orders")).longValue();
            stats.successfulOrders = number(row.get("successful_orders")).longValue();
            stats.standaloneOrders = number(row.get("standalone_orders")).longValue();
            stats.batchOrders = number(row.get("batch_orders")).longValue();
            stats.copyOrders = number(row.get("copy_orders")).longValue();
            stats.originalAmount = number(row.get("original_amount"));
            stats.batchSiteAmount = number(row.get("batch_site_amount"));
        }
        for (Map<String, Object> row : revenueMapper.adminSiteStats(
                userGroup, ownerName, teacherSuffixes, siteCreatedBefore)) {
            AccountStats stats = accounts.computeIfAbsent(text(row, "admin_name"), AccountStats::new);
            stats.group = text(row, "user_group");
            stats.siteCount = number(row.get("site_count")).longValue();
            stats.batchSiteCount = number(row.get("batch_site_count")).longValue();
        }
        for (Map<String, Object> row : revenueMapper.adminSiteCategoryStats(
                userGroup, ownerName, teacherSuffixes, siteCreatedBefore)) {
            AccountStats stats = accounts.computeIfAbsent(text(row, "admin_name"), AccountStats::new);
            stats.group = text(row, "user_group");
            stats.categorySites.merge(categoryName(row.get("category_name")),
                    number(row.get("site_count")).longValue(), Long::sum);
        }
        return accounts;
    }

    private static List<String> teacherSuffixes(List<String> owners,
                                                Map<String, String> teacherMap,
                                                Map<String, List<String>> mergeMap) {
        return teacherMap.entrySet().stream()
                .filter(entry -> owners.contains(entry.getKey())
                        || owners.stream().anyMatch(owner -> realName(owner, mergeMap).equals(entry.getKey())))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static boolean isTeacherSuffixAccount(String adminName, Map<String, String> teacherMap) {
        return teacherMap.values().stream()
                .anyMatch(value -> hasSuffix(adminName, value));
    }

    private static boolean hasSuffix(String accountName, String suffix) {
        String account = Objects.toString(accountName, "").trim().toLowerCase(Locale.ROOT);
        String normalizedSuffix = Objects.toString(suffix, "").trim().toLowerCase(Locale.ROOT);
        return !account.isEmpty() && !normalizedSuffix.isEmpty() && account.endsWith(normalizedSuffix);
    }

    private static String resolveGroup(Map<String, AccountStats> accounts) {
        return accounts.values().stream()
                .map(account -> account.group)
                .filter(group -> group != null && !group.isBlank())
                .distinct()
                .findFirst()
                .orElse(null);
    }

    /**
     * Uses the same successful-amount definition as personal performance:
     * the leader's own merged accounts plus paid amounts synchronized from
     * accounts matching the leader's mentor suffix.
     */
    private static BigDecimal personalSuccessfulAmount(String configuredLeaderName,
                                                        Collection<AccountStats> accounts,
                                                        Map<String, List<String>> mergeMap,
                                                        Map<String, String> teacherMap) {
        String leaderName = realName(configuredLeaderName, mergeMap);
        BigDecimal amount = accounts.stream()
                .filter(account -> leaderName.equals(realName(account.adminName, mergeMap)))
                .map(account -> account.originalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (AccountStats account : accounts) {
            for (Map.Entry<String, String> rule : teacherMap.entrySet()) {
                if (!hasSuffix(account.adminName, rule.getValue())) continue;
                if (leaderName.equals(rule.getKey()) || leaderName.equals(realName(rule.getKey(), mergeMap))) {
                    amount = amount.add(account.originalAmount);
                }
                break;
            }
        }
        return amount;
    }

    private BigDecimal commission(BigDecimal usd, Map<String, Object> config) {
        BigDecimal base = commissionBase(usd, config);
        return base.multiply(regularCommissionRate(base));
    }

    private BigDecimal commissionBase(BigDecimal usd, Map<String, Object> config) {
        return usd.multiply(decimal(config.get("exchangeRate"), "6.73"))
                .multiply(decimal(config.get("rateFactor"), "0.42"));
    }

    /** Regular tiers use the converted RMB commission base and are not progressive. */
    static BigDecimal regularCommissionRate(BigDecimal commissionBase) {
        if (commissionBase.compareTo(REGULAR_LOW_THRESHOLD) <= 0) return REGULAR_LOW_RATE;
        if (commissionBase.compareTo(REGULAR_MIDDLE_THRESHOLD) <= 0) return REGULAR_MIDDLE_RATE;
        return REGULAR_HIGH_RATE;
    }

    /** Batch-site tiers use the converted RMB commission base and are not progressive. */
    static BigDecimal batchSiteCommissionRate(BigDecimal commissionBase) {
        if (commissionBase.compareTo(BATCH_SITE_MIDDLE_THRESHOLD) < 0) {
            return BATCH_SITE_LOW_RATE;
        }
        if (commissionBase.compareTo(BATCH_SITE_HIGH_THRESHOLD) <= 0) {
            return BATCH_SITE_MIDDLE_RATE;
        }
        return BATCH_SITE_HIGH_RATE;
    }

    private static List<Map<String, Object>> batchSiteCommissionTiers() {
        return List.of(
                Map.of("min", 0, "max", 50000, "max_inclusive", false, "rate", BATCH_SITE_LOW_RATE),
                Map.of("min", 50000, "max", 150000, "max_inclusive", true, "rate", BATCH_SITE_MIDDLE_RATE),
                Map.of("min_exclusive", 150000, "rate", BATCH_SITE_HIGH_RATE)
        );
    }

    private static List<Map<String, Object>> regularCommissionTiers() {
        return List.of(
                Map.of("max", REGULAR_LOW_THRESHOLD, "rate", REGULAR_LOW_RATE),
                Map.of("min_exclusive", REGULAR_LOW_THRESHOLD, "max", REGULAR_MIDDLE_THRESHOLD, "rate", REGULAR_MIDDLE_RATE),
                Map.of("min_exclusive", REGULAR_MIDDLE_THRESHOLD, "rate", REGULAR_HIGH_RATE)
        );
    }

    static List<Map<String, Object>> classificationBreakdown(long standalone, long batch, long copy) {
        long total = standalone + batch + copy;
        return List.of(
                classificationItem(0, "单独建站", standalone, total),
                classificationItem(1, "批量建站", batch, total),
                classificationItem(2, "复制站", copy, total)
        );
    }

    static List<Map<String, Object>> categoryBreakdown(Map<String, Long> categories) {
        long total = categories.values().stream().filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        return categories.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> Map.<String, Object>of(
                        "category", categoryName(entry.getKey()),
                        "site_count", entry.getValue(),
                        "ratio", percent(entry.getValue(), total)))
                .toList();
    }

    private static void mergeCounts(Map<String, Long> target, Map<String, Long> source) {
        source.forEach((key, value) -> target.merge(categoryName(key), value, Long::sum));
    }

    private List<String> siteCategories(Object value) {
        if (value instanceof Collection<?> collection) {
            List<String> result = collection.stream().map(RevenueSummaryService::categoryName).distinct().toList();
            return result.isEmpty() ? List.of("未分类") : result;
        }
        String raw = Objects.toString(value, "").trim();
        if (raw.isEmpty()) return List.of("未分类");
        try {
            List<Object> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
            List<String> result = parsed.stream().map(RevenueSummaryService::categoryName).distinct().toList();
            return result.isEmpty() ? List.of("未分类") : result;
        } catch (Exception ignored) {
            return List.of(categoryName(raw));
        }
    }

    private static String categoryName(Object value) {
        String category = Objects.toString(value, "").trim();
        return category.isEmpty() ? "未分类" : category;
    }

    private static Map<String, Object> classificationItem(int type, String label, long orders, long total) {
        return Map.of("type", type, "label", label, "orders", orders, "ratio", percent(orders, total));
    }

    private static int siteTag(Object value) {
        int tag = number(value).intValue();
        return tag >= 0 && tag <= 2 ? tag : 0;
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
        String group = value.trim();
        if (group.length() > 32 || !group.matches("[\\p{L}\\p{N}_-]+")) {
            throw new IllegalArgumentException("userGroup must be an existing site group or empty");
        }
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
        long validOrders;
        long successfulOrders;
        long standaloneOrders;
        long batchOrders;
        long copyOrders;
        long siteCount;
        long batchSiteCount;
        final Map<String, Long> categorySites = new LinkedHashMap<>();
        BigDecimal originalAmount = BigDecimal.ZERO;
        BigDecimal batchSiteAmount = BigDecimal.ZERO;
        AccountStats(String adminName) { this.adminName = adminName; }
    }

    private static final class PersonStats {
        final String realName;
        final Set<String> groups = new TreeSet<>();
        final Set<String> accounts = new TreeSet<>();
        long totalOrders;
        long validOrders;
        long successfulOrders;
        long standaloneOrders;
        long batchOrders;
        long copyOrders;
        long siteCount;
        long batchSiteCount;
        final Map<String, Long> categorySites = new LinkedHashMap<>();
        BigDecimal originalAmount = BigDecimal.ZERO;
        BigDecimal syncedAmount = BigDecimal.ZERO;
        BigDecimal batchSiteAmount = BigDecimal.ZERO;
        BigDecimal syncedBatchSiteAmount = BigDecimal.ZERO;
        boolean commissionEligible;
        PersonStats(String realName) { this.realName = realName; }
    }

    private static final class MonthlyStats {
        final String group;
        final String month;
        final String adminName;
        long siteCount;
        long totalOrders;
        long validOrders;
        long orderedSiteCount;
        long successfulOrders;
        final long[] classificationOrders = new long[3];
        final Map<String, Long> categorySites = new LinkedHashMap<>();
        BigDecimal successfulAmount = BigDecimal.ZERO;
        MonthlyStats(String group, String month, String adminName) {
            this.group = group;
            this.month = month;
            this.adminName = adminName;
        }
    }

    private static final class DomainOrderStats {
        long totalOrders;
        long validOrders;
        long successfulOrders;
        BigDecimal successfulAmount = BigDecimal.ZERO;
    }
}
