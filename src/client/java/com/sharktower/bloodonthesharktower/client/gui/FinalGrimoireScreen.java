package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only Final Grimoire shown after the end-game cinematic.
 *
 * A.10.3 deliberately mirrors the live Grimoire instead of using menu cards:
 * role tokens on the outside, player heads/names on the inside, seat numbers
 * beyond the tokens, and the result in the centre.  This keeps the final reveal
 * visually consistent with the screen the Storyteller has used all game.
 */
public final class FinalGrimoireScreen extends Screen {
    private static final int ROLE_SIZE = 32;
    private static final int HEAD_SIZE = 24;
    private static final int FOOTER_SAFE = 62;
    private static final int INNER_OFFSET = 47;
    private final Screen parent;

    public FinalGrimoireScreen(Screen parent) {
        super(Component.literal("Final Grimoire"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        boolean storyteller = this.minecraft != null
                && this.minecraft.player != null
                && ClientState.storytellerPlayers.contains(this.minecraft.player.getUUID());

        if (storyteller && ClientState.gameEnding) {
            this.addRenderableWidget(Button.builder(
                            Component.literal("Reset for Next Game").withStyle(ChatFormatting.GOLD),
                            b -> {
                                ClientStorytellerActions.send("reset_for_next_game");
                                this.minecraft.gui.setScreen(null);
                            })
                    .bounds(cx - 105, this.height - 30, 130, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> closeScreen())
                    .bounds(cx + 35, this.height - 30, 70, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> closeScreen())
                    .bounds(cx - 40, this.height - 30, 80, 20).build());
        }
    }

    private void closeScreen() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        List<Map.Entry<UUID, Integer>> seats = sortedSeats();
        if (seats.isEmpty()) {
            drawCentered(graphics, "FINAL GRIMOIRE", this.height / 2 - 12, UiDrawing.GOLD, true);
            drawCentered(graphics, "No seated players to reveal.", this.height / 2 + 4, UiDrawing.MUTED, false);
            return;
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Same geometry as the live Grimoire, pulled in slightly so the lower
        // seat number cannot collide with Reset/Close at 6 o'clock.
        int maxRadiusX = Math.max(54, centerX - FOOTER_SAFE);
        int maxRadiusY = Math.max(54, centerY - FOOTER_SAFE);
        int radius = Math.max(54, Math.min(maxRadiusX, maxRadiusY));
        int innerRadius = Math.max(24, radius - INNER_OFFSET);

        HoveredPlayer hovered = null;
        int count = seats.size();
        for (int i = 0; i < count; i++) {
            Map.Entry<UUID, Integer> entry = seats.get(i);
            UUID id = entry.getKey();
            int seat = entry.getValue() == null ? i + 1 : entry.getValue();
            double angle = (Math.PI * 2.0 / count) * i - Math.PI / 2.0;

            PendingRoleAssignment assignment = ClientState.grimoireRoles.get(id);
            ScriptRole role = UiDrawing.roleOf(assignment);
            boolean good = assignment == null || assignment.isFinalGood();
            boolean dead = ClientState.playerDeathStatus.getOrDefault(id, false);
            boolean exiled = ClientState.isExiledTraveler(id);

            int roleX = (int) Math.round(centerX + radius * Math.cos(angle)) - ROLE_SIZE / 2;
            int roleY = (int) Math.round(centerY + radius * Math.sin(angle)) - ROLE_SIZE / 2;
            int headX = (int) Math.round(centerX + innerRadius * Math.cos(angle)) - HEAD_SIZE / 2;
            int headY = (int) Math.round(centerY + innerRadius * Math.sin(angle)) - HEAD_SIZE / 2;

            renderRoleToken(graphics, role, assignment, good, dead, exiled, roleX, roleY);
            renderPlayerHead(graphics, id, seat, dead, headX, headY);
            renderSeatNumber(graphics, seat, centerX, centerY, radius, angle);

            if (hovered == null
                    && (contains(mouseX, mouseY, roleX - 3, roleY - 3, ROLE_SIZE + 6, ROLE_SIZE + 6)
                    || contains(mouseX, mouseY, headX - 3, headY - 3, HEAD_SIZE + 6, HEAD_SIZE + 18))) {
                hovered = new HoveredPlayer(id, seat, assignment, good, dead, exiled);
            }
        }

        renderCentre(graphics, hovered, seats.size());
    }

    private void renderRoleToken(
            GuiGraphicsExtractor graphics,
            ScriptRole role,
            PendingRoleAssignment assignment,
            boolean good,
            boolean dead,
            boolean exiled,
            int x,
            int y
    ) {
        UiDrawing.roleToken(graphics, role, x, y, ROLE_SIZE);

        // The role backplate shows character type; this outline shows the
        // player's actual final alignment, including alignment changes.
        int alignmentColour = good ? UiDrawing.GOOD : UiDrawing.EVIL;
        graphics.outline(x - 2, y - 2, ROLE_SIZE + 4, ROLE_SIZE + 4, alignmentColour);

        if (assignment == null) {
            graphics.text(this.font, "?", x + ROLE_SIZE / 2 - 2, y + ROLE_SIZE / 2 - 4, UiDrawing.TEXT, true);
        }
        if (exiled) {
            graphics.text(this.font, "E", x + ROLE_SIZE - 5, y - 6, UiDrawing.TEXT, true);
        } else if (dead) {
            graphics.text(this.font, "X", x + ROLE_SIZE - 5, y - 6, UiDrawing.DEAD, true);
        }
    }

    private void renderPlayerHead(
            GuiGraphicsExtractor graphics,
            UUID id,
            int seat,
            boolean dead,
            int x,
            int y
    ) {
        boolean drewFace = ClientState.connectedPlayers.contains(id)
                && PlayerFaceCompat.draw(graphics, id, x, y, HEAD_SIZE);
        if (!drewFace) {
            graphics.fill(x, y, x + HEAD_SIZE, y + HEAD_SIZE, 0xCC15151A);
            drawCenteredAt(graphics, Integer.toString(seat), x + HEAD_SIZE / 2, y + 7,
                    dead ? UiDrawing.DEAD : UiDrawing.TEXT, true);
        }

        graphics.outline(x, y, HEAD_SIZE, HEAD_SIZE, dead ? UiDrawing.DEAD : UiDrawing.TEXT);
        String name = ClientState.playerName(id, seat);
        drawCenteredAt(graphics, name, x + HEAD_SIZE / 2, y + HEAD_SIZE + 2,
                dead ? UiDrawing.DEAD : UiDrawing.TEXT, true);
    }

    private void renderSeatNumber(
            GuiGraphicsExtractor graphics,
            int seat,
            int centerX,
            int centerY,
            int radius,
            double angle
    ) {
        int seatRadius = radius + 18;
        int sx = (int) Math.round(centerX + seatRadius * Math.cos(angle));
        int sy = (int) Math.round(centerY + seatRadius * Math.sin(angle));
        drawCenteredAt(graphics, Integer.toString(seat), sx, sy - 4, UiDrawing.TEXT, true);
    }

    private void renderCentre(GuiGraphicsExtractor graphics, HoveredPlayer hovered, int playerCount) {
        int winnerColour = "GOOD".equals(ClientState.winningTeam) ? UiDrawing.GOOD : UiDrawing.EVIL;
        int cy = this.height / 2;

        drawCentered(graphics, "FINAL GRIMOIRE", cy - 26, UiDrawing.GOLD, true);
        drawCentered(graphics, ClientState.winningTeam + " WINS", cy - 13, winnerColour, true);

        if (hovered == null) {
            drawCentered(graphics, "Players: " + playerCount, cy + 5, UiDrawing.MUTED, false);
            drawCentered(graphics, "Hover a player for details", cy + 18, UiDrawing.MUTED, false);
            return;
        }

        String name = "Seat " + hovered.seat + " — " + ClientState.playerName(hovered.id, hovered.seat);
        String roleName = hovered.assignment == null ? "No Role" : hovered.assignment.getDisplayName();
        String alignment = hovered.good ? "GOOD" : "EVIL";
        String status = hovered.exiled ? "EXILED" : (hovered.dead ? "DEAD" : "ALIVE");

        drawCentered(graphics, name, cy + 4, UiDrawing.TEXT, true);
        drawCentered(graphics, roleName, cy + 17,
                hovered.assignment == null ? UiDrawing.MUTED : UiDrawing.teamColor(hovered.assignment.getRoleType()), true);
        drawCentered(graphics, alignment + "  •  " + status, cy + 30,
                hovered.good ? UiDrawing.GOOD : UiDrawing.EVIL, true);
    }

    private List<Map.Entry<UUID, Integer>> sortedSeats() {
        List<Map.Entry<UUID, Integer>> seats = new ArrayList<>(ClientState.grimoireSeatNumbers.entrySet());
        if (seats.isEmpty()) seats.addAll(ClientState.playerSeatNumbers.entrySet());
        seats.sort(Comparator.comparingInt(entry -> entry.getValue() == null ? Integer.MAX_VALUE : entry.getValue()));
        return seats;
    }

    private static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        drawCenteredAt(graphics, text, this.width / 2, y, colour, shadow);
    }

    private void drawCenteredAt(GuiGraphicsExtractor graphics, String text, int centerX, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, centerX - this.font.width(text) / 2, y, colour, shadow);
    }

    private record HoveredPlayer(
            UUID id,
            int seat,
            PendingRoleAssignment assignment,
            boolean good,
            boolean dead,
            boolean exiled
    ) {}
}
