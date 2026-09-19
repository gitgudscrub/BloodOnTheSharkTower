package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.client.event.KeyInputHandler;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A.10 public end-game banner shown throughout the reveal period. */
public final class EndGameHUD {
    private EndGameHUD() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!ClientState.gameEnding || !ClientState.rolesRevealed || minecraft.player == null) return;

        String winner = ClientState.winningTeam == null ? "NONE" : ClientState.winningTeam;
        String title = winner + " WINS";
        String key = KeyInputHandler.openAssignGui == null ? "R"
                : KeyInputHandler.openAssignGui.getTranslatedKeyMessage().getString();
        String hint = "[" + key + "] Final Grimoire";

        int width = Math.max(190, Math.max(minecraft.font.width(title), minecraft.font.width(hint)) + 30);
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int x = (screenWidth - width) / 2;
        int y = 8;
        int colour = "GOOD".equals(winner) ? UiDrawing.GOOD : UiDrawing.EVIL;

        graphics.fill(x, y, x + width, y + 39, 0xE0101014);
        graphics.outline(x, y, width, 39, colour);
        graphics.text(minecraft.font, title,
                x + (width - minecraft.font.width(title)) / 2, y + 7, colour, true);
        graphics.text(minecraft.font, hint,
                x + (width - minecraft.font.width(hint)) / 2, y + 23, UiDrawing.TEXT, false);
    }
}
