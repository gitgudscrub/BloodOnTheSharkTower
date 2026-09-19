package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.client.gui.PlayerFaceCompat;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.states.ClientState;
import com.sharktower.bloodonthesharktower.voicechat.VoicechatIntegrationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Compact BOTC-style player sidebar.
 *
 * Shows connected seated players in seat order without exposing role data.
 * During a nomination/exile vote a raised (or already locked-in YES) vote is
 * represented by a small gold hand beside that player.
 */
public final class PlayerSidebarHUD {
    private static final int HEAD_SIZE = 12;
    private static final int ROW_HEIGHT = 16;
    private static final int PADDING = 5;
    private static final int MAX_WIDTH = 150;
    private static final int MIN_WIDTH = 92;
    private static final int HAND_WIDTH = 8;

    private PlayerSidebarHUD() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!ClientState.isHudEnabled || minecraft.player == null || ClientState.gameEnding) return;
        // This is an in-world at-a-glance HUD. Keep Storyteller/player screens clean.
        if (minecraft.gui.screen() != null) return;

        List<PlayerEntry> players = connectedSeatedPlayers();
        if (players.isEmpty()) return;

        Font font = minecraft.font;
        boolean electionVisible = hasElectionContext();
        int width = calculateWidth(font, players, electionVisible);
        int height = players.size() * ROW_HEIGHT + PADDING * 2;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = screenWidth - width - 8;
        int y = Math.max(8, (screenHeight - height) / 2);

        graphics.fill(x, y, x + width, y + height, 0x90000000);
        graphics.outline(x, y, width, height, 0x806E6E78);

        int rowY = y + PADDING;
        for (PlayerEntry player : players) {
            renderEntry(graphics, minecraft, player, x + PADDING, rowY, width - PADDING * 2, electionVisible);
            rowY += ROW_HEIGHT;
        }
    }

    private static List<PlayerEntry> connectedSeatedPlayers() {
        List<PlayerEntry> players = new ArrayList<>();
        for (UUID id : ClientState.connectedPlayers) {
            if (id == null || ClientState.storytellerPlayers.contains(id)) continue;
            int seat = ClientState.playerSeatNumbers.getOrDefault(id, 0);
            if (seat <= 0) continue;
            players.add(new PlayerEntry(id, seat, ClientState.playerName(id, seat)));
        }
        players.sort(Comparator.comparingInt(PlayerEntry::seat));
        return players;
    }

    private static int calculateWidth(Font font, List<PlayerEntry> players, boolean electionVisible) {
        int widest = MIN_WIDTH;
        for (PlayerEntry player : players) {
            int seatWidth = font.width(Integer.toString(player.seat()));
            int nameWidth = font.width(player.name());
            int content = seatWidth + 5 + HEAD_SIZE + 5 + nameWidth;
            if (electionVisible) content += 5 + HAND_WIDTH;
            widest = Math.max(widest, content + PADDING * 2);
        }
        return Math.min(MAX_WIDTH, widest);
    }

    private static void renderEntry(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            PlayerEntry player,
            int x,
            int y,
            int width,
            boolean electionVisible
    ) {
        Font font = minecraft.font;
        UUID id = player.id();
        boolean dead = ClientState.playerDeathStatus.getOrDefault(id, false);
        boolean currentVoter = ClientState.voteInProgress && id.equals(ClientState.currentVoteClockPlayer);

        if (currentVoter) {
            graphics.fill(x - 2, y, x + width + 2, y + ROW_HEIGHT - 1, 0x403C3210);
            graphics.outline(x - 2, y, width + 4, ROW_HEIGHT - 1, UiDrawing.GOLD);
        }

        String seatText = Integer.toString(player.seat());
        int textY = y + 4;
        int seatColour = dead ? UiDrawing.DEAD : UiDrawing.MUTED;
        graphics.text(font, seatText, x, textY, seatColour, false);

        int seatArea = Math.max(12, font.width(seatText));
        int headX = x + seatArea + 5;
        int headY = y + 2;
        boolean drewFace = PlayerFaceCompat.draw(graphics, id, headX, headY, HEAD_SIZE);
        if (!drewFace) {
            graphics.fill(headX, headY, headX + HEAD_SIZE, headY + HEAD_SIZE, 0xFF454550);
            String fallback = "?";
            graphics.text(font, fallback,
                    headX + (HEAD_SIZE - font.width(fallback)) / 2,
                    headY + 2, UiDrawing.TEXT, true);
        }
        if (dead) {
            graphics.fill(headX, headY, headX + HEAD_SIZE, headY + HEAD_SIZE, 0x70000000);
        }

        // Simple Voice Chat already tracks speaking state client-side. Draw the
        // highlight outside the existing 12px head so the portrait size itself
        // never changes or jumps while someone talks.
        if (VoicechatIntegrationState.isPlayerTalking(id)) {
            graphics.outline(headX - 1, headY - 1, HEAD_SIZE + 2, HEAD_SIZE + 2, 0xFFFFFFFF);
        }

        int nameX = headX + HEAD_SIZE + 5;
        int reservedRight = electionVisible ? HAND_WIDTH + 5 : 0;
        int nameMaxWidth = Math.max(8, x + width - reservedRight - nameX);
        drawNameScaledToFit(
                graphics,
                font,
                player.name(),
                nameX,
                textY,
                nameMaxWidth,
                dead ? UiDrawing.DEAD : UiDrawing.TEXT
        );

        if (electionVisible && shouldShowHand(id)) {
            int handX = x + width - HAND_WIDTH;
            int handY = y + 3;
            drawHand(graphics, handX, handY, UiDrawing.GOLD);
        }
    }

    /**
     * Before the vote clock reaches a player this mirrors their live raised hand.
     * Once their vote has locked, keep the hand visible only when that locked vote
     * was YES. This makes the sidebar an accurate record of who has actually voted.
     */
    private static boolean shouldShowHand(UUID id) {
        if (ClientState.lockedVotes.containsKey(id)) {
            return ClientState.lockedVotes.getOrDefault(id, false);
        }
        return ClientState.raisedHands.getOrDefault(id, false);
    }

    private static boolean hasElectionContext() {
        return ClientState.currentNominee != null
                || ClientState.voteInProgress
                || ClientState.currentExileTarget != null
                || ClientState.exileSupportInProgress
                || ClientState.exileSupportVote;
    }

    /** Tiny pixel hand so the indicator does not depend on an emoji font. */
    private static void drawHand(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        int dark = 0xFF6A4A00;
        // dark silhouette / outline
        graphics.fill(x + 1, y + 1, x + 3, y + 6, dark);
        graphics.fill(x + 3, y, x + 5, y + 6, dark);
        graphics.fill(x + 5, y + 2, x + 7, y + 7, dark);
        graphics.fill(x, y + 5, x + 7, y + 8, dark);
        // gold interior
        graphics.fill(x + 2, y + 2, x + 3, y + 6, colour);
        graphics.fill(x + 4, y + 1, x + 5, y + 6, colour);
        graphics.fill(x + 6, y + 3, x + 7, y + 6, colour);
        graphics.fill(x + 1, y + 5, x + 6, y + 7, colour);
    }

    /**
     * Draw the complete username, shrinking only when it would otherwise run
     * into the voting-hand column / edge of the sidebar. Short names stay at
     * vanilla size; long names smoothly scale down instead of being truncated.
     */
    private static void drawNameScaledToFit(
            GuiGraphicsExtractor graphics,
            Font font,
            String rawText,
            int x,
            int y,
            int maxWidth,
            int colour
    ) {
        String text = rawText == null || rawText.isBlank() ? "Player" : rawText;
        int naturalWidth = Math.max(1, font.width(text));
        float scale = Math.min(1.0F, maxWidth / (float) naturalWidth);

        if (scale >= 0.999F) {
            graphics.text(font, text, x, y, colour, true);
            return;
        }

        float scaledHeight = font.lineHeight * scale;
        float verticalOffset = (font.lineHeight - scaledHeight) * 0.5F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + verticalOffset);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, colour, true);
        graphics.pose().popMatrix();
    }

    private record PlayerEntry(UUID id, int seat, String name) {}
}
