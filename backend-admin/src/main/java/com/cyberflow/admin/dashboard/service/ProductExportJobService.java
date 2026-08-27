package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.dashboard.mapper.ProductQueryMapper;
import com.cyberflow.admin.dashboard.model.ProductFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class ProductExportJobService {
    @Data public static class Job {
        private String id, username, format, error;
        private volatile String state;
        private ProductFilter filters;
        private List<String> allowedDomains;
        private long snapshotId, createdAt, expiresAt, processed, bytes;
        private int maxRows, partRows, parts;
        private boolean limited;
    }
    private record Ticket(String jobId, long expiresAt) {}
    private final ProductQueryMapper mapper;
    private final ProductQueryService queries;
    private final ObjectMapper json;
    private final Path directory;
    private final ConcurrentMap<String, Job> jobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(3), r -> { Thread t = new Thread(r, "product-export"); t.setDaemon(true); return t; });
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "product-export-cleanup"); t.setDaemon(true); return t;
    });

    public ProductExportJobService(ProductQueryMapper mapper, ProductQueryService queries, ObjectMapper json,
            @Value("${cyberflow.product-export.directory:.data/product-exports}") String directory) {
        this.mapper = mapper; this.queries = queries; this.json = json; this.directory = Path.of(directory).toAbsolutePath();
    }
    @PostConstruct public void initialize() throws IOException {
        Files.createDirectories(directory);
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                Job job = json.readValue(path.toFile(), Job.class);
                UUID.fromString(job.id);
                if (active(job)) { job.state = "failed"; job.error = "后台重启中断了导出，请重新创建任务"; save(job); }
                if ("completed".equals(job.state) && !Files.isRegularFile(file(job, ".zip"))) {
                    job.state = "failed"; job.error = "导出文件不存在，请重新导出"; save(job);
                }
                jobs.put(job.id, job);
                Files.deleteIfExists(file(job, ".part"));
            }
        }
        cleanup(); cleaner.scheduleWithFixedDelay(this::cleanup, 10, 10, TimeUnit.MINUTES);
    }

    public synchronized Map<String, Object> create(ProductFilter raw, String format, int maxRows, int partRows, Long snapshot) throws IOException {
        if (!Set.of("csv", "xlsx").contains(format) || maxRows < 1 || maxRows > 5_000_000
                || partRows < 1000 || partRows > 100_000) throw new IllegalArgumentException("无效的导出设置");
        String username = queries.username();
        if (jobs.values().stream().anyMatch(j -> j.username.equals(username) && active(j)))
            throw new IllegalArgumentException("已有导出任务正在执行或排队，请等待完成或先取消");
        cleanup();
        if (jobs.size() >= 100 || directory.toFile().getUsableSpace() < 1_073_741_824L)
            throw new IllegalArgumentException("导出存储空间不足，请稍后再试或联系管理员");
        Job job = new Job(); job.id = UUID.randomUUID().toString(); job.username = username;
        job.filters = raw.normalized(); job.allowedDomains = queries.allowedDomains(username);
        job.snapshotId = queries.snapshot(snapshot); job.format = format; job.maxRows = maxRows; job.partRows = partRows;
        job.state = "queued"; job.createdAt = System.currentTimeMillis(); job.expiresAt = job.createdAt + TimeUnit.HOURS.toMillis(24);
        save(job); jobs.put(job.id, job);
        try { executor.execute(() -> run(job)); }
        catch (RejectedExecutionException ex) {
            jobs.remove(job.id); Files.deleteIfExists(file(job, ".json"));
            throw new IllegalArgumentException("导出队列已满，请稍后重试");
        }
        return view(job);
    }

    private void run(Job job) {
        try {
            check(job);
            synchronized (job) { checkCancelled(job); job.state = "running"; save(job); }
            long after = 0;
            try (OutputStream output = Files.newOutputStream(file(job, ".part"));
                 ProductArchiveWriter archive = new ProductArchiveWriter(output, job.format, job.partRows)) {
                while (true) {
                    check(job);
                    if (directory.toFile().getUsableSpace() < 536_870_912L) throw new IOException("导出磁盘空间不足");
                    if (Files.size(file(job, ".part")) > 4_294_967_296L) throw new IOException("单任务超过 4GB，请缩小范围");
                    int limit = (int)Math.min(500, job.maxRows - job.processed + 1);
                    var batch = mapper.exportBatch(job.filters, job.allowedDomains, job.snapshotId, after, limit);
                    for (var product : batch) {
                        checkCancelled(job);
                        if (job.processed >= job.maxRows) { job.limited = true; break; }
                        archive.write(product); job.processed++;
                        after = ((Number)product.get("id")).longValue();
                    }
                    synchronized (job) { job.parts = archive.parts(); save(job); }
                    if (job.limited || batch.size() < limit) break;
                }
                synchronized (job) { checkCancelled(job); job.state = "packaging"; save(job); }
            }
            synchronized (job) {
                check(job);
                Files.move(file(job, ".part"), file(job, ".zip"), StandardCopyOption.REPLACE_EXISTING);
                job.bytes = Files.size(file(job, ".zip")); job.parts = Math.max(1, job.parts);
                job.state = "completed"; save(job);
            }
        } catch (Exception ex) {
            synchronized (job) {
                if (!"cancelled".equals(job.state)) { job.state = "failed"; job.error = ex instanceof AccessDeniedException
                        ? "账号权限或数据范围已变化，请重新导出" : "导出失败，请缩小筛选范围或稍后重试"; }
                log.warn("Product export {} stopped: {}", job.id, ex.toString());
                try { Files.deleteIfExists(file(job, ".part")); Files.deleteIfExists(file(job, ".zip")); save(job); }
                catch (IOException io) { log.warn("Unable to clean up export {}", job.id, io); }
            }
        }
    }
    private void checkCancelled(Job job) {
        if ("cancelled".equals(job.state) || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
    private void check(Job job) {
        checkCancelled(job);
        if (!Objects.equals(job.allowedDomains, queries.allowedDomains(job.username))) throw new AccessDeniedException("数据范围已变化");
    }
    private Job owned(String id) {
        Job job = jobs.get(id);
        if (job == null || job.expiresAt <= System.currentTimeMillis()) throw new IllegalArgumentException("导出任务不存在或已过期");
        if (!job.username.equals(queries.username())) throw new AccessDeniedException("无权访问该导出任务");
        return job;
    }
    public List<Map<String, Object>> list() {
        String username = queries.username();
        return jobs.values().stream().filter(j -> j.username.equals(username) && j.expiresAt > System.currentTimeMillis())
                .sorted(Comparator.comparingLong(Job::getCreatedAt).reversed()).limit(20).map(this::view).toList();
    }
    public Map<String, Object> get(String id) { return view(owned(id)); }
    public void cancel(String id) throws IOException {
        Job job = owned(id);
        synchronized (job) { if (active(job)) { job.state = "cancelled"; save(job); } }
    }
    public Map<String, String> ticket(String id) {
        Job job = owned(id); check(job);
        if (!"completed".equals(job.state)) throw new IllegalArgumentException("导出尚未完成");
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt < System.currentTimeMillis());
        if (tickets.size() >= 1000) throw new IllegalArgumentException("下载请求过多，请稍后重试");
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        tickets.put(token, new Ticket(id, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2)));
        return Map.of("url", "/admin/dashboard/product-exports/download?ticket=" + token);
    }
    public Path consumeTicket(String token) {
        Ticket ticket = token == null ? null : tickets.remove(token);
        if (ticket == null || ticket.expiresAt < System.currentTimeMillis()) throw new AccessDeniedException("下载链接无效或已过期");
        Job job = jobs.get(ticket.jobId);
        if (job == null || job.expiresAt <= System.currentTimeMillis() || !"completed".equals(job.state))
            throw new IllegalArgumentException("导出文件已过期");
        check(job);
        return file(job, ".zip");
    }
    private Map<String, Object> view(Job job) {
        synchronized (job) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", job.id); v.put("format", job.format); v.put("state", job.state); v.put("filters", job.filters);
            v.put("processed", job.processed); v.put("maxRows", job.maxRows); v.put("partRows", job.partRows);
            v.put("parts", job.parts); v.put("bytes", job.bytes); v.put("limited", job.limited); v.put("error", job.error);
            v.put("createdAt", Instant.ofEpochMilli(job.createdAt).toString()); v.put("expiresAt", Instant.ofEpochMilli(job.expiresAt).toString());
            return v;
        }
    }
    private boolean active(Job j) { return Set.of("queued", "running", "packaging").contains(j.state); }
    private Path file(Job job, String suffix) { return directory.resolve(job.id + suffix); }
    private void save(Job job) throws IOException {
        Path temp = file(job, ".json.tmp"); json.writeValue(temp.toFile(), job);
        Files.move(temp, file(job, ".json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    private void cleanup() {
        for (Job job : jobs.values()) synchronized (job) {
            if (!active(job) && job.expiresAt < System.currentTimeMillis()) try {
                Files.deleteIfExists(file(job, ".zip")); Files.deleteIfExists(file(job, ".part")); Files.deleteIfExists(file(job, ".json"));
                jobs.remove(job.id, job);
            } catch (IOException ex) { log.warn("Unable to expire product export {}", job.id); }
        }
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt < System.currentTimeMillis());
    }
    @PreDestroy public void shutdown() { cleaner.shutdownNow(); executor.shutdownNow(); }
}
