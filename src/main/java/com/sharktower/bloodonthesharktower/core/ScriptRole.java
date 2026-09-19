package com.sharktower.bloodonthesharktower.core;

import net.minecraft.resources.Identifier;

/**
 * BOTB script-role abstraction, ported to the 26.2 identifier type.
 *
 * Remote URL-backed textures are deliberately deferred to the client/UI port.
 */
public interface ScriptRole {
    String getId();
    String getDisplayName();
    RoleType getTeam();
    String getAbility();
    Identifier getIcon();
    boolean isDefaultGood();
    boolean isCustom();

    default Role asRole() {
        if (this instanceof Official official) return official.role();
        throw new IllegalStateException("Script role " + getId() + " is not an official role");
    }

    default CustomRole asCustomRole() {
        if (this instanceof Custom custom) return custom.customRole();
        throw new IllegalStateException("Script role " + getId() + " is not a custom role");
    }

    record Official(Role role) implements ScriptRole {
        @Override public String getId() { return role.getId(); }
        @Override public String getDisplayName() { return role.getDisplayName(); }
        @Override public RoleType getTeam() { return role.getType(); }
        @Override public String getAbility() { return role.getDescription(); }
        @Override public Identifier getIcon() { return role.getIcon(); }
        @Override public boolean isDefaultGood() { return role.isDefaultGood(); }
        @Override public boolean isCustom() { return false; }
    }

    record Custom(CustomRole customRole) implements ScriptRole {
        @Override public String getId() { return customRole.id(); }
        @Override public String getDisplayName() { return customRole.getDisplayName(); }
        @Override public RoleType getTeam() { return customRole.team(); }
        @Override public String getAbility() { return customRole.ability(); }

        @Override
        public Identifier getIcon() {
            if (customRole.imageUrls().isEmpty()) {
                Role official = Role.findById(customRole.id());
                if (official != null) return official.getIcon();
            }
            return Role.NO_ROLE.getIcon();
        }

        @Override public boolean isDefaultGood() { return customRole.isDefaultGood(); }
        @Override public boolean isCustom() { return true; }
    }

    record Fabled(NonPlayerCharacter fabledCharacter) implements ScriptRole {
        @Override public String getId() { return fabledCharacter.id(); }
        @Override public String getDisplayName() { return fabledCharacter.getDisplayName(); }
        @Override public RoleType getTeam() { return fabledCharacter.getRoleType(); }
        @Override public String getAbility() { return fabledCharacter.ability(); }

        @Override
        public Identifier getIcon() {
            if (fabledCharacter.imageUrl() == null || fabledCharacter.imageUrl().isEmpty()) {
                Role official = Role.findById(fabledCharacter.id());
                if (official != null) return official.getIcon();
            }
            return Role.NO_ROLE.getIcon();
        }

        @Override public boolean isDefaultGood() { return true; }
        @Override public boolean isCustom() { return true; }

        public boolean isFabled() { return fabledCharacter.isFabled(); }
        public boolean isLoric() { return fabledCharacter.isLoric(); }
    }
}
