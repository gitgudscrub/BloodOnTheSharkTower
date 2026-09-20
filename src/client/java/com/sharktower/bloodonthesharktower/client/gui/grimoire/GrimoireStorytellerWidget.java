package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.gui.PlayerFaceCompat;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.function.Consumer;

/** Clickable Storyteller head rendered in the centre of the Grimoire. */
public final class GrimoireStorytellerWidget extends AbstractWidget {
    private final UUID storytellerId;
    private final Consumer<UUID> onPress;

    public GrimoireStorytellerWidget(int x, int y, int size, UUID storytellerId, Consumer<UUID> onPress) {
        super(x, y, size, size + 22, Component.literal("Storyteller"));
        this.storytellerId = storytellerId;
        this.onPress = onPress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int headSize = Math.min(this.width, 28);
        int headX = getX() + (this.width - headSize) / 2;
        int headY = getY();

        boolean drewFace = ClientState.connectedPlayers.contains(storytellerId)
                && PlayerFaceCompat.draw(graphics, storytellerId, headX, headY, headSize);
        if (!drewFace) {
            graphics.fill(headX, headY, headX + headSize, headY + headSize, 0xDD251A2D);
            String st = "ST";
            graphics.text(Minecraft.getInstance().font, st,
                    headX + headSize / 2 - Minecraft.getInstance().font.width(st) / 2,
                    headY + 9, UiDrawing.GOLD, true);
        }

        graphics.outline(headX - 1, headY - 1, headSize + 2, headSize + 2,
                isHovered() ? UiDrawing.GOLD : UiDrawing.TEXT);

        String name = ClientState.playerName(storytellerId, 0);
        int nameX = getX() + this.width / 2 - Minecraft.getInstance().font.width(name) / 2;
        graphics.text(Minecraft.getInstance().font, name, nameX, headY + headSize + 3, UiDrawing.TEXT, true);

        String label = "STORYTELLER";
        int labelX = getX() + this.width / 2 - Minecraft.getInstance().font.width(label) / 2;
        graphics.text(Minecraft.getInstance().font, label, labelX, headY + headSize + 13, UiDrawing.MUTED, true);

        if (isHovered()) {
            GrimoireHoverHints.set("Storyteller — click for private chat / Storyteller interaction");
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (onPress != null) onPress.accept(storytellerId);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
