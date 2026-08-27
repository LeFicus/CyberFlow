package com.cyberflow.admin.dashboard.service;

import com.cyberflow.admin.common.DataScopeService;
import com.cyberflow.admin.dashboard.mapper.ProductQueryMapper;
import com.cyberflow.admin.dashboard.model.ProductFilter;
import com.cyberflow.admin.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductQueryService {
    private final ProductQueryMapper mapper;
    private final DataScopeService scopes;
    private final SysUserMapper users;

    public String username() { return SecurityContextHolder.getContext().getAuthentication().getName(); }

    /** Called explicitly for worker threads, never depends on inherited thread-local authentication. */
    public List<String> allowedDomains(String username) {
        var user = users.selectByUsername(username);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || !users.selectPermissionsByUserId(user.getId()).contains("dashboard:product:view"))
            throw new AccessDeniedException("当前账号无商品查看权限");
        var scope = scopes.forUsername(username);
        if (scope.administrator()) return null;
        if (scope.ownerNames().isEmpty()) return List.of();
        Set<String> domains = new TreeSet<>();
        for (String raw : mapper.scopeDomains(scope.ownerNames())) {
            if (raw == null || raw.isBlank()) continue;
            String domain = raw.trim().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            domains.add(domain); domains.add("www." + domain);
        }
        return List.copyOf(domains);
    }

    public long snapshot(Long requested) {
        if (requested != null && requested < 0) throw new IllegalArgumentException("无效的数据快照");
        return requested == null ? mapper.maxId() : Math.min(requested, mapper.maxId());
    }

    public Map<String, Object> search(ProductFilter raw, Long before, Long snapshotId, int size) {
        if (size < 1 || size > 200 || (before != null && before <= 0)) throw new IllegalArgumentException("无效的分页参数");
        ProductFilter filter = raw.normalized();
        long snapshot = snapshot(snapshotId);
        var rows = new ArrayList<>(mapper.search(filter, allowedDomains(username()), snapshot, before, size + 1));
        boolean hasMore = rows.size() > size;
        if (hasMore) rows.remove(rows.size() - 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", rows); result.put("hasMore", hasMore); result.put("snapshotId", Long.toString(snapshot));
        result.put("nextCursor", hasMore ? Objects.toString(rows.get(rows.size() - 1).get("id")) : null);
        return result;
    }
    public long count(ProductFilter filter, Long snapshot) {
        return mapper.count(filter.normalized(), allowedDomains(username()), snapshot(snapshot));
    }
    public List<String> domainOptions(String keyword) {
        String value = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 255) throw new IllegalArgumentException("域名过长");
        return mapper.domainOptions(ProductFilter.escapeLike(value) + "%", allowedDomains(username()));
    }
}
