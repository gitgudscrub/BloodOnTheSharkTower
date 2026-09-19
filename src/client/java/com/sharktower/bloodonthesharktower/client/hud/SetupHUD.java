package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.core.GamePhase;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * BOTB-style setup helper: contextual and centered, not a permanent top-right
 * diagnostic card.
 */
public final class SetupHUD {
    private SetupHUD() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        // End-game reveal has its own presentation.  Without this guard the
        // normal setup card can sit behind/through the Final Grimoire whenever
        // a test game is ended while the server is still in SETUP.
        if (!ClientState.isHudEnabled
                || minecraft.player == null
                || ClientState.gameEnding
                || ClientState.phase() != GamePhase.SETUP
                // SEND ROLES commits the match before the first Dusk changes
                // the 0/0 day-night counters. At that point the Storyteller
                // phase bar takes over the top-centre presentation.
                || ClientState.activePlayerCount + ClientState.travelerCount > 0) return;

        String line1 = "Setup — " + ClientState.displayScriptName();
        String line2 = ClientState.seatedPlayerCount() + " seated player" + (ClientState.seatedPlayerCount() == 1 ? "" : "s");
        int contentWidth = Math.max(150, Math.max(minecraft.font.width(line1), minecraft.font.width(line2)) + 24);
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int x = (screenWidth - contentWidth) / 2;
        int y = 10;

        graphics.fill(x, y, x + contentWidth, y + 38, 0xBF000000);
        graphics.outline(x, y, contentWidth, 38, 0xFFE28A2B);
        graphics.text(minecraft.font, line1, x + (contentWidth - minecraft.font.width(line1)) / 2, y + 8, 0xFFFFFFFF, true);
        graphics.text(minecraft.font, line2, x + (contentWidth - minecraft.font.width(line2)) / 2, y + 22, 0xFFC8C8C8, false);
    }
}
