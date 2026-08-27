package com.cyberflow.admin.common;

import com.cyberflow.admin.system.entity.SysUser;
import com.cyberflow.admin.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/** Resolves the backend-enforced row scope for the authenticated user. */
@Service
@RequiredArgsConstructor
public class DataScopeService {
    private final SysUserMapper userMapper;

    public DataScope current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("当前用户未认证");
        }

        return forUsername(authentication.getName());
    }

    public DataScope forUsername(String username) {
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new IllegalStateException("当前用户不存在");
        }

        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        if (roles.stream().anyMatch("ROLE_ADMIN"::equalsIgnoreCase)) {
            return DataScope.all();
        }

        // dataOwner is the explicit mapping to site_info.admin_name. The
        // username/nickname fallbacks keep existing installations usable.
        String ownerName = normalizeOwners(user.getDataOwner());
        if (ownerName.isBlank()) {
            ownerName = firstNonBlank(user.getUsername(), user.getNickname());
        }
        boolean operator = roles.stream().anyMatch("ROLE_OPERATOR"::equalsIgnoreCase);
        return new DataScope(false, operator, ownerName);
    }

    private static String normalizeOwners(String value) {
        if (value == null || value.isBlank()) return "";
        return java.util.Arrays.stream(value.split("[,，、\\n]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        // An unmapped account must see no rows, including unassigned data.
        return "\u0000";
    }
}
