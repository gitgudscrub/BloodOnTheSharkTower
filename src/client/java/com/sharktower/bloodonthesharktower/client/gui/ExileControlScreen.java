package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
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

/** Storyteller console for Traveller exile calls and the dedicated exile vote clock. */
public final class ExileControlScreen extends Screen {
    private UUID selectedCaller;
    private UUID selectedTraveler;
    private boolean selectingCaller = true;

    public ExileControlScreen() {
        super(Component.literal("Traveller Exile"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Pick Caller"), b -> selectingCaller = true)
                .bounds(cx - 125, 38, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Pick Traveller"), b -> selectingCaller = false)
                .bounds(cx + 5, 38, 120, 20).build());

        buildPlayerGrid();

        int actionY = Math.min(this.height - 151, 222);
        this.addRenderableWidget(Button.builder(Component.literal("Call for Exile").withStyle(ChatFormatting.LIGHT_PURPLE), b -> submitExile())
                .bounds(cx - 185, actionY, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Start Exile Vote").withStyle(ChatFormatting.AQUA), b -> action("exile_start"))
                .bounds(cx - 60, actionY, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Resolve Exile").withStyle(ChatFormatting.GREEN), b -> action("exile_finish"))
                .bounds(cx + 65, actionY, 120, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Count -1"), b -> action("exile_override_delta", "-1"))
                .bounds(cx - 185, actionY + 25, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Clear Override"), b -> action("exile_override_clear"))
                .bounds(cx - 60, actionY + 25, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Count +1"), b -> action("exile_override_delta", "1"))
                .bounds(cx + 65, actionY + 25, 120, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel Vote"), b -> action("exile_cancel_vote"))
                .bounds(cx - 125, actionY + 50, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel Exile"), b -> action("exile_reset"))
                .bounds(cx + 5, actionY + 50, 120, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                .bounds(cx - 60, this.height - 27, 120, 20).build());
    }

    private void buildPlayerGrid() {
        List<Map.Entry<UUID, Integer>> seats = new ArrayList<>(ClientState.playerSeatNumbers.entrySet());
        seats.sort(Comparator.comparingInt(entry -> entry.getValue() == null ? Integer.MAX_VALUE : entry.getValue()));
        if (seats.isEmpty()) return;

        int columns = Math.min(4, Math.max(2, (seats.size() + 4) / 5));
        int buttonW = Math.min(125, Math.max(88, (this.width - 50) / columns - 5));
        int gap = 5;
        int totalW = columns * buttonW + (columns - 1) * gap;
        int startX = (this.width - totalW) / 2;
        int startY = 82;

        for (int i = 0; i < seats.size(); i++) {
            Map.Entry<UUID, Integer> entry = seats.get(i);
            UUID id = entry.getKey();
            int seat = entry.getValue() == null ? i + 1 : entry.getValue();
            int col = i % columns;
            int row = i / columns;
            boolean traveler = ClientState.canBeExiled.getOrDefault(id, false) || ClientState.isExiledTraveler(id);
            boolean exiled = ClientState.isExiledTraveler(id);
            String marker = exiled ? "E " : (traveler ? "T " : "");
            String dead = ClientState.playerDeathStatus.getOrDefault(id, false) ? "X " : "";
            Component label = Component.literal(marker + dead + seat + " " + trim(ClientState.playerName(id, seat), 11));
            this.addRenderableWidget(Button.builder(label, b -> choosePlayer(id))
                    .bounds(startX + col * (buttonW + gap), startY + row * 23, buttonW, 20).build());
        }
    }

    private void choosePlayer(UUID id) {
        if (selectingCaller) {
            if (ClientState.isExiledTraveler(id)) {
                message("An exiled Traveller cannot call another exile.");
                return;
            }
            selectedCaller = id;
            selectingCaller = false;
            return;
        }
        if (!ClientState.canBeExiled.getOrDefault(id, false)) {
            message(ClientState.isExiledTraveler(id)
                    ? "That Traveller has already been exiled."
                    : "The exile target must be an active Traveller.");
            return;
        }
        selectedTraveler = id;
    }

    private void submitExile() {
        if (selectedCaller == null || selectedTraveler == null) {
            message("Choose both an exile caller and an active Traveller first.");
            return;
        }
        action("exile_call_pair", selectedCaller + "|" + selectedTraveler);
    }

    private void action(String op) { action(op, ""); }

    private void action(String op, String arg) {
        ClientStorytellerActions.send(op, arg);
    }

    private void message(String text) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal(text));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawCentered(graphics, "TRAVELLER EXILE", 12, 0xFFFF55FF, true);
        drawCentered(graphics,
                "Selection: " + (selectingCaller ? "CALLER" : "TRAVELLER")
                        + "   |   Required: " + ClientState.voteThreshold,
                25, UiDrawing.MUTED, false);
        drawCentered(graphics,
                "Caller: " + label(selectedCaller) + "   →   Traveller: " + label(selectedTraveler),
                63, UiDrawing.TEXT, true);

        int statusY = Math.min(this.height - 179, 204);
        if (ClientState.currentExileTarget != null) {
            drawCentered(graphics,
                    "ACTIVE: " + label(ClientState.currentExileCaller) + " → " + label(ClientState.currentExileTarget),
                    statusY, 0xFFFF55FF, true);
            if (ClientState.exileSupportVote) {
                String clock = ClientState.voteClockComplete
                        ? "CLOCK COMPLETE — " + ClientState.effectiveVoteCount + "/" + ClientState.voteThreshold + " support"
                        : "Clock " + Math.min(ClientState.voteClockIndex + 1, ClientState.voteClockTotal)
                        + "/" + ClientState.voteClockTotal + " — " + label(ClientState.currentVoteClockPlayer);
                if (ClientState.voteCountOverrideActive) clock += "   OVERRIDE=" + ClientState.voteCountOverride;
                drawCentered(graphics, clock, statusY + 12,
                        ClientState.voteClockComplete ? 0xFF55CC66 : UiDrawing.TEXT, true);
            } else {
                drawCentered(graphics,
                        "Ready to start exile clock — " + ClientState.handsRaised + " hand(s) currently raised",
                        statusY + 12, UiDrawing.MUTED, false);
            }
        } else {
            long exiled = ClientState.exiledTravelers.values().stream().filter(Boolean.TRUE::equals).count();
            drawCentered(graphics, "No active exile call   |   Exiled Travellers: " + exiled,
                    statusY, UiDrawing.MUTED, false);
        }

        drawCentered(graphics, "T = active Traveller   E = exiled Traveller   X = dead player",
                statusY + 28, UiDrawing.MUTED, false);
    }

    private String label(UUID id) {
        if (id == null) return "—";
        int seat = ClientState.playerSeatNumbers.getOrDefault(id, 0);
        return ClientState.playerName(id, seat);
    }

    private static String trim(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "…";
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, colour, shadow);
    }
}
