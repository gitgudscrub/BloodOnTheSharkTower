package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.client.event.KeyInputHandler;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 26.2 presentation port of BOTB's role box. */
public final class RoleHUD {
    private RoleHUD() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!ClientState.isHudEnabled || !ClientState.isRoleHudVisible || minecraft.player == null) return;
        if ("none".equals(ClientState.displayRoleId())) return;

        ScriptRole role = UiDrawing.roleOf(ClientState.myAssignment);
        if (role == null) return;

        // BOTB's full role box sits at x=10/y=50 and uses a 48px role token.
        int x = 10;
        int y = 50;
        int icon = 48;
        int width = 200;
        int height = 74;
        int backplate = (ClientState.isGood() ? 0x8087CEEB : 0x80F08080);
        graphics.fill(x, y, x + width, y + height, backplate);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0xBF000000);

        UiDrawing.roleIcon(graphics, role, x + 10, y + 10, icon);
        graphics.outline(x + 9, y + 9, icon + 2, icon + 2, 0xFFFFFFFF);

        int tx = x + 68;
        graphics.text(minecraft.font, ClientState.displayRoleName(), tx, y + 10,
                UiDrawing.teamColor(ClientState.myAssignment.getRoleType()), true);
        graphics.text(minecraft.font, ClientState.myAssignment.getRoleType().getDisplayName(), tx, y + 24,
                0xFFAAAAAA, false);
        graphics.text(minecraft.font, ClientState.isGood() ? "GOOD" : "EVIL", tx, y + 38,
                ClientState.isGood() ? UiDrawing.GOOD : UiDrawing.EVIL, true);
        String detailsKey = KeyInputHandler.openMyRoleDetailsKey == null ? "X"
                : KeyInputHandler.openMyRoleDetailsKey.getTranslatedKeyMessage().getString();
        String hideKey = KeyInputHandler.toggleShowRole == null ? "Z"
                : KeyInputHandler.toggleShowRole.getTranslatedKeyMessage().getString();
        graphics.text(minecraft.font, detailsKey + ": details   " + hideKey + ": hide", tx, y + 54, 0xFFAAAAAA, false);
    }
}
