package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.client.event.KeyInputHandler;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.client.gui.UiTheme;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 26.2 presentation port of BOTB's role box. */
public final class RoleHUD {
    private RoleHUD() {}

    /** Right edge of the role card, so other top HUDs can keep clear of it. */
    public static final int RIGHT = 10 + 200;
    /** Bottom edge of the role card. */
    public static final int BOTTOM = 50 + 74;

    /** True when the role card is on screen for this player. */
    public static boolean visible(Minecraft minecraft) {
        if (!ClientState.isHudEnabled || !ClientState.isRoleHudVisible || minecraft.player == null) return false;
        if ("none".equals(ClientState.displayRoleId())) return false;
        return UiDrawing.roleOf(ClientState.myAssignment) != null;
    }

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!visible(minecraft)) return;
        ScriptRole role = UiDrawing.roleOf(ClientState.myAssignment);

        // BOTB's full role box sits at x=10/y=50 and uses a 48px role token.
        int x = 10;
        int y = 50;
        int icon = 48;
        int width = 200;
        int height = 74;
        UiTheme.framedPanel(graphics, x, y, width, height);
        // Team colour as a thin ribbon down the left edge.
        graphics.fill(x + 1, y + 1, x + 4, y + height - 1, UiDrawing.opaque(UiDrawing.teamColor(ClientState.myAssignment.getRoleType())));

        UiTheme.roundToken(graphics, role, x + 12, y + 13, icon);

        int tx = x + 68;
        boolean dark = UiTheme.dark();
        graphics.text(minecraft.font, UiTheme.display(ClientState.displayRoleName().toUpperCase(java.util.Locale.ROOT)), tx, y + 10,
                dark ? UiTheme.GOLD : 0xFF5C4315, dark);
        graphics.text(minecraft.font, ClientState.myAssignment.getRoleType().getDisplayName().toUpperCase(java.util.Locale.ROOT)
                + "  ·  " + (ClientState.isGood() ? "GOOD" : "EVIL"), tx, y + 26,
                ClientState.isGood() ? UiDrawing.GOOD : UiDrawing.EVIL, dark);
        graphics.fill(tx, y + 38, x + width - 10, y + 39, UiTheme.GOLD_SHADOW);
        String detailsKey = KeyInputHandler.openMyRoleDetailsKey == null ? "X"
                : KeyInputHandler.openMyRoleDetailsKey.getTranslatedKeyMessage().getString();
        String hideKey = KeyInputHandler.toggleShowRole == null ? "Z"
                : KeyInputHandler.toggleShowRole.getTranslatedKeyMessage().getString();
        graphics.text(minecraft.font, detailsKey + ": details   " + hideKey + ": hide", tx, y + 54, UiDrawing.MUTED, false);
    }
}
