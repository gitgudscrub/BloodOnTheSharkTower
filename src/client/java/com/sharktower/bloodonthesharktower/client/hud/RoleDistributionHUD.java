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
    private static final String SEPARATOR = " : ";

    private RoleDistributionHUD() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!ClientState.isHudEnabled || minecraft.player == null || ClientState.gameEnding) return;
        if (minecraft.gui.screen() != null) return;
        // Keep the Setup card clean. The count strip is an in-game reference.
        if (ClientState.phase() == GamePhase.SETUP) return;

        RoleCounts.RoleCountInfo counts = RoleCounts.getCounts(ClientState.activePlayerCount);
        if (counts == null) return;

        Font font = minecraft.font;
        String townsfolk = Integer.toString(counts.townsfolk());
        String outsiders = Integer.toString(counts.outsiders());
        String minions = Integer.toString(counts.minions());
        String demons = Integer.toString(counts.demons());
        String travelers = ClientState.travelerCount > 0 ? Integer.toString(ClientState.travelerCount) : null;

        int width = font.width(townsfolk)
                + font.width(SEPARATOR) + font.width(outsiders)
                + font.width(SEPARATOR) + font.width(minions)
                + font.width(SEPARATOR) + font.width(demons);
        if (travelers != null) width += font.width(SEPARATOR) + font.width(travelers);

        int x = (minecraft.getWindow().getGuiScaledWidth() - width) / 2;
        int y = 8;

        x = draw(graphics, font, townsfolk, x, y, RoleType.TOWNSFOLK.getColor());
        x = draw(graphics, font, SEPARATOR, x, y, 0xFFD0D0D0);
        x = draw(graphics, font, outsiders, x, y, RoleType.OUTSIDER.getColor());
        x = draw(graphics, font, SEPARATOR, x, y, 0xFFD0D0D0);
        x = draw(graphics, font, minions, x, y, RoleType.MINION.getColor());
        x = draw(graphics, font, SEPARATOR, x, y, 0xFFD0D0D0);
        x = draw(graphics, font, demons, x, y, RoleType.DEMON.getColor());
        if (travelers != null) {
            x = draw(graphics, font, SEPARATOR, x, y, 0xFFD0D0D0);
            draw(graphics, font, travelers, x, y, RoleType.TRAVELER.getColor());
        }
    }

    private static int draw(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int colour) {
        graphics.text(font, text, x, y, colour, true);
        return x + font.width(text);
    }
}
