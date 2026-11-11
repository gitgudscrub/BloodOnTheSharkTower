package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
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
                .bounds(left, top, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Script"), b -> this.minecraft.gui.setScreen(new ScriptReferenceScreen()))
                .bounds(mid, top, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Role Catalog"), b -> this.minecraft.gui.setScreen(new RoleCatalogScreen()))
                .bounds(right, top, 100, 20).build());

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Start Night"), b -> ClientStorytellerActions.send("phase_night"))
                .bounds(left, top + row, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Start Day"), b -> ClientStorytellerActions.send("phase_day"))
                .bounds(mid, top + row, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Night Status"), b ->
                        ClientStorytellerActions.send("night_status"))
                .bounds(right, top + row, 100, 20).build());

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Timer"), b -> this.minecraft.gui.setScreen(new TimerScreen()))
                .bounds(left, top + row * 2, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Sync State"), b ->
                        ClientStorytellerActions.send("sync_state"))
                .bounds(mid, top + row * 2, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Status"), b ->
                        ClientStorytellerActions.send("storyteller_status"))
                .bounds(right, top + row * 2, 100, 20).build());

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Nomination Flow"), b ->
                        this.minecraft.gui.setScreen(new NominationControlScreen()))
                .bounds(left, top + row * 3, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Traveller Exile"), b ->
                        this.minecraft.gui.setScreen(new ExileControlScreen()))
                .bounds(mid, top + row * 3, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Execute Marked"), b -> ClientStorytellerActions.send("execute_marked"))
                .bounds(right, top + row * 3, 100, 20).build());

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Full Reset"), b -> ClientStorytellerActions.send("reset_hard"))
                .bounds(left, top + row * 4, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("End Game"), b ->
                        this.minecraft.gui.setScreen(new EndGameControlScreen(this)))
                .bounds(mid, top + row * 4, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Restore Previous"), b ->
                        ClientStorytellerActions.send("snapshot_restore_previous"))
                .bounds(right, top + row * 4, 100, 20).build());

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Settings"), b ->
                        this.minecraft.gui.setScreen(new SharktowerSettingsScreen(this)))
                .bounds(cx - 105, this.height - 28, 100, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Back"), b -> this.onClose())
                .bounds(cx + 5, this.height - 28, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int top = 46;
        int row = rowSpacing();
        drawCentered(graphics, "Storyteller Tools", 14, UiDrawing.TEXT, true);
        drawCentered(graphics,
                "Phase: " + ClientState.phase() + "   Script: " + ClientState.displayScriptName() + "   Seats: " + ClientState.seatedPlayerCount(),
                27, UiDrawing.MUTED, false);

        graphics.text(this.font, "Setup", cx - 155, top - 12, UiDrawing.GOLD, true);
        graphics.text(this.font, "Night Cycle", cx - 155, top + row - 12, UiDrawing.GOLD, true);
        graphics.text(this.font, "Management", cx - 155, top + row * 2 - 12, UiDrawing.GOLD, true);
        graphics.text(this.font, "Voting", cx - 155, top + row * 3 - 12, UiDrawing.GOLD, true);
        graphics.text(this.font, "Recovery", cx - 155, top + row * 4 - 12, UiDrawing.GOLD, true);
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
