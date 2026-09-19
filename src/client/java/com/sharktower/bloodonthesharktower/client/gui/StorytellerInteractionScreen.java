package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientPlayerActions;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Small action screen shown when a player clicks the central Storyteller token. */
public final class StorytellerInteractionScreen extends Screen {
    private final UUID storytellerId;
    private final Screen parent;

    public StorytellerInteractionScreen(UUID storytellerId, Screen parent) {
        super(Component.literal("Storyteller"));
        this.storytellerId = storytellerId;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 + 28;

        this.addRenderableWidget(Button.builder(
                        Component.literal("Request Private Chat").withStyle(ChatFormatting.LIGHT_PURPLE),
                        b -> {
                            ClientPlayerActions.send("request_private_storyteller", storytellerId.toString());
                            this.minecraft.gui.setScreen(parent);
                        })
                .bounds(centerX - 80, y, 160, 20).build());

        int offset = 25;
        if (hasActiveTraveller()) {
            this.addRenderableWidget(Button.builder(
                            Component.literal("Call for Exile").withStyle(ChatFormatting.LIGHT_PURPLE),
                            b -> this.minecraft.gui.setScreen(new PlayerExileCallScreen(this)))
                    .bounds(centerX - 80, y + offset, 160, 20).build());
            offset += 25;
        }

        if (atheistOnScript()) {
            this.addRenderableWidget(Button.builder(
                            Component.literal("Nominate Storyteller").withStyle(ChatFormatting.GOLD),
                            b -> {
                                ClientPlayerActions.send("nominate_storyteller", storytellerId.toString());
                                this.minecraft.gui.setScreen(parent);
                            })
                    .bounds(centerX - 80, y + offset, 160, 20).build());
            offset += 25;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.minecraft.gui.setScreen(parent))
                .bounds(centerX - 40, y + offset + 5, 80, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int headSize = 32;
        int headX = centerX - headSize / 2;
        int headY = this.height / 2 - 48;
        boolean drewFace = ClientState.connectedPlayers.contains(storytellerId)
                && PlayerFaceCompat.draw(graphics, storytellerId, headX, headY, headSize);
        if (!drewFace) {
            graphics.fill(headX, headY, headX + headSize, headY + headSize, 0xDD251A2D);
            graphics.text(this.font, "ST", centerX - this.font.width("ST") / 2, headY + 11, UiDrawing.GOLD, true);
        }
        graphics.outline(headX - 1, headY - 1, headSize + 2, headSize + 2, UiDrawing.GOLD);

        String name = ClientState.playerName(storytellerId, 0);
        graphics.text(this.font, name, centerX - this.font.width(name) / 2, headY + 37, UiDrawing.TEXT, true);
        String title = "STORYTELLER";
        graphics.text(this.font, title, centerX - this.font.width(title) / 2, headY + 48, UiDrawing.MUTED, true);

        if (atheistOnScript()) {
            String hint = ClientState.nominationsOpen
                    ? "Atheist is on the script — Storyteller nomination is available."
                    : "Atheist is on the script — nominations must be open first.";
            graphics.text(this.font, hint, centerX - this.font.width(hint) / 2,
                    this.height / 2 + 2, UiDrawing.MUTED, true);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private boolean hasActiveTraveller() {
        return ClientState.canBeExiled.values().stream().anyMatch(Boolean.TRUE::equals);
    }

    private boolean atheistOnScript() {
        return ClientState.currentScript != null && ClientState.currentScript.roles().contains(Role.ATHEIST);
    }
}
