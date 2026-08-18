package com.cyberflow.admin.common;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Effective row-level scope for the current operator.
 *
 * <p>Administrators receive an unrestricted scope. Other users are restricted
 * to the external administrator name configured on their account.</p>
 */
public record DataScope(boolean administrator, boolean operator, String ownerName) {
    public static DataScope all() {
        return new DataScope(true, false, null);
    }

    /** Supports the legacy single value and the new comma-separated values. */
    public List<String> ownerNames() {
        if (ownerName == null || ownerName.isBlank()) return List.of();
        return List.copyOf(Arrays.stream(ownerName.split("[,，、\\n]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }
}
