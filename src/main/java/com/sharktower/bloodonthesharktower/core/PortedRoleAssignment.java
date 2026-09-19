package com.sharktower.bloodonthesharktower.core;

/**
 * Temporary bridge for ServerState while Role/CustomRole/ScriptRole are being
 * ported. This gets replaced by the original PendingRoleAssignment model in
 * the role-model milestone rather than becoming a new Sharktower design.
 */
public record PortedRoleAssignment(
        String roleId,
        String displayName,
        RoleType roleType,
        boolean roleDefaultGood,
        AlignmentOverride override
) {
    public boolean isFinalGood() {
        return switch (override) {
            case FORCE_GOOD -> true;
            case FORCE_BAD -> false;
            case DEFAULT -> roleDefaultGood;
        };
    }
}
