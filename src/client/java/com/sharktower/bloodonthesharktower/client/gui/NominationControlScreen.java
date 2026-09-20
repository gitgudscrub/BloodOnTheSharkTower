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

/** Storyteller-facing nomination, clockwise vote and execution console. */
public final class NominationControlScreen extends Screen {
    private UUID selectedNominator;
    private UUID selectedNominee;
    private boolean selectingNominator = true;

    public NominationControlScreen() {
        super(Component.literal("Nomination & Execution"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Open Noms").withStyle(ChatFormatting.GOLD), b -> action("nominations_open"))
                .bounds(cx - 245, 38, 115, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Close Noms").withStyle(ChatFormatting.GRAY), b -> action("nominations_close"))
                .bounds(cx - 125, 38, 115, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Pick Nominator"), b -> selectingNominator = true)
                .bounds(cx - 5, 38, 115, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Pick Nominee"), b -> selectingNominator = false)
                .bounds(cx + 115, 38, 115, 20).build());

        buildPlayerGrid();

        int actionY = Math.min(this.height - 126, 222);
        this.addRenderableWidget(Button.builder(Component.literal("Submit Nomination").withStyle(ChatFormatting.GOLD), b -> submitNomination())
                .bounds(cx - 185, actionY, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Start Vote").withStyle(ChatFormatting.AQUA), b -> action("vote_start"))
                .bounds(cx - 60, actionY, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Finish Vote").withStyle(ChatFormatting.GREEN), b -> action("vote_finish"))
                .bounds(cx + 65, actionY, 120, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Count -1"), b -> action("vote_override_delta", "-1"))
                .bounds(cx - 185, actionY + 25, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Clear Override"), b -> action("vote_override_clear"))
                .bounds(cx - 60, actionY + 25, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Count +1"), b -> action("vote_override_delta", "1"))
                .bounds(cx + 65, actionY + 25, 120, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel Vote"), b -> action("vote_cancel"))
                .bounds(cx - 185, actionY + 50, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel Nomination"), b -> action("nomination_cancel"))
                .bounds(cx - 60, actionY + 50, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("No Execution"), b -> action("no_execution"))
                .bounds(cx + 65, actionY + 50, 120, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Execute — Dies").withStyle(ChatFormatting.RED), b -> action("execute_marked"))
                .bounds(cx - 125, actionY + 75, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Execute — Lives").withStyle(ChatFormatting.GOLD), b -> action("execute_marked_survives"))
                .bounds(cx + 5, actionY + 75, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                .bounds(cx - 60, this.height - 27, 120, 20).build());
    }

    private void buildPlayerGrid() {
        List<Map.Entry<UUID, Integer>> seats = new ArrayList<>(ClientState.playerSeatNumbers.entrySet());
        seats.sort(Comparator.comparingInt(entry -> entry.getValue() == null ? Integer.MAX_VALUE : entry.getValue()));
        if (seats.isEmpty()) return;

        int columns = Math.min(4, Math.max(2, (seats.size() + 4) / 5));
        int buttonW = Math.min(120, Math.max(86, (this.width - 50) / columns - 5));
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
            String name = ClientState.playerName(id, seat);
            String prefix = ClientState.isExiledTraveler(id) ? "E "
                    : (ClientState.playerDeathStatus.getOrDefault(id, false) ? "X " : "");
            Component label = Component.literal(prefix + seat + " " + trim(name, 12));
            this.addRenderableWidget(Button.builder(label, b -> choosePlayer(id))
                    .bounds(startX + col * (buttonW + gap), startY + row * 23, buttonW, 20).build());
        }
    }

    private void choosePlayer(UUID id) {
        if (selectingNominator) {
            selectedNominator = id;
            selectingNominator = false;
        } else {
            selectedNominee = id;
        }
    }

    private void submitNomination() {
        if (selectedNominator == null || selectedNominee == null) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.literal("Choose both a nominator and nominee first."));
            }
            return;
        }
        action("nominate_pair", selectedNominator + "|" + selectedNominee);
    }

    private void action(String op) {
        action(op, "");
    }

    private void action(String op, String arg) {
        ClientStorytellerActions.send(op, arg);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        drawCentered(graphics, "NOMINATION & EXECUTION", 12, UiDrawing.GOLD, true);
        drawCentered(graphics,
                "Selection: " + (selectingNominator ? "NOMINATOR" : "NOMINEE")
                        + "   |   Required: " + ClientState.voteThreshold,
                25, UiDrawing.MUTED, false);

        String selected = "Nominator: " + label(selectedNominator) + "   →   Nominee: " + label(selectedNominee);
        drawCentered(graphics, selected, 63, UiDrawing.TEXT, true);

        int statusY = Math.min(this.height - 154, 204);
        if (ClientState.currentNominee != null) {
            drawCentered(graphics,
                    "ACTIVE: " + label(ClientState.currentNominator) + " → " + label(ClientState.currentNominee),
                    statusY, UiDrawing.GOLD, true);
        } else if (!"NONE".equals(ClientState.lastVoteResult) && ClientState.lastVoteNominee != null) {
            drawCentered(graphics,
                    "LAST: " + label(ClientState.lastVoteNominee) + " — " + prettyResult(ClientState.lastVoteResult)
                            + " (" + ClientState.lastVoteCount + ")",
                    statusY, UiDrawing.TEXT, true);
        } else {
            drawCentered(graphics, ClientState.nominationsOpen ? "Nominations open" : "Nominations closed",
                    statusY, UiDrawing.MUTED, false);
        }

        if (ClientState.voteInProgress) {
            String clock = ClientState.voteClockComplete
                    ? "CLOCK COMPLETE — " + ClientState.effectiveVoteCount + " counted"
                    : "Clock " + Math.min(ClientState.voteClockIndex + 1, ClientState.voteClockTotal)
                    + "/" + ClientState.voteClockTotal + " — " + label(ClientState.currentVoteClockPlayer);
            if (ClientState.voteCountOverrideActive) clock += "   OVERRIDE=" + ClientState.voteCountOverride;
            drawCentered(graphics, clock, statusY + 12,
                    ClientState.voteClockComplete ? 0xFF55CC66 : UiDrawing.TEXT, true);
        } else if (ClientState.markedForExecution != null) {
            drawCentered(graphics,
                    "ON THE BLOCK: " + label(ClientState.markedForExecution)
                            + " with " + ClientState.votesForMarkedPlayer + " vote(s)",
                    statusY + 12, 0xFFFFB347, true);
        }
    }

    private String label(UUID id) {
        if (id == null) return "—";
        int seat = ClientState.playerSeatNumbers.getOrDefault(id, 0);
        return ClientState.playerName(id, seat);
    }

    private static String prettyResult(String raw) {
        if (raw == null) return "";
        return raw.replace('_', ' ').toLowerCase();
    }

    private static String trim(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "…";
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, colour, shadow);
    }
}
