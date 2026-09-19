package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.gui.RoleSelectionScreen;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Original-style left-side Demon bluff slot. */
public final class GrimoireBluffWidget extends AbstractWidget {
    private final int index;
    private final String roleId;

    public GrimoireBluffWidget(int x, int y, int size, int index, String roleId) {
        super(x, y, size, size, Component.literal("Demon bluff " + (index + 1)));
        this.index = index;
        this.roleId = roleId == null ? "" : roleId;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ScriptRole role = resolve(roleId);
        if (role != null) {
            UiDrawing.roleToken(graphics, role, getX(), getY(), this.width);
        } else {
            UiDrawing.emptyRoleSlot(graphics, getX(), getY(), this.width);
        }
        if (isHovered()) graphics.outline(getX() - 1, getY() - 1, this.width + 2, this.height + 2, UiDrawing.GOLD);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        Minecraft.getInstance().gui.setScreen(RoleSelectionScreen.forBluff(index));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private static ScriptRole resolve(String id) {
        if (id == null || id.isBlank()) return null;
        if (ClientState.currentScript != null) {
            for (ScriptRole role : ClientState.currentScript.allRoles()) {
                if (role.getId().equalsIgnoreCase(id)) return role;
            }
        }
        Role official = Role.findById(id);
        return official == null || official == Role.NO_ROLE ? null : new ScriptRole.Official(official);
    }
}
