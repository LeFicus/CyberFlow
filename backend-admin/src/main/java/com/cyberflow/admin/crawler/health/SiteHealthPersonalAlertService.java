package com.cyberflow.admin.crawler.health;

import com.cyberflow.admin.crawler.health.mapper.SiteHealthMapper;
import com.cyberflow.admin.system.entity.SysUser;
import com.cyberflow.admin.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** Claims and builds the once-per-Beijing-day personal site-health reminder shown after login. */
@Service
@RequiredArgsConstructor
public class SiteHealthPersonalAlertService {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final int DETAIL_LIMIT = 20;

    private final SiteHealthMapper healthMapper;
    private final SysUserMapper userMapper;

    public record SiteItem(String domain, String reason, String serverName, int consecutiveFailures,
                           LocalDateTime failureSince, LocalDateTime checkedAt) {}

    public record PersonalAlert(String ownerName, long total, List<SiteItem> sites, long remaining) {}

    @Transactional
    public PersonalAlert claimForLogin(SysUser user) {
        String owner = user == null || user.getDataOwner() == null ? "" : user.getDataOwner().trim();
        if (user == null || user.getId() == null || owner.isBlank()) return null;

        long total = healthMapper.countUnhealthyByAdmin(owner);
        if (total <= 0) return null;
        LocalDate today = LocalDate.now(BEIJING);
        if (userMapper.markSiteHealthNoticeShown(user.getId(), today) == 0) return null;

        List<SiteItem> sites = healthMapper.listUnhealthyByAdmin(owner, DETAIL_LIMIT).stream()
                .map(SiteHealthPersonalAlertService::toItem)
                .toList();
        return new PersonalAlert(owner, total, sites, Math.max(0, total - sites.size()));
    }

    private static SiteItem toItem(Map<String, Object> row) {
        return new SiteItem(text(row, "site_domain"), text(row, "reason"), text(row, "server_name"),
                number(row.get("consecutive_failures")), dateTime(row.get("failure_since")),
                dateTime(row.get("checked_at")));
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return null;
    }
}
