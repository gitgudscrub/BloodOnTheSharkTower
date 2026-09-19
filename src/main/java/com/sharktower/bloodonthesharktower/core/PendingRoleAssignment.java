package com.sharktower.bloodonthesharktower.core;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Optional;

/**
 * Port of BOTB's pending role assignment model.
 *
 * 0.3.2 restores the original packet shape: a custom-role flag, alignment
 * override, then either a custom role id or an official role id.
 */
public record PendingRoleAssignment(
        Role role,
        Optional<CustomRole> customRole,
        AlignmentOverride override
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, PendingRoleAssignment> PACKET_CODEC =
            new StreamCodec<>() {
                @Override
                public PendingRoleAssignment decode(RegistryFriendlyByteBuf buf) {
                    boolean custom = buf.readBoolean();

                    AlignmentOverride alignment;
                    try {
                        alignment = AlignmentOverride.valueOf(buf.readUtf());
                    } catch (IllegalArgumentException ex) {
                        alignment = AlignmentOverride.DEFAULT;
                    }

                    String roleId = buf.readUtf();
                    if (custom) {
                        return unresolvedCustomRole(roleId, alignment);
                    }

                    Role decodedRole = Role.findById(roleId);
                    if (decodedRole == null) decodedRole = Role.NO_ROLE;
                    return new PendingRoleAssignment(decodedRole, alignment);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, PendingRoleAssignment value) {
                    buf.writeBoolean(value.isCustomRole());
                    buf.writeUtf(value.override().name());
                    buf.writeUtf(value.getRoleId());
                }
            };

    public PendingRoleAssignment(Role role, AlignmentOverride override) {
        this(role, Optional.empty(), override);
    }

    public PendingRoleAssignment(CustomRole customRole, AlignmentOverride override) {
        this(Role.NO_ROLE, Optional.of(customRole), override);
    }

    public PendingRoleAssignment {
        role = role == null ? Role.NO_ROLE : role;
        customRole = customRole == null ? Optional.empty() : customRole;
        override = override == null ? AlignmentOverride.DEFAULT : override;
    }

    public boolean isCustomRole() {
        return customRole.isPresent();
    }

    public boolean isOfficialRole() {
        return !isCustomRole() && role != Role.NO_ROLE;
    }

    public String getRoleId() {
        return isCustomRole() ? customRole.get().id() : role.getId();
    }

    public String getDisplayName() {
        return isCustomRole() ? customRole.get().getDisplayName() : role.getDisplayName();
    }

    public RoleType getRoleType() {
        return isCustomRole() ? customRole.get().team() : role.getType();
    }

    public boolean isRoleDefaultGood() {
        return isCustomRole() ? customRole.get().isDefaultGood() : role.isDefaultGood();
    }

    public ScriptRole getScriptRole() {
        if (isCustomRole()) return new ScriptRole.Custom(customRole.get());
        if (role != Role.NO_ROLE) return new ScriptRole.Official(role);
        return null;
    }

    public boolean isFinalGood() {
        return switch (override) {
            case FORCE_GOOD -> true;
            case FORCE_BAD -> false;
            case DEFAULT -> isRoleDefaultGood();
        };
    }

    private static PendingRoleAssignment unresolvedCustomRole(String id, AlignmentOverride override) {
        CustomRole unresolved = new CustomRole(
                id, id, RoleType.TOWNSFOLK, "", "", List.of(),
                0.0, 0.0, "", "", List.of(), List.of(), false, List.of()
        );
        return new PendingRoleAssignment(Role.NO_ROLE, Optional.of(unresolved), override);
    }

    public PendingRoleAssignment resolveCustomRole(Script script) {
        if (!isCustomRole() || script == null) return this;
        String customRoleId = customRole.get().id();
        return script.getCustomRole(customRoleId)
                .map(role -> new PendingRoleAssignment(Role.NO_ROLE, Optional.of(role), override))
                .orElse(this);
    }
}
