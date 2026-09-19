package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.states.ClientState;
import com.sharktower.bloodonthesharktower.timer.ClientTimerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** HUD presentation for the 0.5.0 timer backend. */
public final class TimerHUD {
    private TimerHUD() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!ClientState.isHudEnabled || !ClientTimerState.isActive || minecraft.player == null) return;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        String text = format(ClientTimerState.remainingSeconds) + (ClientTimerState.isPaused ? "  PAUSED" : "");
        int width = minecraft.font.width(text) + 18;
        int x = (screenWidth - width) / 2;
        int y = ClientState.voteInProgress || ClientState.nominationsOpen ? 66 : 8;
        UiDrawing.panel(graphics, x, y, width, 24);
        graphics.text(minecraft.font, text, x + 9, y + 8, ClientTimerState.isPaused ? UiDrawing.GOLD : UiDrawing.TEXT, true);
    }

    private static String format(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format("%d:%02d", safe / 60, safe % 60);
    }
}
