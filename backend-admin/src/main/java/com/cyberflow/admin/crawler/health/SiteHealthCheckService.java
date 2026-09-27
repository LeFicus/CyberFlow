package com.cyberflow.admin.crawler.health;

import com.cyberflow.admin.common.DataScopeService;
import com.cyberflow.admin.crawler.config.service.CrawlerConfigService;
import com.cyberflow.admin.crawler.health.mapper.SiteHealthMapper;
import com.cyberflow.admin.crawler.task.entity.TaskHistory;
import com.cyberflow.admin.crawler.task.service.TaskHistoryService;
import com.cyberflow.admin.system.service.SysNotificationService;
import com.cyberflow.admin.system.notification.NotificationPlatform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.IDN;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Runs the domain monitor as a first-class CyberFlow task. */
@Service
@Slf4j
@RequiredArgsConstructor
public class SiteHealthCheckService {

    private static final Pattern IMAGE_URL = Pattern.compile(
            "src=[\\\"'](https?://[^\\\"']+/wp-content/uploads/[^\\\"']+\\.(?:png|jpg|jpeg|webp))(?:\\?[^\\\"']*)?[\\\"']",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> FATAL_TEXT = List.of(
            "error establishing a database connection", "站点发生了关键错误",
            "there has been a critical error", "internal server error", "published_database_error");
    private static final DateTimeFormatter REPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SiteHealthMapper mapper;
    private final TaskHistoryService taskHistoryService;
    private final CrawlerConfigService crawlerConfigService;
    private final SysNotificationService notificationService;
    private final TaskExecutor taskExecutor;
    private final DataScopeService dataScopeService;

    public synchronized Map<String, Object> trigger(String triggerType) {
        if (taskHistoryService.hasActiveTask("site_health", null)) {
            throw new IllegalArgumentException("站点健康检查正在执行，请等待当前任务结束");
        }
        String taskId = UUID.randomUUID().toString();
        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setType("site_health");
        history.setTriggerType("cron".equals(triggerType) ? "cron" : "manual");
        history.setStatus("PENDING");
        history.setProgress(0);
        history.setProgressMessage("等待站点健康检查");
        taskHistoryService.save(history);
        taskExecutor.execute(() -> run(taskId));
        return Map.of("task_id", taskId, "status", "Site health check started");
    }

    public Map<String, Object> page(int page, int size, String status, String reason, String userGroup,
                                    String adminName, String keyword) {
        String normalizedStatus = normalizeStatus(status);
        String checkedReason = trimToNull(reason);
        String group = trimToNull(userGroup);
        String admin = trimToNull(adminName);
        String search = trimToNull(keyword);
        String ownerName = ownerName();
        int safePage = Math.max(1, page);
        int safeSize = Math.max(10, Math.min(100, size));
        long total = mapper.count(normalizedStatus, checkedReason, group, admin, search, ownerName);
        return Map.of("records", mapper.list(normalizedStatus, checkedReason, group, admin, search, ownerName,
                        (safePage - 1) * safeSize, safeSize),
                "total", total, "current", safePage, "size", safeSize);
    }

    public Map<String, Object> summary(String status, String reason, String userGroup, String adminName, String keyword) {
        Map<String, Object> value = mapper.summary(normalizeStatus(status), trimToNull(reason), trimToNull(userGroup),
                trimToNull(adminName), trimToNull(keyword), ownerName());
        return value == null ? Map.of("total", 0, "healthy", 0, "unhealthy", 0) : value;
    }

    public Map<String, Object> filterOptions() {
        String ownerName = ownerName();
        return Map.of("userGroups", mapper.listUserGroups(ownerName),
                "adminNames", mapper.listAdminNames(ownerName),
                "reasons", mapper.listReasons(ownerName));
    }

    private String ownerName() {
        var scope = dataScopeService.current();
        return scope.administrator() ? null : scope.ownOwnerFilter();
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !normalized.isEmpty()
                && !Set.of("HEALTHY", "UNHEALTHY").contains(normalized)) {
            throw new IllegalArgumentException("不支持的站点健康状态");
        }
        return trimToNull(normalized);
    }

