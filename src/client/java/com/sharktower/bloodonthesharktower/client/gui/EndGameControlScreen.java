package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Storyteller end-game / reveal controller with a hard-reset escape hatch. */
public final class EndGameControlScreen extends Screen {
    private final Screen parent;

    public EndGameControlScreen(Screen parent) {
        super(Component.literal("End Game"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 72;

        if (!ClientState.gameEnding) {
            this.addRenderableWidget(Button.builder(
                            Component.literal("GOOD WINS").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                            b -> {
                                ClientStorytellerActions.send("end_game_good");
                                this.minecraft.gui.setScreen(null);
                            })
                    .bounds(cx - 105, y, 100, 20).build());
            this.addRenderableWidget(Button.builder(
                            Component.literal("EVIL WINS").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                            b -> {
                                ClientStorytellerActions.send("end_game_evil");
                                this.minecraft.gui.setScreen(null);
                            })
                    .bounds(cx + 5, y, 100, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Final Grimoire"), b ->
                            this.minecraft.gui.setScreen(new FinalGrimoireScreen(this)))
                    .bounds(cx - 105, y, 100, 20).build());
            this.addRenderableWidget(Button.builder(
                            Component.literal("Reset for Next Game").withStyle(ChatFormatting.GOLD),
                            b -> {
                                ClientStorytellerActions.send("reset_for_next_game");
                                this.minecraft.gui.setScreen(null);
                            })
                    .bounds(cx + 5, y, 100, 20).build());

            this.addRenderableWidget(Button.builder(
                            Component.literal("Change to GOOD").withStyle(ChatFormatting.AQUA),
                            b -> {
                                ClientStorytellerActions.send("end_game_good");
                                this.minecraft.gui.setScreen(null);
                            })
                    .bounds(cx - 105, y + 30, 100, 20).build());
            this.addRenderableWidget(Button.builder(
                            Component.literal("Change to EVIL").withStyle(ChatFormatting.RED),
                            b -> {
                                ClientStorytellerActions.send("end_game_evil");
                                this.minecraft.gui.setScreen(null);
                            })
                    .bounds(cx + 5, y + 30, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Cancel Reveal"), b -> {
                        ClientStorytellerActions.send("end_game_cancel");
                        this.minecraft.gui.setScreen(null);
                    })
                    .bounds(cx - 50, y + 60, 100, 20).build());
        }

        this.addRenderableWidget(Button.builder(
                        Component.literal("Hard Reset").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        b -> {
                            ClientStorytellerActions.send("reset_hard");
                            this.minecraft.gui.setScreen(null);
                        })
                .bounds(cx - 60, this.height - 56, 120, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b ->
                        this.minecraft.gui.setScreen(parent))
                .bounds(cx - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawCentered(graphics, "End Game", 18, UiDrawing.GOLD, true);

        if (ClientState.gameEnding) {
            int colour = "GOOD".equals(ClientState.winningTeam) ? UiDrawing.GOOD : UiDrawing.EVIL;
            drawCentered(graphics, "GAME OVER — " + ClientState.winningTeam + " WINS", 40, colour, true);
            drawCentered(graphics, "Final roles are revealed. The world is not restored until Reset for Next Game.",
                    54, UiDrawing.MUTED, false);
        } else {
            drawCentered(graphics, "Choose the winning team. This starts reveal mode without resetting the map.",
                    42, UiDrawing.MUTED, false);
        }

        drawCentered(graphics, "Hard Reset clears the full game state immediately.",
                this.height - 76, UiDrawing.MUTED, false);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, colour, shadow);
    }
}
