package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.core.GamePhase;
import com.sharktower.bloodonthesharktower.core.RoleCounts;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Public base role distribution shown at the top-centre of the in-world HUD.
 *
 * The values come only from the standard player-count table, never from the
 * actual assigned bag, so setup-changing characters do not leak information.
 */
public final class RoleDistributionHUD {
    private static final String SEPARATOR = "  ·  ";

    private RoleDistributionHUD() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!ClientState.isHudEnabled || minecraft.player == null || ClientState.gameEnding) return;
        if (minecraft.gui.screen() != null) return;
        // Keep the Setup card clean. The count strip is an in-game reference.
        if (ClientState.phase() == GamePhase.SETUP) return;

        RoleCounts.RoleCountInfo counts = RoleCounts.getCounts(ClientState.activePlayerCount);
        if (counts == null) return;

        Font font = minecraft.font;
        // Numbers with words, so the strip reads without telling the team colours apart.
        String[][] parts = {
                {counts.townsfolk() + " Townsfolk", counts.townsfolk() + " TF"},
                {counts.outsiders() + (counts.outsiders() == 1 ? " Outsider" : " Outsiders"), counts.outsiders() + " Out"},
                {counts.minions() + (counts.minions() == 1 ? " Minion" : " Minions"), counts.minions() + " Min"},
                {counts.demons() + (counts.demons() == 1 ? " Demon" : " Demons"), counts.demons() + " Dem"},
                ClientState.travelerCount > 0
                        ? new String[]{ClientState.travelerCount + (ClientState.travelerCount == 1 ? " Traveller" : " Travellers"),
                                ClientState.travelerCount + " Trav"}
                        : null
        };
        int[] colours = {RoleType.TOWNSFOLK.getColor(), RoleType.OUTSIDER.getColor(), RoleType.MINION.getColor(),
                RoleType.DEMON.getColor(), RoleType.TRAVELER.getColor()};
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int form = width(font, parts, 0) <= screenWidth - 40 ? 0 : 1;
        int x = (screenWidth - width(font, parts, form)) / 2;
        int y = 8;
        boolean first = true;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null) continue;
            if (!first) x = draw(graphics, font, SEPARATOR, x, y, 0xFFD0D0D0);
            x = draw(graphics, font, parts[i][form], x, y, colours[i]);
            first = false;
        }
    }

    private static int width(Font font, String[][] parts, int form) {
        int width = 0;
        boolean first = true;
        for (String[] part : parts) {
            if (part == null) continue;
            if (!first) width += font.width(SEPARATOR);
            width += font.width(part[form]);
            first = false;
        }
        return width;
    }

    private static int draw(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int colour) {
        graphics.text(font, text, x, y, colour, true);
        return x + font.width(text);
    }
}
