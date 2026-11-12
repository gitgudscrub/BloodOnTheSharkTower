package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.config.ClientSettings;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Player presentation preferences plus a clearly separated Storyteller section. */
public final class SharktowerSettingsScreen extends Screen {
    private static final int[] VOTE_SPEED_TICKS = {10, 15, 20, 25, 30, 40};
    private final Screen parent;

    public SharktowerSettingsScreen(Screen parent) {
        super(Component.literal("Sharktower Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int left = cx - 155;
        int mid = cx - 50;
        int right = cx + 55;
        int top = 54;
        int row = 28;

        addToggle(left, top, "Voice HUD", () -> ClientSettings.voiceHud, v -> ClientSettings.voiceHud = v);
        addToggle(mid, top, "Role HUD", () -> ClientSettings.roleHud, v -> ClientSettings.roleHud = v);
        addToggle(right, top, "Setup HUD", () -> ClientSettings.setupHud, v -> ClientSettings.setupHud = v);
        addToggle(left, top + row, "Voting HUD", () -> ClientSettings.electionHud, v -> ClientSettings.electionHud = v);
        addToggle(mid, top + row, "Hand HUD", () -> ClientSettings.handHud, v -> ClientSettings.handHud = v);
        addToggle(right, top + row, "Timer HUD", () -> ClientSettings.timerHud, v -> ClientSettings.timerHud = v);
        addToggle(left, top + row * 2, "Player List", () -> ClientSettings.playerListHud, v -> ClientSettings.playerListHud = v);
        addToggle(mid, top + row * 2, "Role Counts", () -> ClientSettings.roleCountsHud, v -> ClientSettings.roleCountsHud = v);
        addToggle(right, top + row * 2, "World Roles", () -> ClientSettings.worldRoleIcons, v -> ClientSettings.worldRoleIcons = v);
        addToggle(left, top + row * 3, "Pronouns", () -> ClientSettings.pronounTags, v -> ClientSettings.pronounTags = v);
        addToggle(mid, top + row * 3, "Role Names", () -> ClientSettings.grimoireLabels, v -> ClientSettings.grimoireLabels = v,
                "Show each role's name under its Grimoire token.");
        addToggle(right, top + row * 3, "G/E Letters", () -> ClientSettings.alignmentLetters, v -> ClientSettings.alignmentLetters = v,
                "Add G or E beside Good/Evil colours, for colour-blind players.");
        addToggle(left, top + row * 4, "Compact Cards", () -> ClientSettings.compactCards, v -> ClientSettings.compactCards = v,
                "Smaller role, nomination and hand cards, so you can play with them open.");

        if (isStoryteller()) {
            this.addRenderableWidget(SharktowerButton.builder(voteSpeedLabel(), b -> {
                int current = ClientState.voteStepTicks;
                int next = VOTE_SPEED_TICKS[0];
                for (int i = 0; i < VOTE_SPEED_TICKS.length; i++) {
                    if (current <= VOTE_SPEED_TICKS[i]) {
                        next = VOTE_SPEED_TICKS[(i + 1) % VOTE_SPEED_TICKS.length];
                        break;
                    }
                }
                ClientStorytellerActions.send("set_vote_step_ticks", Integer.toString(next));
                b.setMessage(Component.literal(String.format(Locale.ROOT, "Vote Speed: %.2fs / seat", next / 20.0D)));
            }).bounds(left, top + row * 5, 150, 20).build());

            this.addRenderableWidget(SharktowerButton.builder(Component.literal("Hand Scale -"), b -> {
                double next = Math.max(1.5D, ClientState.clockHandScale - 0.5D);
                ClientStorytellerActions.send("set_clock_scale", String.format(Locale.ROOT, "%.2f", next));
            }).bounds(mid + 55, top + row * 5, 76, 20).build());
            this.addRenderableWidget(SharktowerButton.builder(Component.literal("Hand Scale +"), b -> {
                double next = Math.min(8.0D, ClientState.clockHandScale + 0.5D);
                ClientStorytellerActions.send("set_clock_scale", String.format(Locale.ROOT, "%.2f", next));
            }).bounds(right + 79, top + row * 5, 76, 20).build());
        }

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Keys & Help"), b ->
                        minecraft.gui.setScreen(new KeysHelpScreen(this)))
                .bounds(cx - 155, height - 30, 100, 20).build());
        SharktowerButton theme = SharktowerButton.builder(Component.empty(), b -> {
            ClientSettings.theme = UiTheme.mode().next().name().toLowerCase(java.util.Locale.ROOT);
            ClientSettings.save();
            UiTheme.apply();
            b.setMessage(Component.literal("Theme: " + UiTheme.mode().label));
        }).bounds(cx - 50, height - 30, 100, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Candlelit is dark with gold frames. Parchment is light with ink text.")))
                .build();
        theme.setMessage(Component.literal("Theme: " + UiTheme.mode().label));
        this.addRenderableWidget(theme);
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx + 55, height - 30, 100, 20).build());
    }

    private void addToggle(int x, int y, String label, BoolGetter getter, BoolSetter setter, String tooltip) {
        addToggle(x, y, label, getter, setter).setTooltip(
                net.minecraft.client.gui.components.Tooltip.create(Component.literal(tooltip)));
    }

    private SharktowerButton addToggle(int x, int y, String label, BoolGetter getter, BoolSetter setter) {
        SharktowerButton button = SharktowerButton.builder(Component.empty(), b -> {
            setter.set(!getter.get());
            ClientSettings.save();
            b.setMessage(toggleLabel(label, getter.get()));
        }).bounds(x, y, 100, 20).build();
        button.setMessage(toggleLabel(label, getter.get()));
        this.addRenderableWidget(button);
        return button;
    }

    private Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "ON" : "OFF"));
    }

    private Component voteSpeedLabel() {
        return Component.literal(String.format(Locale.ROOT, "Vote Speed: %.2fs / seat", ClientState.voteStepTicks / 20.0D));
    }

    private boolean isStoryteller() {
        return minecraft != null && minecraft.player != null
                && ClientState.storytellerPlayers.contains(minecraft.player.getUUID());
    }

    @Override
    public void onClose() {
        ClientSettings.save();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        UiTheme.drawTitle(graphics, font, "Sharktower Settings", width / 2, 12);
        drawCentered(graphics, "Player HUD", 39, UiDrawing.GOLD, UiTheme.dark());
        if (isStoryteller()) {
            drawCentered(graphics, String.format(Locale.ROOT, "Storyteller Vote Presentation  ·  clock hands x%.2f",
                    ClientState.clockHandScale), 175, UiDrawing.GOLD, UiTheme.dark());
        } else {
            drawCentered(graphics, "Storyteller-only vote settings appear when you are the active Storyteller.",
                    175, UiDrawing.MUTED, false);
        }
        // The hint sits above Done only when the window is tall enough for it not to
        // collide with the Storyteller rows; small windows just lose the hint.
        if (height - 47 >= 54 + 28 * 5 + 40) {
            drawCentered(graphics, "All controls can also be rebound in Minecraft Controls → Blood on the Sharktower.",
                    height - 47, UiDrawing.MUTED, false);
        }
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        graphics.text(font, text, (width - font.width(text)) / 2, y, colour, shadow);
    }

    private interface BoolGetter { boolean get(); }
    private interface BoolSetter { void set(boolean value); }
}
