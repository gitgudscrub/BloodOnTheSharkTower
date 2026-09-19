package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Clickable BOTC-style character token used by the Sharktower role bag. */
final class RoleBagRoleWidget extends AbstractWidget {
    private final ScriptRole role;
    private final java.util.function.BooleanSupplier selected;
    private final Runnable toggle;

    RoleBagRoleWidget(int x, int y, int width, int height, ScriptRole role,
                      java.util.function.BooleanSupplier selected, Runnable toggle) {
        super(x, y, width, height, Component.literal(role.getDisplayName()));
        this.role = role;
        this.selected = selected;
        this.toggle = toggle;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int tokenSize = Math.min(38, Math.max(24, this.height - 18));
        int tokenX = getX() + (this.width - tokenSize) / 2;
        int tokenY = getY();
        UiDrawing.roleToken(graphics, role, tokenX, tokenY, tokenSize);

        if (selected.getAsBoolean()) {
            graphics.outline(tokenX - 2, tokenY - 2, tokenSize + 4, tokenSize + 4, UiDrawing.GOLD);
            graphics.text(Minecraft.getInstance().font, "✓", tokenX + tokenSize - 6, tokenY - 2, UiDrawing.GOLD, true);
        } else if (isHovered()) {
            graphics.outline(tokenX - 1, tokenY - 1, tokenSize + 2, tokenSize + 2, UiDrawing.TEXT);
        }

        String name = role.getDisplayName();
        int nameX = getX() + (this.width - Minecraft.getInstance().font.width(name)) / 2;
        int nameY = tokenY + tokenSize + 3;
        graphics.text(Minecraft.getInstance().font, name, nameX, nameY,
                selected.getAsBoolean() ? UiDrawing.GOLD : UiDrawing.TEXT, false);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        toggle.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
