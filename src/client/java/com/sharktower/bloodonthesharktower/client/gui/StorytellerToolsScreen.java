package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.GamePhase;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Tooltip;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Storyteller utility screen.
 *
 * The 1.1.0 night-lifecycle pass gives Dusk/Dawn first-class server actions so
 * the UI and command tests exercise the same authoritative phase path.
 */
public class StorytellerToolsScreen extends Screen {
    public StorytellerToolsScreen() {
        super(Component.literal("Storyteller Tools"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - 155;
        int mid = cx - 50;
        int right = cx + 55;
        int top = 46;
        int row = rowSpacing();

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Grimoire"), b -> this.minecraft.gui.setScreen(new AssignRolesScreen()))
                .bounds(left, top, 100, 20)
                .tooltip(tip("Seats, roles, reminders and nominations (R).")).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Script"), b -> this.minecraft.gui.setScreen(new ScriptReferenceScreen()))
                .bounds(mid, top, 100, 20)
                .tooltip(tip("The current script and night order (C).")).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Role Catalog"), b -> this.minecraft.gui.setScreen(new RoleCatalogScreen()))
                .bounds(right, top, 100, 20)
                .tooltip(tip("Every character, with abilities (K).")).build());

        // The phase button that comes next is marked with an arrow and gold text.
        boolean nightNext = ClientState.phase() != GamePhase.NIGHT;
        this.addRenderableWidget(SharktowerButton.builder(nextLabel("Start Night", nightNext), b -> ClientStorytellerActions.send("phase_night"))
                .bounds(left, top + row, 100, 20)
                .tooltip(tip("Dusk: players go home and the night order starts."))
                .build());
        this.addRenderableWidget(SharktowerButton.builder(nextLabel("Start Day", !nightNext), b -> ClientStorytellerActions.send("phase_day"))
                .bounds(mid, top + row, 100, 20)
                .tooltip(tip("Dawn: deaths are announced and the day begins."))
                .build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Night Status"), b ->
                        ClientStorytellerActions.send("night_status"))
                .bounds(right, top + row, 100, 20)
                .tooltip(tip("Print who still needs waking tonight.")).build());

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Timer"), b -> this.minecraft.gui.setScreen(new TimerScreen()))
                .bounds(left, top + row * 2, 100, 20)
                .tooltip(tip("Start or stop a discussion timer (Y).")).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Sync State"), b ->
                        ClientStorytellerActions.send("sync_state"))
                .bounds(mid, top + row * 2, 100, 20)
                .tooltip(tip("Resend the game state to every player. Use if a screen looks out of date.")).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Status"), b ->
                        ClientStorytellerActions.send("storyteller_status"))
                .bounds(right, top + row * 2, 100, 20)
                .tooltip(tip("Print a summary of the game in chat.")).build());

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Nomination Flow"), b ->
                        this.minecraft.gui.setScreen(new NominationControlScreen()))
                .bounds(left, top + row * 3, 100, 20)
                .tooltip(tip("Open, run and close nominations and votes.")).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Traveller Exile"), b ->
                        this.minecraft.gui.setScreen(new ExileControlScreen()))
                .bounds(mid, top + row * 3, 100, 20)
                .tooltip(tip("Call for and count a Traveller exile.")).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Execute Marked"), b -> ClientStorytellerActions.send("execute_marked"))
                .bounds(right, top + row * 3, 100, 20)
                .tooltip(tip("Execute the player on the block. You are warned first if they should survive.")).build());

        // Press twice: the first press arms it for a few seconds.
        this.addRenderableWidget(SharktowerButton.builder(SharktowerButton.label("Full Reset", ChatFormatting.RED), b -> {
                    long now = System.currentTimeMillis();
                    if (now - resetArmedAt < 4000L) {
                        resetArmedAt = 0L;
                        b.setMessage(SharktowerButton.label("Full Reset", ChatFormatting.RED));
                        ClientStorytellerActions.send("reset_hard");
                    } else {
                        resetArmedAt = now;
                        b.setMessage(SharktowerButton.label("Click to confirm", ChatFormatting.RED));
                    }
                })
                .bounds(left, top + row * 4, 100, 20)
                .tooltip(tip("Clear roles, deaths and setup for a new game. Click twice to confirm."))
                .build());
        this.addRenderableWidget(SharktowerButton.builder(SharktowerButton.label("End Game", ChatFormatting.RED), b ->
                        this.minecraft.gui.setScreen(new EndGameControlScreen(this)))
                .bounds(mid, top + row * 4, 100, 20)
                .tooltip(tip("Choose the winning team and reveal the Grimoire.")).build());
        this.addRenderableWidget(SharktowerButton.builder(SharktowerButton.label("Restore Previous", ChatFormatting.RED), b ->
                        ClientStorytellerActions.send("snapshot_restore_previous"))
                .bounds(right, top + row * 4, 100, 20)
                .tooltip(tip("Undo back to the previous saved game state.")).build());

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Settings"), b ->
                        this.minecraft.gui.setScreen(new SharktowerSettingsScreen(this)))
                .bounds(left, this.height - 28, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Keys & Help"), b ->
                        this.minecraft.gui.setScreen(new KeysHelpScreen(this)))
                .bounds(mid, this.height - 28, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Back"), b -> this.onClose())
                .bounds(right, this.height - 28, 100, 20).build());
    }

    private long resetArmedAt;

    private static Tooltip tip(String text) {
        return Tooltip.create(Component.literal(text));
    }

    private static Component nextLabel(String text, boolean next) {
        return next ? SharktowerButton.label("> " + text, ChatFormatting.GOLD) : Component.literal(text);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int top = 46;
        int row = rowSpacing();
        UiTheme.drawTitle(graphics, this.font, "Storyteller Tools", cx, 4);
        drawCentered(graphics,
                KeysHelpScreen.phaseName(ClientState.phase()) + "   ·   Script: " + ClientState.displayScriptName() + "   ·   Seats: " + ClientState.seatedPlayerCount(),
                24, UiDrawing.MUTED, false);

        UiTheme.drawSectionLabel(graphics, this.font, "Setup", cx - 155, top - 11);
        UiTheme.drawSectionLabel(graphics, this.font, "Night Cycle", cx - 155, top + row - 11);
        UiTheme.drawSectionLabel(graphics, this.font, "Management", cx - 155, top + row * 2 - 11);
        UiTheme.drawSectionLabel(graphics, this.font, "Voting", cx - 155, top + row * 3 - 11);
        UiTheme.drawSectionLabel(graphics, this.font, "Recovery", cx - 155, top + row * 4 - 11);
    }

    /**
     * Five button rows plus the Settings/Back footer must fit the screen. At GUI
     * scale 4 on a 1280x720 window the area is only about 270 logical pixels
     * tall, which the fixed 45-pixel spacing used to overflow into the footer.
     */
    private int rowSpacing() {
        int available = this.height - 28 - 8 - 46 - 20;
        return Math.max(30, Math.min(45, available / 4));
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, colour, shadow);
    }
}
