package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.client.event.KeyInputHandler;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.UUID;

/** Small player-facing reminder for the rebindable Sharktower voting hand. */
public final class HandVoteHUD {
    private HandVoteHUD() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!ClientState.isHudEnabled || minecraft.player == null) return;
        boolean exileVoting = ClientState.currentExileTarget != null || ClientState.exileSupportVote;
        if (!ClientState.nominationsOpen && !ClientState.voteInProgress && !exileVoting) return;

        UUID self = minecraft.player.getUUID();
        if (!ClientState.playerSeatNumbers.containsKey(self)) return;
        if (ClientState.storytellerPlayers.contains(self)) return;

        if (ClientState.isExiledTraveler(self)) return;
        boolean dead = ClientState.playerDeathStatus.getOrDefault(self, false);
        boolean ghostUsed = !exileVoting && dead && ClientState.hasUsedGhostVote.getOrDefault(self, false);
        boolean locked = (ClientState.voteInProgress || ClientState.exileSupportVote)
                && ClientState.lockedVotes.containsKey(self);
        boolean raised = ClientState.isHandRaised(self);
        String key = KeyInputHandler.toggleVoteHandKey == null
                ? "U" : KeyInputHandler.toggleVoteHandKey.getTranslatedKeyMessage().getString();

        String state;
        String hint;
        int colour;
        if (ghostUsed) {
            state = "GHOST VOTE USED";
            hint = "You cannot vote again.";
            colour = UiDrawing.DEAD;
        } else if (locked) {
            boolean countedYes = ClientState.lockedVotes.getOrDefault(self, false);
            state = exileVoting
                    ? (countedYes ? "EXILE SUPPORT — YES" : "EXILE SUPPORT — NO")
                    : (countedYes ? "VOTE COUNTED — YES" : "VOTE COUNTED — NO");
            hint = "The clock has passed your seat.";
            colour = countedYes ? UiDrawing.GOLD : UiDrawing.MUTED;
        } else {
            state = exileVoting
                    ? (raised ? "EXILE HAND RAISED" : "EXILE HAND LOWERED")
                    : (raised ? "HAND RAISED" : "HAND LOWERED");
            hint = "[" + key + "] Raise / Lower";
            colour = raised ? UiDrawing.GOLD : UiDrawing.MUTED;
        }

        int width = Math.max(138, Math.max(minecraft.font.width(state), minecraft.font.width(hint)) + 18);
        int height = 30;
        int x = (minecraft.getWindow().getGuiScaledWidth() - width) / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - 70;

        UiDrawing.panel(graphics, x, y, width, height);
        graphics.text(minecraft.font, state,
                x + width / 2 - minecraft.font.width(state) / 2, y + 6, colour, true);
        graphics.text(minecraft.font, hint,
                x + width / 2 - minecraft.font.width(hint) / 2, y + 17, UiDrawing.TEXT, false);
    }
}
