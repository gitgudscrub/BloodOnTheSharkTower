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

    /** True when the hand box is on screen for this player (so other cards can skip its key hint). */
    public static boolean visible(Minecraft minecraft) {
        if (!ClientState.isHudEnabled || minecraft.player == null) return false;
        if (!com.sharktower.bloodonthesharktower.client.config.ClientSettings.handHud) return false;
        if (ClientState.handRaiseMode() == com.sharktower.bloodonthesharktower.core.HandRaiseMode.OFF) return false;
        UUID self = minecraft.player.getUUID();
        return ClientState.playerSeatNumbers.containsKey(self)
                && !ClientState.storytellerPlayers.contains(self)
                && !ClientState.isExiledTraveler(self);
    }

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!ClientState.isHudEnabled || minecraft.player == null) return;
        var mode = ClientState.handRaiseMode();
        if (mode == com.sharktower.bloodonthesharktower.core.HandRaiseMode.OFF) return;
        boolean exileVoting = ClientState.currentExileTarget != null || ClientState.exileSupportVote;

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
        if (mode == com.sharktower.bloodonthesharktower.core.HandRaiseMode.SPEAKING) {
            int position = ClientState.attentionHands.getOrDefault(self, 0);
            state = position > 0 ? "HAND RAISED — #" + position : "HAND LOWERED";
            hint = "[" + key + "] Join / Leave Speaking Queue";
            colour = position > 0 ? UiDrawing.GOLD : UiDrawing.MUTED;
        } else if (ghostUsed) {
            state = "GHOST VOTE USED";
            hint = "You cannot vote again.";
            colour = UiDrawing.DEAD;
        } else if (locked) {
            boolean countedYes = ClientState.lockedVotes.getOrDefault(self, false);
            state = exileVoting
                    ? (countedYes ? "EXILE SUPPORT — YES" : "EXILE SUPPORT — NO")
                    : (countedYes ? "VOTE COUNTED — YES" : "VOTE COUNTED — NO");
            hint = "The clock has passed your seat.";
            colour = countedYes ? UiDrawing.YES : UiDrawing.NO;
        } else {
            state = exileVoting
                    ? (raised ? "EXILE SUPPORT — YES" : "EXILE SUPPORT — NO")
                    : (raised ? "VOTE — YES" : "VOTE — NO");
            hint = "[" + key + "] Raise / Lower";
            colour = raised ? UiDrawing.YES : UiDrawing.NO;
        }

        boolean compact = com.sharktower.bloodonthesharktower.client.config.ClientSettings.compactCards;
        int pad = compact ? 12 : 18;
        int width = Math.max(compact ? 100 : 138, Math.max(minecraft.font.width(state), minecraft.font.width(hint)) + pad);
        int height = compact ? 26 : 30;
        int x = (HudScale.width(minecraft) - width) / 2;
        int y = HudScale.height(minecraft) - 70 + (30 - height);
        int top = compact ? 4 : 6;

        UiDrawing.panel(graphics, x, y, width, height);
        graphics.text(minecraft.font, state,
                x + width / 2 - minecraft.font.width(state) / 2, y + top, colour, true);
        graphics.text(minecraft.font, hint,
                x + width / 2 - minecraft.font.width(hint) / 2, y + top + 11, UiDrawing.TEXT, false);
    }
}
