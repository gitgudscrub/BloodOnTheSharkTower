package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.event.KeyInputHandler;
import com.sharktower.bloodonthesharktower.core.GamePhase;
import com.sharktower.bloodonthesharktower.core.HandRaiseMode;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * One page that answers "what can I press, and what should I do now?".
 *
 * Every key is read from its live binding, so rebinding a control in
 * Minecraft's Controls menu shows up here. The "Right now" section changes
 * with the phase and with whether the viewer is the Storyteller.
 */
public class KeysHelpScreen extends Screen {
    private final Screen parent;

    public KeysHelpScreen(Screen parent) {
        super(Component.literal("Keys & Help"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    private boolean storyteller() {
        return this.minecraft != null && this.minecraft.player != null
                && ClientState.storytellerPlayers.contains(this.minecraft.player.getUUID());
    }

    /** Short, phase-aware advice. Plain words; no colour is needed to read it. */
    static List<String> nowHints(GamePhase phase, boolean storyteller, boolean handsOn) {
        List<String> hints = new ArrayList<>();
        if (storyteller) {
            switch (phase) {
                case SETUP -> {
                    hints.add("Seat players and pick a script in the Grimoire.");
                    hints.add("Fill the bag, then send roles from the Grimoire.");
                }
                case NIGHT -> {
                    hints.add("Work through the night order with the arrow keys.");
                    hints.add("Up arrow visits the highlighted player. Start Day when done.");
                }
                case DAY -> hints.add("Let players talk, then open nominations (Tools > Nomination Flow).");
                case NOMINATIONS -> hints.add("Shift+click two seats in the Grimoire to nominate, or let players do it.");
                case PLAYER_NOMINATED -> hints.add("Start the vote from the Grimoire or Nomination Flow.");
                case PLAYER_MARKED -> hints.add("Someone is on the block. Execute Marked when nominations end.");
                case CALL_FOR_EXILE, EXILE_SUPPORT -> hints.add("A Traveller exile is in progress (Tools > Traveller Exile).");
            }
        } else {
            switch (phase) {
                case SETUP -> hints.add("Wait for the Storyteller to send your role.");
                case NIGHT -> {
                    hints.add("Stay in your house. The Storyteller may visit you.");
                    hints.add("Check your role details if you need a reminder.");
                }
                case DAY -> hints.add("Talk with the town. Your notebook keeps private notes.");
                case NOMINATIONS, PLAYER_NOMINATED, PLAYER_MARKED ->
                        hints.add(handsOn ? "Raise your hand to vote when the clock reaches you."
                                : "Nominations are open. Ask the Storyteller to nominate.");
                case CALL_FOR_EXILE, EXILE_SUPPORT -> hints.add("A Traveller may be exiled. Raise your hand to support it.");
            }
        }
        return hints;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        boolean st = storyteller();
        int cx = this.width / 2;
        drawCentered(graphics, "Keys & Help", 10, UiDrawing.TEXT);

        int columnWidth = Math.min(190, (this.width - 36) / 2);
        int leftX = cx - columnWidth - 6;
        int rightX = cx + 6;
        int y = 28;

        // Right now.
        graphics.text(this.font, "Right now (" + phaseName(ClientState.phase()) + ")", leftX, y, UiDrawing.GOLD, true);
        y += 12;
        for (String hint : nowHints(ClientState.phase(), st, ClientState.handRaiseMode() != HandRaiseMode.OFF)) {
            for (FormattedCharSequence line : this.font.split(Component.literal("- " + hint), columnWidth * 2 + 12)) {
                graphics.text(this.font, line, leftX, y, UiDrawing.TEXT, false);
                y += 10;
            }
        }
        y += 6;

        int top = y;
        y = keySection(graphics, "Everyone", leftX, top, columnWidth, List.of(
                KeyInputHandler.openMyRoleDetailsKey, KeyInputHandler.toggleShowRole,
                KeyInputHandler.openScriptKey, KeyInputHandler.openCatalogKey,
                KeyInputHandler.openAssignGui, KeyInputHandler.toggleVoteHandKey,
                KeyInputHandler.leavePrivateChatKey, KeyInputHandler.openTimerKey,
                KeyInputHandler.openSettingsKey, KeyInputHandler.openHelpKey,
                KeyInputHandler.disableHudKey));
        int rightY = top;
        if (st) {
            rightY = keySection(graphics, "Storyteller", rightX, top, columnWidth, List.of(
                    KeyInputHandler.openStorytellerToolsKey, KeyInputHandler.toggleNightHudKey,
                    KeyInputHandler.nightHudPrevKey, KeyInputHandler.nightHudNextKey,
                    KeyInputHandler.nightHudActivateKey));
            rightY += 6;
            graphics.text(this.font, "Grimoire mouse", rightX, rightY, UiDrawing.GOLD, true);
            rightY += 12;
            for (String line : List.of("Left-click: edit role", "Right-click: player actions",
                    "Shift+Left: pick nominator", "Shift+Right: nominate")) {
                graphics.text(this.font, line, rightX, rightY, UiDrawing.TEXT, false);
                rightY += 10;
            }
        } else {
            graphics.text(this.font, "Good to know", rightX, rightY, UiDrawing.GOLD, true);
            rightY += 12;
            for (String line : List.of("Tab shows seats and pronouns.", "/nick sets your display name.",
                    "/pronouns sets your pronouns.", "Settings can hide any HUD.")) {
                graphics.text(this.font, line, rightX, rightY, UiDrawing.TEXT, false);
                rightY += 10;
            }
        }
        int bottom = Math.max(y, rightY) + 4;
        if (bottom < this.height - 44) {
            drawCentered(graphics, "Rebind any key in Options > Controls > Blood on the Sharktower.", this.height - 42, UiDrawing.MUTED);
        }
    }

    private int keySection(GuiGraphicsExtractor graphics, String title, int x, int y, int width, List<KeyMapping> keys) {
        graphics.text(this.font, title, x, y, UiDrawing.GOLD, true);
        y += 12;
        for (KeyMapping key : keys) {
            if (key == null) continue;
            if (y > this.height - 40) break;
            Component bound = key.isUnbound() ? Component.literal("not set") : key.getTranslatedKeyMessage();
            String keyText = "[" + bound.getString() + "]";
            int keyWidth = this.font.width(keyText);
            graphics.text(this.font, keyText, x, y, key.isUnbound() ? UiDrawing.MUTED : UiDrawing.GOLD, false);
            String label = Component.translatable(key.getName()).getString();
            int room = width - keyWidth - 6;
            if (this.font.width(label) > room) label = this.font.plainSubstrByWidth(label, room - 6) + "...";
            graphics.text(this.font, label, x + keyWidth + 6, y, UiDrawing.TEXT, false);
            y += 11;
        }
        return y;
    }

    private static String phaseName(GamePhase phase) {
        return switch (phase) {
            case SETUP -> "Setup";
            case NIGHT -> "Night";
            case DAY -> "Day";
            case NOMINATIONS -> "Nominations";
            case PLAYER_NOMINATED -> "Vote pending";
            case PLAYER_MARKED -> "Someone on the block";
            case CALL_FOR_EXILE, EXILE_SUPPORT -> "Traveller exile";
        };
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour) {
        graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, colour, true);
    }
}