    private void run(String taskId) {
        long started = System.currentTimeMillis();
        try {
            taskHistoryService.start(taskId, "正在筛选待检查站点");
            Strategy strategy = Strategy.from(crawlerConfigService.getSiteHealthStrategy());
            mapper.deleteOrphans();
            List<Target> targets = filterTargets(mapper.listTargets(), strategy);
            Map<String, String> previous = mapper.listCurrentStates().stream()
                    .collect(Collectors.toMap(row -> String.valueOf(row.get("site_domain")),
                            row -> String.valueOf(row.get("status")), (left, right) -> right));
            taskHistoryService.appendLog(taskId, "站点健康检查开始：目标 " + targets.size() + " 个\n");

            if (targets.isEmpty()) {
                taskHistoryService.succeed(taskId, 0, System.currentTimeMillis() - started, "没有符合条件的站点");
                return;
            }

            Map<String, List<Target>> groups = targets.stream().collect(Collectors.groupingBy(
                    target -> target.serverIp().isBlank() ? "unknown:" + target.domain() : target.serverIp(),
                    LinkedHashMap::new, Collectors.toList()));
            AtomicInteger completed = new AtomicInteger();
            List<CheckResult> results = java.util.Collections.synchronizedList(new ArrayList<>());
            int parallelism = Math.max(1, Math.min(strategy.maxParallelServers(), groups.size()));
            ExecutorService pool = Executors.newFixedThreadPool(parallelism);
            try {
                List<CompletableFuture<Void>> futures = groups.values().stream().map(serverTargets ->
                        CompletableFuture.runAsync(() -> checkServer(taskId, serverTargets, strategy,
                                targets.size(), completed, results), pool)).toList();
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            } finally {
                pool.shutdownNow();
            }

            results.sort(Comparator.comparing(CheckResult::healthy).thenComparing(CheckResult::domain));
            long unhealthy = results.stream().filter(result -> !result.healthy()).count();
            long newFailures = results.stream().filter(result -> !result.healthy()
                    && !"UNHEALTHY".equals(previous.get(result.domain()))).count();
            long recovered = results.stream().filter(CheckResult::healthy)
                    .filter(result -> "UNHEALTHY".equals(previous.get(result.domain()))).count();
            sendSummary(results, newFailures, recovered, System.currentTimeMillis() - started, strategy);
            taskHistoryService.succeed(taskId, results.size(), System.currentTimeMillis() - started,
                    "检查完成：正常 " + (results.size() - unhealthy) + "，异常 " + unhealthy);
        } catch (Exception e) {
            log.error("Site health check failed: taskId={}", taskId, e);
            try { taskHistoryService.appendLog(taskId, "任务异常：" + safeMessage(e) + "\n"); }
            catch (RuntimeException logError) { log.debug("Unable to append final site-health log", logError); }
            taskHistoryService.fail(taskId, safeMessage(e), System.currentTimeMillis() - started);
        }
    }

