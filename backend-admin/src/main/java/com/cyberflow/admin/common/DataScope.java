package com.cyberflow.admin.common;

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
}
