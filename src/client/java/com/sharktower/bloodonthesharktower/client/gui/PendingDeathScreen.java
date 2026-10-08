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

/** Two-click resolution of a private death, with the server validating every action. */
public final class PendingDeathScreen extends Screen {
    private final UUID playerId;
    private final int seat;
    private boolean submitted;

    public PendingDeathScreen(UUID playerId, int seat) {
        super(Component.literal("Pending Death"));
        this.playerId = playerId;
        this.seat = seat;
    }

    public static boolean available(UUID playerId) {
        return ClientGrimoireEdits.isLocalStoryteller()
                && ClientState.phase() != GamePhase.SETUP && ClientState.phase() != GamePhase.NIGHT
                && ClientState.pendingDeaths.contains(playerId)
                && ClientState.playerDeathStatus.getOrDefault(playerId, false);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = Math.max(62, this.height / 2 - 38);
        int w = Math.min(150, (this.width - 30) / 2);
        Button reveal = Button.builder(Component.literal("Reveal Death").withStyle(ChatFormatting.RED),
                b -> resolve("reveal_death")).bounds(cx - w - 3, y, w, 20).build();
        Button revive = Button.builder(Component.literal("Revive").withStyle(ChatFormatting.GREEN),
                b -> resolve("revive_player")).bounds(cx + 3, y, w, 20).build();
        reveal.active = revive.active = available(playerId) && !submitted;
        this.addRenderableWidget(reveal);
        this.addRenderableWidget(revive);
        this.addRenderableWidget(Button.builder(Component.literal("Edit Role / Alignment"), b ->
                this.minecraft.gui.setScreen(new PlayerSetupScreen(playerId, seat, ClientGrimoireEdits.roleFor(playerId))))
                .bounds(cx - w - 3, y + 26, w, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Edit Reminders"), b ->
                this.minecraft.gui.setScreen(new ReminderChooseScreen(playerId, seat)))
                .bounds(cx + 3, y + 26, w, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Game Actions"), b ->
                this.minecraft.gui.setScreen(new GrimoirePlayerActionScreen(playerId, seat)))
                .bounds(cx - 90, y + 52, 180, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back to Grimoire"), b -> onClose())
                .bounds(cx - 90, this.height - 30, 180, 20).build());
    }

    private void resolve(String action) {
        if (submitted || !available(playerId)) return;
        submitted = true;
        this.rebuildWidgets();
        GrimoireReturnState.requestAfterNextGrimoireSync();
        ClientStorytellerActions.send(action, Integer.toString(seat));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = ClientState.playerName(playerId, seat) + " — Seat " + seat;
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 18, UiDrawing.GOLD, true);
        String status = "Dead? — death is not yet public";
        graphics.text(this.font, status, (this.width - this.font.width(status)) / 2, 34, UiDrawing.TEXT, true);
        String hint = "Reveal to everyone, or restore this player to life.";
        graphics.text(this.font, hint, (this.width - this.font.width(hint)) / 2, 48, UiDrawing.TEXT, true);
    }

    @Override
    public void onClose() {
        GrimoireReturnState.suppressNextReveal();
        this.minecraft.gui.setScreen(new AssignRolesScreen());
    }
}
