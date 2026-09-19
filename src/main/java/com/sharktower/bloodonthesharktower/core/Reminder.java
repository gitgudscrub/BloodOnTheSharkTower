package com.sharktower.bloodonthesharktower.core;

import java.util.Optional;
import java.util.UUID;

/**
 * Data-model port of BOTB Reminder.
 *
 * Rendering/icon helpers stay in the later UI batch; this keeps the original
 * grimoire reminder shape available to server/client state now.
 */
public record Reminder(
        String text,
        Optional<Role> role,
        Optional<String> customRoleId,
        Optional<UUID> playerUuid
) {
    public Reminder(String text, Optional<Role> role) {
        this(text, role, Optional.empty(), Optional.empty());
    }

    public Reminder(String text, Optional<Role> role, Optional<UUID> playerUuid) {
        this(text, role, Optional.empty(), playerUuid);
    }

    public Reminder {
        text = text == null ? "" : text;
        role = role == null ? Optional.empty() : role;
        customRoleId = customRoleId == null ? Optional.empty() : customRoleId;
        playerUuid = playerUuid == null ? Optional.empty() : playerUuid;
    }

    public static Reminder forCustomRole(String text, String customRoleId) {
        return new Reminder(text, Optional.empty(), Optional.ofNullable(customRoleId), Optional.empty());
    }

    public static Reminder forFabled(String text, String fabledId) {
        return forCustomRole(text, fabledId);
    }

    public boolean isPlayerReminder() {
        return playerUuid.isPresent();
    }

    public boolean isCustomRoleReminder() {
        return customRoleId.isPresent();
    }
}
