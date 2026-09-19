package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Shared UI drawing helpers.
 *
 * 0.6.1 intentionally follows the original BOTB visual language: vanilla
 * screen backgrounds/buttons, compact token grids, team-coloured token
 * backplates, and minimal dark overlays rather than large bespoke cards.
 */
public final class UiDrawing {
    public static final int PANEL = 0xD0101014;
    public static final int PANEL_SOFT = 0xB8202028;
    public static final int BORDER = 0xFF8E8E98;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFFB7B7C0;
    public static final int GOOD = 0xFF55AAFF;
    public static final int EVIL = 0xFFFF5555;
    public static final int GOLD = 0xFFFFD166;
    public static final int DEAD = 0xFF8A8A8A;
    public static final int BLACK = 0xFF000000;

    private static final Identifier EMPTY_ROLE_SLOT = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/icons/empty_role_slot.png");
    private static final Identifier EMPTY_REMINDER_SLOT = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/icons/empty_reminder_slot.png");

    private UiDrawing() {}

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.outline(x, y, width, height, BORDER);
    }

    public static void softPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_SOFT);
        graphics.outline(x, y, width, height, 0xFF5D5D68);
    }

    /** Draws the full role texture scaled to the requested square. */
    public static void roleIcon(GuiGraphicsExtractor graphics, ScriptRole role, int x, int y, int size) {
        if (role == null || role.getIcon() == null || size <= 0) return;
        // In 26.2 the final texture-size arguments define the source texture
        // coordinate space. Using 108 here sampled only the upper-left corner
        // of the original 108px role art. BOTB renders the whole texture.
        graphics.blit(RenderPipelines.GUI_TEXTURED, role.getIcon(), x, y, 0, 0, size, size, size, size);
    }

    public static void emptyRoleSlot(GuiGraphicsExtractor graphics, int x, int y, int size) {
        if (size <= 0) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, EMPTY_ROLE_SLOT, x, y, 0, 0, size, size, size, size);
    }

    public static void emptyReminderSlot(GuiGraphicsExtractor graphics, int x, int y, int size) {
        if (size <= 0) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, EMPTY_REMINDER_SLOT, x, y, 0, 0, size, size, size, size);
    }

    public static Identifier emptyRoleSlotTexture() {
        return EMPTY_ROLE_SLOT;
    }

    /**
     * Original BOTB-style token: team colour square/backplate with the icon
     * inset by one pixel. The original catalog uses 40px tiles with 38px art.
     */
    public static void roleToken(GuiGraphicsExtractor graphics, ScriptRole role, int x, int y, int size) {
        if (role == null || role.getIcon() == null) {
            emptyRoleSlot(graphics, x, y, size);
            return;
        }
        int colour = teamColor(role.getTeam());
        graphics.fill(x, y, x + size, y + size, opaque(colour));
        if (size > 2) roleIcon(graphics, role, x + 1, y + 1, size - 2);
    }

    public static ScriptRole roleOf(PendingRoleAssignment assignment) {
        return assignment == null ? null : assignment.getScriptRole();
    }

    public static int teamColor(RoleType type) {
        if (type == null) return TEXT;
        return type.getColor();
    }

    public static int opaque(int colour) {
        return colour | 0xFF000000;
    }

    public static String shortUuid(java.util.UUID uuid) {
        if (uuid == null) return "unknown";
        String raw = uuid.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }
}