    private void checkServer(String taskId, List<Target> targets, Strategy strategy, int total,
                             AtomicInteger completed, List<CheckResult> results) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(strategy.connectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.ALWAYS).build();
        for (int index = 0; index < targets.size(); index++) {
            Target target = targets.get(index);
            CheckResult result = checkWithRetry(client, target, strategy)
                    .withContext(target.adminName(), target.serverName());
            results.add(result);
            mapper.upsert(result.domain(), result.healthy() ? "HEALTHY" : "UNHEALTHY", result.reason(),
                    result.httpStatus(), truncate(result.finalUrl(), 1000), target.serverName(), target.serverIp(),
                    target.adminName(), target.userGroup(), result.latencyMs(), LocalDateTime.now());
            taskHistoryService.appendLog(taskId, String.format("[%s] %s status=%s reason=%s latency=%dms%n",
                    target.serverIp().isBlank() ? "未知服务器" : target.serverIp(), result.domain(),
                    result.httpStatus() == null ? "Error" : result.httpStatus(), result.reason(), result.latencyMs()));
            int done = completed.incrementAndGet();
            taskHistoryService.progress(taskId, Math.min(99, done * 100 / total),
                    "已检查 " + done + "/" + total + " 个站点");
            if (index + 1 < targets.size() && strategy.sameServerDelayMillis() > 0) {
                sleep(strategy.sameServerDelayMillis());
            }
        }
    }

    private CheckResult checkWithRetry(HttpClient client, Target target, Strategy strategy) {
        CheckResult result = null;
        for (int attempt = 1; attempt <= strategy.maxRetries(); attempt++) {
            result = executeCheck(client, target.domain(), strategy);
            if (result.healthy() || result.reason().contains("图片缺失")) return result;
            if (attempt < strategy.maxRetries()) sleep(strategy.retryDelayMillis());
        }
        return result;
    }

    private CheckResult executeCheck(HttpClient client, String domain, Strategy strategy) {
        long started = System.nanoTime();
        String clean = normalizeDomain(domain);
        List<String> candidates = clean.startsWith("www.")
                ? List.of("https://" + clean)
                : List.of("https://www." + clean, "https://" + clean);
        CheckResult last = null;
        for (String url : candidates) {
            try {
                HttpRequest request = request(url, strategy.requestTimeoutSeconds());
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                long latency = elapsedMillis(started);
                int status = response.statusCode();
                String body = response.body() == null ? "" : response.body();
                String lower = body.toLowerCase(Locale.ROOT);
                if (status == 403) {
                    if (lower.contains("suspected phishing")) return ok(clean, status, response.uri(), latency);
                    String reason = lower.contains("suspected misleading website") || lower.contains("misrepresenting itself")
                            ? "遭 Cloudflare 拦截：疑似误导/仿冒品牌网站"
                            : lower.contains("cloudflare") && (lower.contains("captcha") || lower.contains("challenge-platform"))
                            ? "遭 Cloudflare 拦截：触发验证码/五秒盾" : "异常响应(403 Forbidden/服务器拒绝访问)";
                    return fail(clean, status, response.uri(), reason, latency);
                }
                if (status >= 200 && status < 400) {
                    for (String keyword : FATAL_TEXT) {
                        if (lower.contains(keyword)) return fail(clean, status, response.uri(), keyword, latency);
                    }
                    if (body.trim().length() < strategy.minimumBodyLength()) {
                        return fail(clean, status, response.uri(), "站点停止运行", latency);
                    }
                    Matcher matcher = IMAGE_URL.matcher(body);
                    Set<String> images = new LinkedHashSet<>();
                    while (matcher.find() && images.size() < 8) images.add(matcher.group(1));
                    for (String image : images) {
                        try {
                            HttpResponse<Void> imageResponse = client.send(request(image, 5), HttpResponse.BodyHandlers.discarding());
                            if (imageResponse.statusCode() >= 400) {
                                return fail(clean, status, response.uri(), imageReason(image), latency);
                            }
                        } catch (Exception e) {
                            return fail(clean, status, response.uri(), imageReason(image), latency);
                        }
                    }
                    return ok(clean, status, response.uri(), latency);
                }
                return fail(clean, status, response.uri(), "异常响应(" + status + ")", latency);
            } catch (Exception e) {
                String reason = classify(e);
                last = fail(clean, null, URI.create(url), reason, elapsedMillis(started));
                if (!isTransportFailure(e)) return last;
            }
        }
        return last == null ? fail(clean, null, null, "请求错误", elapsedMillis(started)) : last;
    }

    private void sendSummary(List<CheckResult> results, long newFailures, long recovered,
                             long durationMillis, Strategy strategy) {
        List<CheckResult> failures = results.stream().filter(result -> !result.healthy()).toList();
        if (failures.isEmpty() && recovered == 0 && !strategy.notifyEveryRun()) return;
        Map<String, List<CheckResult>> failuresByEmployee = failures.stream().collect(Collectors.groupingBy(
                result -> result.adminName().isBlank() ? "未分配员工" : result.adminName(),
                LinkedHashMap::new, Collectors.toList()));
        StringBuilder content = new StringBuilder()
                .append(failures.isEmpty() ? "站点健康检查完成\n" : "员工站点异常告警\n")
                .append("总站点：").append(results.size()).append("\n")
                .append("正常：").append(results.size() - failures.size()).append("\n")
                .append("异常：").append(failures.size()).append("\n")
                .append("涉及员工：").append(failuresByEmployee.size()).append("\n")
                .append("新增异常：").append(newFailures).append("\n")
                .append("本轮恢复：").append(recovered).append("\n")
                .append("耗时：").append(String.format(Locale.ROOT, "%.1f 秒", durationMillis / 1000.0)).append("\n")
                .append("时间：").append(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(REPORT_TIME));
        if (!failures.isEmpty()) {
            int included = 0;
            content.append("\n\n异常员工：");
            for (Map.Entry<String, List<CheckResult>> employee : failuresByEmployee.entrySet()) {
                if (included >= 30) break;
                included++;
                content.append("\n- ").append(employee.getKey())
                        .append("：").append(employee.getValue().size()).append(" 个");
            }
            if (failuresByEmployee.size() > included) {
                content.append("\n- 其余 ").append(failuresByEmployee.size() - included).append(" 名员工请在 CyberFlow 查看");
            }
        }
        if (content.length() > 2950) content.setLength(2950);
        notificationService.sendToEnabled(NotificationPlatform.FEISHU,
                failures.isEmpty() ? "CyberFlow 站点健康检查" : "CyberFlow 站点异常汇总", content.toString());
    }

    private List<Target> filterTargets(List<Map<String, Object>> rows, Strategy strategy) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(strategy.minimumAgeDays());
        List<Target> targets = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            try {
                Target target = Target.from(row);
                if (!strategy.userGroups().isEmpty()
                        && !strategy.userGroups().contains(target.userGroup().toUpperCase(Locale.ROOT))) continue;
                if (strategy.excludedDomains().contains(target.domain())) continue;
                if (strategy.excludedServerIps().contains(target.serverIp())) continue;
                if (target.createdAt() != null && target.createdAt().isAfter(cutoff)) continue;
                targets.add(target);
            } catch (IllegalArgumentException e) {
                log.warn("Skipping invalid site-health target: domain={}, reason={}",
                        Target.text(row, "site_domain", "siteDomain"), e.getMessage());
            }
        }
        return targets;
    }

    private static HttpRequest request(String url, int timeoutSeconds) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5").GET().build();
    }

    static String normalizeDomain(String value) {
        String domain = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (domain.startsWith("http://") || domain.startsWith("https://")) domain = URI.create(domain).getHost();
        int slash = domain.indexOf('/');
        if (slash >= 0) domain = domain.substring(0, slash);
        if (domain == null || domain.isBlank()) throw new IllegalArgumentException("站点域名为空");
        domain = IDN.toASCII(domain);
        if (!domain.matches("(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")) {
            throw new IllegalArgumentException("无效站点域名: " + value);
        }
        return domain;
    }

    private static boolean isTransportFailure(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConnectException || current instanceof UnknownHostException || current instanceof HttpTimeoutException) return true;
            current = current.getCause();
        }
        return false;
    }

    private static String classify(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof UnknownHostException) return "DNS解析异常";
            if (current instanceof HttpTimeoutException) return "响应超时";
            if (current instanceof ConnectException) return "连接失败";
            current = current.getCause();
        }
        return "请求错误(" + safeMessage(error) + ")";
    }

    private static String imageReason(String url) {
        String path;
        try { path = URI.create(url).getPath(); }
        catch (IllegalArgumentException ignored) { path = url; }
        String name = path.replaceAll(".*/", "").replaceFirst("\\?.*$", "").toLowerCase(Locale.ROOT);
        if (name.matches("banner(?:-\\d+)?\\.(?:png|jpg|jpeg|webp)")) return "站点 Banner 图片缺失";
        if (name.matches("logo(?:-\\d+)?\\.(?:png|jpg|jpeg|webp)")) return "站点 Logo 图片缺失";
        return "站点商品图片缺失";
    }

    private static CheckResult ok(String domain, int status, URI uri, long latency) {
        return new CheckResult(domain, true, "正常", status, uri == null ? null : uri.toString(), latency, "", "");
    }

    private static CheckResult fail(String domain, Integer status, URI uri, String reason, long latency) {
        return new CheckResult(domain, false, reason, status, uri == null ? null : uri.toString(), latency, "", "");
    }

    private static long elapsedMillis(long startedNanos) { return (System.nanoTime() - startedNanos) / 1_000_000; }
    private static String safeMessage(Throwable error) {
        String message = error == null ? "未知错误" : error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        return message.substring(0, Math.min(240, message.length()));
    }
    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
    private static void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("站点检查已中断", e); }
    }

    record CheckResult(String domain, boolean healthy, String reason, Integer httpStatus, String finalUrl,
                       long latencyMs, String adminName, String serverName) {
        CheckResult withContext(String owner, String server) {
            return new CheckResult(domain, healthy, reason, httpStatus, finalUrl, latencyMs,
                    owner == null ? "" : owner, server == null ? "" : server);
        }
    }

    record Target(String domain, String serverName, String serverIp, String adminName, String userGroup,
                  LocalDateTime createdAt) {
        static Target from(Map<String, Object> row) {
            Object rawCreated = row.containsKey("created_at") ? row.get("created_at") : row.get("createdAt");
            LocalDateTime created = rawCreated instanceof LocalDateTime value ? value
                    : rawCreated instanceof java.sql.Timestamp value ? value.toLocalDateTime() : null;
            return new Target(normalizeDomain(text(row, "site_domain", "siteDomain")),
                    text(row, "server_name", "serverName"), text(row, "server_ip", "serverIp"),
                    text(row, "admin_name", "adminName"), text(row, "user_group", "userGroup"), created);
        }
        private static String text(Map<String, Object> row, String... keys) {
            for (String key : keys) if (row.get(key) != null) return String.valueOf(row.get(key)).trim();
            return "";
        }
    }

    record Strategy(Set<String> userGroups, Set<String> excludedDomains, Set<String> excludedServerIps,
                    int minimumAgeDays, int maxRetries, long retryDelayMillis, int maxParallelServers,
                    long sameServerDelayMillis, int connectTimeoutSeconds, int requestTimeoutSeconds,
                    int minimumBodyLength, boolean notifyEveryRun) {
        static Strategy from(Map<String, Object> values) {
            return new Strategy(set(values.get("userGroups"), true), set(values.get("excludeDomains"), false),
                    set(values.get("excludeServerIps"), false), integer(values, "minimumAgeDays", 7, 0, 365),
                    integer(values, "maxRetries", 5, 1, 10), integer(values, "retryDelaySeconds", 2, 0, 60) * 1000L,
                    integer(values, "maxParallelServers", 20, 1, 100), integer(values, "sameServerDelaySeconds", 3, 0, 60) * 1000L,
                    integer(values, "connectTimeoutSeconds", 10, 1, 60), integer(values, "requestTimeoutSeconds", 30, 2, 120),
                    integer(values, "minimumBodyLength", 1200, 0, 100000), bool(values.get("notifyEveryRun"), true));
        }
        private static Set<String> set(Object value, boolean upper) {
            Collection<?> raw = value instanceof Collection<?> collection ? collection
                    : value instanceof String text ? List.of(text.split(",")) : List.of();
            return raw.stream().map(String::valueOf).map(String::trim).filter(text -> !text.isEmpty())
                    .map(text -> upper ? text.toUpperCase(Locale.ROOT) : text.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        private static int integer(Map<String, Object> map, String key, int fallback, int min, int max) {
            try { return Math.max(min, Math.min(max, Integer.parseInt(String.valueOf(map.getOrDefault(key, fallback))))); }
            catch (NumberFormatException ignored) { return fallback; }
        }
        private static boolean bool(Object value, boolean fallback) {
            return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
        }
    }
}
