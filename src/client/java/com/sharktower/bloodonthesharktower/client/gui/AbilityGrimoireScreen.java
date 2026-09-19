package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.core.GamePhase;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
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
 * Read-only true-Grimoire snapshot shown by Spy/Widow abilities.
 * It never touches the player's personal deduction Grimoire state.
 */
public final class AbilityGrimoireScreen extends Screen {
    private static final int ROLE_SIZE = 32;
    private static final int HEAD_SIZE = 24;
    private static final int REMINDER_SIZE = 16;

    private final Map<UUID, PendingRoleAssignment> roles;
    private final Map<UUID, Integer> seats;
    private final Map<UUID, List<Reminder>> reminders;
    private final String sourceRoleId;

    public AbilityGrimoireScreen(
            Map<UUID, PendingRoleAssignment> roles,
            Map<UUID, Integer> seats,
            Map<UUID, List<Reminder>> reminders,
            String sourceRoleId
    ) {
        super(Component.literal("True Grimoire"));
        this.roles = Map.copyOf(roles);
        this.seats = Map.copyOf(seats);
        this.reminders = Map.copyOf(reminders);
        this.sourceRoleId = sourceRoleId == null ? "" : sourceRoleId;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 45, this.height - 28, 90, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        // Ability-view information is only intended for the Night it was shown.
        if (ClientState.phase() != GamePhase.NIGHT || ClientState.gameEnding) {
            this.minecraft.gui.setScreen(null);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String source = pretty(sourceRoleId);
        String title = source.isBlank() ? "TRUE GRIMOIRE" : "TRUE GRIMOIRE — " + source;
        drawCentered(graphics, title, 10, UiDrawing.GOLD, true);
        drawCentered(graphics, "Read-only ability view — your personal Grimoire is unchanged.",
                23, UiDrawing.MUTED, false);

        List<Map.Entry<UUID, Integer>> ordered = orderedSeats();
        if (ordered.isEmpty()) {
            drawCentered(graphics, "No seated players.", this.height / 2, UiDrawing.MUTED, false);
            return;
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2 + 4;
        int radius = Math.max(58, Math.min(centerX, centerY) - 58);
        int innerRadius = Math.max(26, radius - 47);
        int count = ordered.size();

        for (int i = 0; i < count; i++) {
            Map.Entry<UUID, Integer> entry = ordered.get(i);
            UUID id = entry.getKey();
            int seat = entry.getValue() == null ? i + 1 : entry.getValue();
            double angle = (Math.PI * 2.0 / count) * i - Math.PI / 2.0;

            double tokenCenterX = centerX + radius * Math.cos(angle);
            double tokenCenterY = centerY + radius * Math.sin(angle);
            int roleX = (int) Math.round(tokenCenterX) - ROLE_SIZE / 2;
            int roleY = (int) Math.round(tokenCenterY) - ROLE_SIZE / 2;

            PendingRoleAssignment assignment = resolve(roles.get(id));
            ScriptRole role = UiDrawing.roleOf(assignment);
            UiDrawing.roleToken(graphics, role, roleX, roleY, ROLE_SIZE);

            boolean dead = ClientState.playerDeathStatus.getOrDefault(id, false);
            if (dead) {
                graphics.outline(roleX - 2, roleY - 2, ROLE_SIZE + 4, ROLE_SIZE + 4, UiDrawing.DEAD);
                graphics.text(this.font, "X", roleX + ROLE_SIZE / 2 - 3,
                        roleY + ROLE_SIZE / 2 - 4, UiDrawing.DEAD, true);
            }

            // Reminder tokens sit around the true role token exactly as information
            // on the physical Grimoire would be visible to a Spy/Widow.
            List<Reminder> playerReminders = reminders.getOrDefault(id, List.of());
            int reminderCount = Math.min(6, playerReminders.size());
            for (int r = 0; r < reminderCount; r++) {
                double reminderAngle = angle + (r - (reminderCount - 1) / 2.0) * 0.16;
                int reminderRadius = radius + 26;
                int rx = (int) Math.round(centerX + reminderRadius * Math.cos(reminderAngle)) - REMINDER_SIZE / 2;
                int ry = (int) Math.round(centerY + reminderRadius * Math.sin(reminderAngle)) - REMINDER_SIZE / 2;
                drawReminder(graphics, rx, ry, playerReminders.get(r));
            }

            int headX = (int) Math.round(centerX + innerRadius * Math.cos(angle)) - HEAD_SIZE / 2;
            int headY = (int) Math.round(centerY + innerRadius * Math.sin(angle)) - HEAD_SIZE / 2;
            boolean drewFace = ClientState.connectedPlayers.contains(id)
                    && PlayerFaceCompat.draw(graphics, id, headX, headY, HEAD_SIZE);
            if (!drewFace) {
                graphics.fill(headX, headY, headX + HEAD_SIZE, headY + HEAD_SIZE, 0xCC15151A);
                drawCenteredAt(graphics, Integer.toString(seat), headX + HEAD_SIZE / 2,
                        headY + 7, dead ? UiDrawing.DEAD : UiDrawing.TEXT, true);
            }
            graphics.outline(headX, headY, HEAD_SIZE, HEAD_SIZE, dead ? UiDrawing.DEAD : UiDrawing.TEXT);
            drawCenteredAt(graphics, ClientState.playerName(id, seat), headX + HEAD_SIZE / 2,
                    headY + HEAD_SIZE + 2, dead ? UiDrawing.DEAD : UiDrawing.TEXT, true);

            int seatRadius = radius + 22;
            int sx = (int) Math.round(centerX + seatRadius * Math.cos(angle));
            int sy = (int) Math.round(centerY + seatRadius * Math.sin(angle));
            drawCenteredAt(graphics, Integer.toString(seat), sx, sy - 4, UiDrawing.TEXT, true);
        }
    }

    private PendingRoleAssignment resolve(PendingRoleAssignment assignment) {
        if (assignment == null) return null;
        if (assignment.isCustomRole() && ClientState.currentScript != null) {
            return assignment.resolveCustomRole(ClientState.currentScript);
        }
        return assignment;
    }

    private List<Map.Entry<UUID, Integer>> orderedSeats() {
        List<Map.Entry<UUID, Integer>> out = new ArrayList<>(seats.entrySet());
        out.sort(Comparator.comparingInt(e -> e.getValue() == null ? Integer.MAX_VALUE : e.getValue()));
        return out;
    }

    private void drawReminder(GuiGraphicsExtractor graphics, int x, int y, Reminder reminder) {
        String text = reminder == null || reminder.text() == null ? "" : reminder.text();
        graphics.fill(x, y, x + REMINDER_SIZE, y + REMINDER_SIZE, 0xFFE6D6A8);
        graphics.outline(x, y, REMINDER_SIZE, REMINDER_SIZE, UiDrawing.BLACK);
        String mark = text.isBlank() ? "•" : text.substring(0, 1).toUpperCase();
        graphics.text(this.font, mark,
                x + (REMINDER_SIZE - this.font.width(mark)) / 2,
                y + 4, 0xFF111111, false);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        drawCenteredAt(graphics, text, this.width / 2, y, colour, shadow);
    }

    private void drawCenteredAt(GuiGraphicsExtractor graphics, String text, int centerX, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, centerX - this.font.width(text) / 2, y, colour, shadow);
    }

    private static String pretty(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String[] parts = raw.replace('-', ' ').replace('_', ' ').trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return out.toString();
    }
}
