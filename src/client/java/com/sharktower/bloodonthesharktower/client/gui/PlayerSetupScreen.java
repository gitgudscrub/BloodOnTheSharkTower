package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.AlignmentOverride;
import com.sharktower.bloodonthesharktower.core.GamePhase;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Reminder;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** Small interaction sheet opened by clicking a player's role token. */
public final class PlayerSetupScreen extends Screen {
    private final UUID playerId;
    private final int seat;
    private final PendingRoleAssignment assignment;

    public PlayerSetupScreen(UUID playerId, int seat, PendingRoleAssignment assignment) {
        super(Component.literal("Seat " + seat));
        this.playerId = playerId;
        this.seat = seat;
        this.assignment = assignment;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 70;
        int w = 150;
        int gap = 5;

        this.addRenderableWidget(Button.builder(Component.literal("Choose Role"), b ->
                        this.minecraft.gui.setScreen(RoleSelectionScreen.forSeat(seat, new AssignRolesScreen())))
                .bounds(cx - w - gap, y, w, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Clear Role"), b -> action("clear_role", Integer.toString(seat)))
                .bounds(cx + gap, y, w, 20).build());

        y += 32;
        this.addRenderableWidget(Button.builder(Component.literal("Default Alignment"), b -> action("alignment_default", Integer.toString(seat)))
                .bounds(cx - 155, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Force Good"), b -> action("alignment_good", Integer.toString(seat)))
                .bounds(cx - 50, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Force Evil"), b -> action("alignment_evil", Integer.toString(seat)))
                .bounds(cx + 55, y, 100, 20).build());

        y += 42;
        this.addRenderableWidget(Button.builder(Component.literal("Edit Reminders"), b ->
                        this.minecraft.gui.setScreen(new ReminderChooseScreen(playerId, seat)))
                .bounds(cx - 155, y, 205, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Clear Reminders"), b -> action("clear_reminders", Integer.toString(seat)))
                .bounds(cx + 55, y, 100, 20).build());

        if (ClientGrimoireEdits.isLocalStoryteller() && ClientState.phase() != GamePhase.SETUP) {
            y += 34;
            boolean dead = ClientState.playerDeathStatus.getOrDefault(playerId, false);
            if (dead) {
                this.addRenderableWidget(Button.builder(Component.literal("Revive Player"),
                                b -> action("revive_player", Integer.toString(seat)))
                        .bounds(cx - 100, y, 200, 20).build());
            } else if (ClientState.phase() == GamePhase.NIGHT) {
                this.addRenderableWidget(Button.builder(Component.literal("Mark Dead"),
                                b -> action("mark_dead", Integer.toString(seat)))
                        .bounds(cx - w - gap, y, w, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("Demon Kill"),
                                b -> action("demon_kill", Integer.toString(seat)))
                        .bounds(cx + gap, y, w, 20).build());
            } else {
                this.addRenderableWidget(Button.builder(Component.literal("Mark Dead"),
                                b -> action("mark_dead", Integer.toString(seat)))
                        .bounds(cx - 100, y, 200, 20).build());
            }
        }

        if (ClientGrimoireEdits.isLocalStoryteller() && ClientState.phase() != GamePhase.SETUP) {
            this.addRenderableWidget(Button.builder(Component.literal("Game Actions"), b ->
                            this.minecraft.gui.setScreen(new GrimoirePlayerActionScreen(playerId, seat)))
                    .bounds(cx - 100, this.height - 55, 200, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Back to Grimoire"), b -> returnToGrimoire())
                .bounds(cx - 100, this.height - 30, 200, 20).build());
    }

    private void action(String action, String argument) {
        if (ClientGrimoireEdits.isLocalStoryteller()) {
            GrimoireReturnState.requestAfterNextGrimoireSync();
            ClientStorytellerActions.send(action, argument);
            return;
        }

        switch (action) {
            case "clear_role" -> ClientGrimoireEdits.clearRole(playerId);
            case "alignment_default" -> ClientGrimoireEdits.setAlignment(playerId, AlignmentOverride.DEFAULT);
            case "alignment_good" -> ClientGrimoireEdits.setAlignment(playerId, AlignmentOverride.FORCE_GOOD);
            case "alignment_evil" -> ClientGrimoireEdits.setAlignment(playerId, AlignmentOverride.FORCE_BAD);
            case "clear_reminders" -> ClientGrimoireEdits.clearReminders(playerId);
            default -> { }
        }
        returnToGrimoire();
    }

    private void returnToGrimoire() {
        if (this.minecraft == null) return;
        GrimoireReturnState.suppressNextReveal();
        this.minecraft.gui.setScreen(new AssignRolesScreen());
    }

    @Override
    public void onClose() {
        returnToGrimoire();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        PendingRoleAssignment current = ClientGrimoireEdits.roleFor(playerId);
        String role = current == null || "norole".equalsIgnoreCase(current.getRoleId())
                ? "Unassigned"
                : current.getDisplayName();
        graphics.text(this.font, "Seat " + seat + " — " + role,
                (this.width - this.font.width("Seat " + seat + " — " + role)) / 2, 24, UiDrawing.GOLD, true);
        String playerName = ClientState.playerName(playerId, seat);
        graphics.text(this.font, playerName,
                (this.width - this.font.width(playerName)) / 2, 38, UiDrawing.MUTED, false);

        List<Reminder> reminders = ClientGrimoireEdits.remindersFor(playerId);
        String reminderText = reminders.isEmpty() ? "No reminders" : "Reminders: " + reminders.stream().map(Reminder::text).toList();
        graphics.text(this.font, reminderText,
                (this.width - this.font.width(reminderText)) / 2, 52, UiDrawing.MUTED, false);
    }
}
