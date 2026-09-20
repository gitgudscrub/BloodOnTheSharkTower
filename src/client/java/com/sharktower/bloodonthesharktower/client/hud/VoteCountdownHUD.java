package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Large centre-screen 3/2/1 shown before the clockwise vote clock begins. */
public final class VoteCountdownHUD {
    private VoteCountdownHUD() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!ClientState.isHudEnabled || minecraft == null || minecraft.player == null) return;

        int countdown = ClientState.voteCountdownNumber();
        if (countdown <= 0) return;

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        String label = ClientState.exileSupportVote ? "EXILE VOTE STARTS IN" : "VOTE STARTS IN";

        int panelWidth = Math.max(132, minecraft.font.width(label) + 24);
        int panelHeight = 78;
        int x = (screenWidth - panelWidth) / 2;
        int y = Math.max(54, screenHeight / 2 - 72);

        UiDrawing.panel(graphics, x, y, panelWidth, panelHeight);
        graphics.text(
                minecraft.font,
                label,
                x + (panelWidth - minecraft.font.width(label)) / 2,
                y + 10,
                UiDrawing.GOLD,
                true
        );

        String number = Integer.toString(countdown);
        float scale = 4.0F;
        float scaledWidth = minecraft.font.width(number) * scale;

        graphics.pose().pushMatrix();
        graphics.pose().translate(screenWidth / 2.0F - scaledWidth / 2.0F, y + 28.0F);
        graphics.pose().scale(scale, scale);
        graphics.text(minecraft.font, number, 0, 0, UiDrawing.TEXT, true);
        graphics.pose().popMatrix();
    }
}
