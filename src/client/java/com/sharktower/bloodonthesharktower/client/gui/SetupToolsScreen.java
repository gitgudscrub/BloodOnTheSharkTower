package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Setup actions that are not part of the Grimoire's setup checklist. */
public final class SetupToolsScreen extends Screen {
    public SetupToolsScreen() {
        super(Component.literal("More Setup"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 44;
        add(cx - 153, y, "Shuffle Seats", "Move players to random seats.", () -> action("shuffle_seats"));
        add(cx + 3, y, "Shuffle Roles", "Swap the dealt tokens between players at random.", () -> action("shuffle_roles"));
        y += 24;
        add(cx - 153, y, "Randomize Roles", "Deal a random legal setup, ignoring the bag.", () -> action("randomize_roles"));
        add(cx + 3, y, "Script Builder", "Pick characters one by one.", () -> this.minecraft.gui.setScreen(new ScriptBuilderScreen()));
        y += 24;
        add(cx - 153, y, "Send to Seats", "Teleport seated players to their seats.", () -> action("send_to_seats"));
        add(cx + 3, y, "Send to Home", "Teleport players to their houses.", () -> action("send_home"));
        y += 24;
        add(cx - 153, y, "Self: " + (AssignRolesScreen.showSelf() ? "SHOW" : "HIDE"),
                "Show or hide your own head in the middle of the Grimoire.", () -> {
                    AssignRolesScreen.toggleShowSelf();
                    rebuildWidgets();
                });
        add(cx + 3, y, "Unseated: " + (AssignRolesScreen.showUnseated() ? "SHOW" : "HIDE"),
                "Show or hide the list of players without a seat.", () -> {
                    AssignRolesScreen.toggleShowUnseated();
                    rebuildWidgets();
                });
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Back to Grimoire"), b -> onClose())
                .bounds(cx - 90, this.height - 28, 180, 20).build());
    }

    private void add(int x, int y, String label, String tip, Runnable run) {
        this.addRenderableWidget(SharktowerButton.builder(Component.literal(label), b -> run.run())
                .bounds(x, y, 150, 20).tooltip(Tooltip.create(Component.literal(tip))).build());
    }

    private void action(String op) {
        ClientStorytellerActions.send(op);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(new AssignRolesScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "More Setup";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 18, UiDrawing.GOLD, UiTheme.dark());
    }
}
