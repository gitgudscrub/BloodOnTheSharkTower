package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientPlayerActions;
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

/** Player-facing list of active Travellers who can be called for exile. */
public final class PlayerExileCallScreen extends Screen {
    private final Screen parent;

    public PlayerExileCallScreen(Screen parent) {
        super(Component.literal("Call for Exile"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<Map.Entry<UUID, Integer>> targets = new ArrayList<>(ClientState.playerSeatNumbers.entrySet());
        targets.removeIf(entry -> !ClientState.canBeExiled.getOrDefault(entry.getKey(), false));
        targets.sort(Comparator.comparingInt(entry -> entry.getValue() == null ? Integer.MAX_VALUE : entry.getValue()));

        int cx = this.width / 2;
        int startY = 58;
        int buttonW = 180;
        for (int i = 0; i < targets.size(); i++) {
            Map.Entry<UUID, Integer> entry = targets.get(i);
            UUID id = entry.getKey();
            int seat = entry.getValue() == null ? 0 : entry.getValue();
            String label = "Seat " + seat + " — " + ClientState.playerName(id, seat);
            this.addRenderableWidget(Button.builder(Component.literal(label).withStyle(ChatFormatting.LIGHT_PURPLE), b -> {
                        ClientPlayerActions.send("call_for_exile", id.toString());
                        this.minecraft.gui.setScreen(parent);
                    })
                    .bounds(cx - buttonW / 2, startY + i * 24, buttonW, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.minecraft.gui.setScreen(parent))
                .bounds(cx - 40, this.height - 28, 80, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "CALL FOR TRAVELLER EXILE";
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 18, 0xFFFF55FF, true);
        String hint = "Exile is separate from nominations and does not use ghost votes.";
        graphics.text(this.font, hint, (this.width - this.font.width(hint)) / 2, 34, UiDrawing.MUTED, false);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
