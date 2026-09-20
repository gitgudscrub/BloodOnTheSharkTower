package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.GamePhase;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Large, keyboard-friendly Storyteller action sheet for one Grimoire player.
 *
 * Right-clicking a role token opens this screen. Modifier-click shortcuts on the
 * token remain available for fast operation, but every important action also has
 * a normal button here for accessibility.
 */
public final class GrimoirePlayerActionScreen extends Screen {
    private final UUID playerId;
    private final int seat;

    public GrimoirePlayerActionScreen(UUID playerId, int seat) {
        super(Component.literal("Player Actions"));
        this.playerId = playerId;
        this.seat = seat;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int w = 170;
        int gap = 6;
        int y = 62;

        if (!ClientGrimoireEdits.isLocalStoryteller()) {
            this.addRenderableWidget(Button.builder(Component.literal("Back to Grimoire"), b -> back())
                    .bounds(cx - 90, this.height - 30, 180, 20).build());
            return;
        }

        boolean setup = ClientState.phase() == GamePhase.SETUP;
        boolean dead = ClientState.playerDeathStatus.getOrDefault(playerId, false);
        boolean thisNominee = playerId.equals(ClientState.currentNominee);
        boolean thisMarked = playerId.equals(ClientState.markedForExecution);

        if (!setup) {
            Button setNominator = Button.builder(Component.literal("Set as Nominator").withStyle(ChatFormatting.GOLD), b -> {
                        GrimoireInteractionState.selectNominator(playerId);
                        back();
                    })
                    .bounds(cx - w - gap / 2, y, w, 20).build();
            setNominator.active = ClientState.nominationsOpen;
            this.addRenderableWidget(setNominator);

            Button nominate = Button.builder(Component.literal("Nominate This Player").withStyle(ChatFormatting.YELLOW), b -> {
                        UUID nominator = GrimoireInteractionState.selectedNominator();
                        if (nominator == null) {
                            message("Choose a nominator first (Shift + left-click a player portrait or role token).");
                            return;
                        }
                        ClientStorytellerActions.send("nominate_pair", nominator + "|" + playerId);
                        GrimoireInteractionState.clearNominator();
                        back();
                    })
                    .bounds(cx + gap / 2, y, w, 20).build();
            nominate.active = ClientState.nominationsOpen && GrimoireInteractionState.hasSelectedNominator();
            this.addRenderableWidget(nominate);

            y += 26;

            if (thisNominee && !ClientState.voteInProgress) {
                this.addRenderableWidget(Button.builder(Component.literal("Start Vote").withStyle(ChatFormatting.AQUA), b ->
                                actionAndBack("vote_start"))
                        .bounds(cx - w - gap / 2, y, w, 20).build());
            }

            if (thisNominee && ClientState.voteInProgress) {
                Button finishVote = Button.builder(Component.literal("Finish Vote").withStyle(ChatFormatting.GREEN), b ->
                                actionAndBack("vote_finish"))
                        .bounds(cx - w - gap / 2, y, w, 20).build();
                finishVote.active = ClientState.voteClockComplete;
                this.addRenderableWidget(finishVote);
            }

            if (ClientState.currentNominee != null || ClientState.voteInProgress) {
                this.addRenderableWidget(Button.builder(Component.literal("Cancel Nomination").withStyle(ChatFormatting.GRAY), b ->
                                actionAndBack("nomination_cancel"))
                        .bounds(cx + gap / 2, y, w, 20).build());
                y += 26;
            }

            if (thisMarked) {
                this.addRenderableWidget(Button.builder(Component.literal("Execute — DIES").withStyle(ChatFormatting.RED), b ->
                                actionAndBack("execute_marked"))
                        .bounds(cx - w - gap / 2, y, w, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("Execute — LIVES").withStyle(ChatFormatting.GOLD), b ->
                                actionAndBack("execute_marked_survives"))
                        .bounds(cx + gap / 2, y, w, 20).build());
                y += 26;
            }

            if (dead) {
                this.addRenderableWidget(Button.builder(Component.literal("Revive Player").withStyle(ChatFormatting.GREEN), b ->
                                actionAndBack("revive_player", Integer.toString(seat)))
                        .bounds(cx - 90, y, 180, 20).build());
                y += 26;
            } else if (ClientState.phase() == GamePhase.NIGHT) {
                this.addRenderableWidget(Button.builder(Component.literal("Mark Dead"), b ->
                                actionAndBack("mark_dead", Integer.toString(seat)))
                        .bounds(cx - w - gap / 2, y, w, 20).build());

                Button demonReminder = Button.builder(Component.literal("Demon Kill Reminder").withStyle(ChatFormatting.GOLD), b ->
                                DemonKillReminderScreen.openOrApply(playerId, seat))
                        .bounds(cx + gap / 2, y, w, 20).build();
                demonReminder.active = !DemonKillReminderScreen.demonsInStorytellerGrimoire().isEmpty();
                this.addRenderableWidget(demonReminder);
                y += 26;

                this.addRenderableWidget(Button.builder(Component.literal("Resolve Demon Kill").withStyle(ChatFormatting.RED), b ->
                                actionAndBack("demon_kill", Integer.toString(seat)))
                        .bounds(cx - 90, y, 180, 20).build());
                y += 26;
            } else {
                this.addRenderableWidget(Button.builder(Component.literal("Mark Dead").withStyle(ChatFormatting.RED), b ->
                                actionAndBack("mark_dead", Integer.toString(seat)))
                        .bounds(cx - 90, y, 180, 20).build());
                y += 26;
            }
        }

        this.addRenderableWidget(Button.builder(Component.literal("Edit Role / Alignment"), b ->
                        this.minecraft.gui.setScreen(new PlayerSetupScreen(
                                playerId, seat, ClientGrimoireEdits.roleFor(playerId))))
                .bounds(cx - w - gap / 2, y, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Edit Reminders"), b ->
                        this.minecraft.gui.setScreen(new ReminderChooseScreen(playerId, seat)))
                .bounds(cx + gap / 2, y, w, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back to Grimoire"), b -> back())
                .bounds(cx - 90, this.height - 30, 180, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String name = ClientState.playerName(playerId, seat);
        String heading = "Seat " + seat + " — " + name;
        graphics.text(this.font, heading,
                (this.width - this.font.width(heading)) / 2, 18, UiDrawing.GOLD, true);

        String status = ClientState.playerDeathStatus.getOrDefault(playerId, false) ? "DEAD" : "ALIVE";
        if (playerId.equals(ClientState.markedForExecution)) status += "   •   ON THE BLOCK";
        if (playerId.equals(ClientState.currentNominee)) status += "   •   CURRENT NOMINEE";
        graphics.text(this.font, status,
                (this.width - this.font.width(status)) / 2, 33,
                ClientState.playerDeathStatus.getOrDefault(playerId, false) ? UiDrawing.DEAD : UiDrawing.TEXT, false);

        UUID nominator = GrimoireInteractionState.selectedNominator();
        String nominatorText = nominator == null
                ? "No nominator selected"
                : "Selected nominator: " + ClientState.playerName(
                        nominator, ClientState.playerSeatNumbers.getOrDefault(nominator, 0));
        graphics.text(this.font, nominatorText,
                (this.width - this.font.width(nominatorText)) / 2, 46, UiDrawing.MUTED, false);
    }

    private void actionAndBack(String action) {
        ClientStorytellerActions.send(action);
        back();
    }

    private void actionAndBack(String action, String arg) {
        ClientStorytellerActions.send(action, arg);
        back();
    }

    private void back() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(new AssignRolesScreen());
    }

    private void message(String text) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
        }
    }
}
