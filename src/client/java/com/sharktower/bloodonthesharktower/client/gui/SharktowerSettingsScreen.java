package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.config.ClientSettings;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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

        if (isStoryteller()) {
            this.addRenderableWidget(Button.builder(voteSpeedLabel(), b -> {
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
            }).bounds(left, top + row * 4, 150, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Clock Scale -"), b -> {
                double next = Math.max(1.5D, ClientState.clockHandScale - 0.5D);
                ClientStorytellerActions.send("set_clock_scale", String.format(Locale.ROOT, "%.2f", next));
            }).bounds(mid + 55, top + row * 4, 72, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Clock Scale +"), b -> {
                double next = Math.min(8.0D, ClientState.clockHandScale + 0.5D);
                ClientStorytellerActions.send("set_clock_scale", String.format(Locale.ROOT, "%.2f", next));
            }).bounds(right + 73, top + row * 4, 82, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 100, height - 30, 200, 20).build());
    }

    private void addToggle(int x, int y, String label, BoolGetter getter, BoolSetter setter) {
        Button button = Button.builder(Component.empty(), b -> {
            setter.set(!getter.get());
            ClientSettings.save();
            b.setMessage(toggleLabel(label, getter.get()));
        }).bounds(x, y, 100, 20).build();
        button.setMessage(toggleLabel(label, getter.get()));
        this.addRenderableWidget(button);
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
        drawCentered(graphics, "Sharktower Settings", 16, UiDrawing.TEXT, true);
        drawCentered(graphics, "Player HUD", 39, UiDrawing.GOLD, true);
        if (isStoryteller()) {
            drawCentered(graphics, "Storyteller Vote Presentation", 147, UiDrawing.GOLD, true);
            drawCentered(graphics,
                    String.format(Locale.ROOT, "Clock hand scale: %.2f", ClientState.clockHandScale),
                    194, UiDrawing.MUTED, false);
        } else {
            drawCentered(graphics, "Storyteller-only vote settings appear when you are the active Storyteller.",
                    147, UiDrawing.MUTED, false);
        }
        drawCentered(graphics, "All controls can also be rebound in Minecraft Controls → Blood on the Sharktower.",
                height - 47, UiDrawing.MUTED, false);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        graphics.text(font, text, (width - font.width(text)) / 2, y, colour, shadow);
    }

    private interface BoolGetter { boolean get(); }
    private interface BoolSetter { void set(boolean value); }
}
