package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Shared UI drawing helpers and the colour rule every screen follows.
 *
 * Buttons are {@link SharktowerButton}s (dark panel, soft border, gold hover ring).
 * Label colours mean something, so use them consistently:
 * <ul>
 *   <li>white: navigation, toggles and movement (Back, Done, Shuffle, Send to Seats);</li>
 *   <li>gold: the action that advances the game (Send Roles, Nominate, Start Vote);</li>
 *   <li>green: confirm or resolve (Execute - lives, Confirm);</li>
 *   <li>red: irreversible or evil-team actions (Game End, Execute - dies);</li>
 *   <li>grey/muted: cancel and secondary actions;</li>
 *   <li>light purple: Traveller and exile flows only;</li>
 *   <li>team colours are reserved for role tokens and names, never for buttons.</li>
 * </ul>
 */
public final class UiDrawing {
    // Theme-dependent (set by UiTheme.apply(); read at draw time, so not final).
    public static int PANEL = 0xD0101014;
    public static int PANEL_SOFT = 0xB8202028;
    public static int BORDER = 0xFF8E8E98;
    public static int TEXT = 0xFFFFFFFF;
    public static int MUTED = 0xFFB7B7C0;
    public static int GOLD = 0xFFFFD166;
    public static int DEAD = 0xFF8A8A8A;
    // Fixed meanings.
    public static final int GOOD = 0xFF55AAFF;
    public static final int EVIL = 0xFFFF5555;
    public static final int YES = 0xFF55FF55;
    public static final int NO = 0xFFFF5555;
    public static final int BLACK = 0xFF000000;

    private static final Identifier EMPTY_ROLE_SLOT = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/icons/empty_role_slot.png");
    private static final Identifier EMPTY_REMINDER_SLOT = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/icons/empty_reminder_slot.png");

    private UiDrawing() {}

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        UiTheme.framedPanel(graphics, x, y, width, height);
    }

    public static void softPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_SOFT);
        graphics.outline(x, y, width, height, UiTheme.GOLD_SHADOW);
    }

    /** Draws the full role texture scaled to the requested square. */
    public static void roleIcon(GuiGraphicsExtractor graphics, ScriptRole role, int x, int y, int size) {
        if (role == null || role.getIcon() == null || size <= 0) return;
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

    /** Round parchment token (the Clocktower look); an empty round slot when there is no role. */
    public static void roleToken(GuiGraphicsExtractor graphics, ScriptRole role, int x, int y, int size) {
        if (role == null || role.getIcon() == null) {
            UiTheme.emptyToken(graphics, x, y, size);
            return;
        }
        UiTheme.roundToken(graphics, role, x, y, size);
    }

    /**
     * Compact BOTC-style death marker over a player portrait.
     *
     * Only half of the supplied portrait is covered so the player's face stays
     * visible while the shroud remains an immediate dead-player cue.
     */
    public static void deathShroud(GuiGraphicsExtractor graphics, int x, int y, int size) {
        if (size <= 0) return;
        // Dim the portrait and hang the shroud at its top-left corner, as on tokens.
        graphics.fill(x, y, x + size, y + size, UiTheme.dark() ? 0x70120D14 : 0x50EBDFC3);
        UiTheme.shroud(graphics, x - 3, y - 3, Math.max(8, size / 2));
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
