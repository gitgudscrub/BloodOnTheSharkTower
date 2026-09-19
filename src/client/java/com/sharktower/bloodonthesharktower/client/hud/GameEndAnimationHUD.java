package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.client.gui.GameEndHoldingScreen;
import com.sharktower.bloodonthesharktower.client.gui.PlayerFaceCompat;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A.10.1 cinematic end-game reveal, ported from BOTB's original
 * GameEndAnimationHUD presentation.
 *
 * Sequence: world -> fade to black -> Victory/Defeat -> winning team ->
 * seat-ordered player/role reveals -> hold -> fade back to the persistent A.10
 * reveal state.
 */
public final class GameEndAnimationHUD {
    private static final long FADE_TO_BLACK_MS = 1_000L;
    private static final long BLACK_DELAY_MS = 400L;
    private static final long PLAYER_REVEAL_START_MS = 5_000L;
    private static final long PLAYER_REVEAL_STEP_MS = 1_000L;
    private static final long END_HOLD_MS = 5_000L;
    private static final long FADE_OUT_MS = 3_000L;

    private static boolean animating;
    private static long startedAt;
    private static String winner = "NONE";

    private GameEndAnimationHUD() {}

    public static void start(String winningTeam) {
        winner = normalizeWinner(winningTeam);
        startedAt = System.currentTimeMillis();
        animating = true;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gui != null) {
            minecraft.gui.setScreen(new GameEndHoldingScreen());
        }
    }

    public static void updateWinner(String winningTeam) {
        winner = normalizeWinner(winningTeam);
    }

    public static void reset() {
        animating = false;
        winner = "NONE";
        startedAt = 0L;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gui != null) minecraft.gui.setScreen(null);
    }

    public static boolean isAnimating() {
        return animating;
    }

    /**
     * @return true while the cinematic owns the frame and normal Sharktower HUD
     * elements should be suppressed.
     */
    public static boolean render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!animating || minecraft == null || minecraft.player == null) return false;

        List<PlayerReveal> players = playerData();
        long elapsed = Math.max(0L, System.currentTimeMillis() - startedAt);
        long revealDuration = players.size() * PLAYER_REVEAL_STEP_MS;
        long fadeOutStart = PLAYER_REVEAL_START_MS + revealDuration + END_HOLD_MS;
        long animationEnd = fadeOutStart + FADE_OUT_MS;

        if (elapsed >= animationEnd) {
            animating = false;
            if (minecraft.gui != null) minecraft.gui.setScreen(null);
            return false;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        float blackOpacity;
        if (elapsed < FADE_TO_BLACK_MS) {
            blackOpacity = clamp01(elapsed / (float) FADE_TO_BLACK_MS);
        } else if (elapsed < fadeOutStart) {
            blackOpacity = 1.0F;
        } else {
            blackOpacity = 1.0F - clamp01((elapsed - fadeOutStart) / (float) FADE_OUT_MS);
        }

        int blackAlpha = Math.round(255.0F * blackOpacity);
        graphics.fill(0, 0, screenWidth, screenHeight, argb(blackAlpha, 0x000000));

        long titleStart = FADE_TO_BLACK_MS + BLACK_DELAY_MS;
        if (elapsed < titleStart) return true;

        float contentAlpha = elapsed >= fadeOutStart
                ? 1.0F - clamp01((elapsed - fadeOutStart) / (float) FADE_OUT_MS)
                : 1.0F;

        renderTitles(graphics, minecraft, screenWidth, screenHeight, elapsed, contentAlpha);

        if (elapsed >= PLAYER_REVEAL_START_MS) {
            int revealElapsed = (int) (elapsed - PLAYER_REVEAL_START_MS);
            renderPlayers(graphics, minecraft, screenWidth, screenHeight, players, revealElapsed, contentAlpha);
        }

        return true;
    }

    private static void renderTitles(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            int screenWidth,
            int screenHeight,
            long elapsed,
            float alpha
    ) {
        boolean localWon = localPlayerWon(minecraft);
        String main = localWon ? "VICTORY" : "DEFEAT";
        int mainColour = localWon ? 0xFF00FF00 : 0xFFFF0000;
        int mainY = Math.max(24, screenHeight / 4 - 30);

        drawCentered(graphics, minecraft, main, screenWidth / 2, mainY,
                withAlpha(mainColour, alpha), true);

        long teamTitleStart = FADE_TO_BLACK_MS + BLACK_DELAY_MS + 1_100L;
        if (elapsed >= teamTitleStart) {
            boolean good = "GOOD".equals(winner);
            String team = "THE " + winner + " TEAM WINS";
            int teamColour = good ? UiDrawing.GOOD : UiDrawing.EVIL;
            drawCentered(graphics, minecraft, team, screenWidth / 2, mainY + 24,
                    withAlpha(teamColour, alpha), true);
        }
    }

    private static void renderPlayers(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            int screenWidth,
            int screenHeight,
            List<PlayerReveal> players,
            int revealElapsed,
            float globalAlpha
    ) {
        if (players.isEmpty()) return;

        final int perRow = 6;
        final int cellWidth = 82;
        final int cellHeight = 92;
        final int gapX = 8;
        final int gapY = 12;
        int rows = (players.size() + perRow - 1) / perRow;
        int gridHeight = rows * cellHeight + Math.max(0, rows - 1) * gapY;
        int startY = Math.max(92, (screenHeight - gridHeight) / 2 + 28);

        for (int i = 0; i < players.size(); i++) {
            long playerStart = i * PLAYER_REVEAL_STEP_MS;
            if (revealElapsed < playerStart) continue;

            float revealAlpha = clamp01((revealElapsed - playerStart) / (float) PLAYER_REVEAL_STEP_MS);
            revealAlpha *= globalAlpha;
            if (revealAlpha <= 0.02F) continue;

            int row = i / perRow;
            int col = i % perRow;
            int rowCount = Math.min(perRow, players.size() - row * perRow);
            int rowWidth = rowCount * cellWidth + Math.max(0, rowCount - 1) * gapX;
            int startX = (screenWidth - rowWidth) / 2;
            int x = startX + col * (cellWidth + gapX);
            int y = startY + row * (cellHeight + gapY);

            renderPlayer(graphics, minecraft, players.get(i), x, y, cellWidth, cellHeight, revealAlpha);
        }
    }

    private static void renderPlayer(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            PlayerReveal player,
            int x,
            int y,
            int width,
            int height,
            float alpha
    ) {
        int alignment = player.good ? UiDrawing.GOOD : UiDrawing.EVIL;
        graphics.fill(x, y, x + width, y + height, withAlpha(0xFF101014, alpha * 0.92F));
        graphics.outline(x, y, width, height, withAlpha(alignment, alpha));

        int headX = x + 8;
        int headY = y + 8;
        if (!PlayerFaceCompat.draw(graphics, player.id, headX, headY, 24)) {
            String seat = Integer.toString(player.seat);
            graphics.fill(headX, headY, headX + 24, headY + 24, withAlpha(0xFF222228, alpha));
            drawCentered(graphics, minecraft, seat, headX + 12, headY + 8,
                    withAlpha(UiDrawing.TEXT, alpha), true);
        }

        ScriptRole role = UiDrawing.roleOf(player.assignment);
        if (role != null) {
            UiDrawing.roleToken(graphics, role, x + width - 40, y + 5, 34);
        }

        String name = player.name;
        String roleName = player.assignment == null ? "No Role" : player.assignment.getDisplayName();
        String alignmentText = player.good ? "GOOD" : "EVIL";
        String status = player.exiled ? "EXILED" : (player.dead ? "DEAD" : "");

        drawCentered(graphics, minecraft, name, x + width / 2, y + 43,
                withAlpha(UiDrawing.TEXT, alpha), true);
        drawCentered(graphics, minecraft, roleName, x + width / 2, y + 56,
                withAlpha(player.assignment == null ? UiDrawing.MUTED : UiDrawing.teamColor(player.assignment.getRoleType()), alpha), true);
        drawCentered(graphics, minecraft, alignmentText, x + width / 2, y + 69,
                withAlpha(alignment, alpha), true);
        if (!status.isEmpty()) {
            drawCentered(graphics, minecraft, status, x + width / 2, y + 80,
                    withAlpha(UiDrawing.DEAD, alpha), true);
        }

        // Role/head render helpers do not expose alpha in 26.3. Mimic the
        // original one-by-one fade by lifting a black cover as the entry appears.
        if (alpha < 0.999F) {
            int cover = Math.round((1.0F - alpha) * 255.0F);
            graphics.fill(x, y, x + width, y + height, argb(cover, 0x000000));
        }
    }

    private static List<PlayerReveal> playerData() {
        Map<UUID, Integer> seats = ClientState.grimoireSeatNumbers.isEmpty()
                ? ClientState.playerSeatNumbers
                : ClientState.grimoireSeatNumbers;

        List<Map.Entry<UUID, Integer>> ordered = new ArrayList<>(seats.entrySet());
        ordered.sort(Comparator.comparingInt(entry -> entry.getValue() == null ? Integer.MAX_VALUE : entry.getValue()));

        List<PlayerReveal> result = new ArrayList<>();
        int fallback = 1;
        for (Map.Entry<UUID, Integer> entry : ordered) {
            UUID id = entry.getKey();
            int seat = entry.getValue() == null ? fallback : entry.getValue();
            PendingRoleAssignment assignment = ClientState.grimoireRoles.get(id);
            boolean good = assignment == null || assignment.isFinalGood();
            result.add(new PlayerReveal(
                    id,
                    seat,
                    ClientState.playerName(id, seat),
                    assignment,
                    good,
                    ClientState.playerDeathStatus.getOrDefault(id, false),
                    ClientState.isExiledTraveler(id)
            ));
            fallback++;
        }
        return result;
    }

    private static boolean localPlayerWon(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) return true;
        UUID localId = minecraft.player.getUUID();
        PendingRoleAssignment assignment = ClientState.grimoireRoles.get(localId);
        if (assignment == null || "none".equalsIgnoreCase(assignment.getRoleId())) {
            // Storytellers/spectators get the celebratory presentation rather
            // than being labelled defeated.
            return true;
        }
        boolean goodWon = "GOOD".equals(winner);
        return assignment.isFinalGood() == goodWon;
    }

    private static void drawCentered(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            String text,
            int centerX,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.text(minecraft.font, text, centerX - minecraft.font.width(text) / 2, y, colour, shadow);
    }

    private static String normalizeWinner(String value) {
        if (value == null) return "NONE";
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.equals("GOOD") || normalized.equals("EVIL") ? normalized : "NONE";
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int withAlpha(int colour, float alpha) {
        return argb(Math.round(255.0F * clamp01(alpha)), colour & 0x00FFFFFF);
    }

    private static int argb(int alpha, int rgb) {
        return ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    private record PlayerReveal(
            UUID id,
            int seat,
            String name,
            PendingRoleAssignment assignment,
            boolean good,
            boolean dead,
            boolean exiled
    ) {}
}
