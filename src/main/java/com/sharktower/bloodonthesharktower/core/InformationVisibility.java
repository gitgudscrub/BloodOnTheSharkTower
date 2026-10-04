package com.sharktower.bloodonthesharktower.core;
/** Bluff entitlement follows the known character as well as the actual role: never reveal a Marionette. */
public final class InformationVisibility {
    private InformationVisibility() {}
    public static boolean canSeeBluffs(PendingRoleAssignment actual, PendingRoleAssignment known) {
        if (actual==null || known==null) return false;
        RoleType type=actual.getRoleType();
        return (type==RoleType.DEMON || type==RoleType.MINION) && known.getRoleType()==type;
    }
}
