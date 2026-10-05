package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import com.sharktower.bloodonthesharktower.client.gui.DemonBluffSelectionScreen;
import com.sharktower.bloodonthesharktower.client.gui.LunaticBluffSelectionScreen;
import com.sharktower.bloodonthesharktower.client.gui.AssignRolesScreen;
import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
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

/** Original-style left-side bluff slot for the real Demon or Lunatic fake set. */
public final class GrimoireBluffWidget extends AbstractWidget {
    private final int index;
    private final String roleId;
    private final boolean lunatic;

    public GrimoireBluffWidget(int x, int y, int size, int index, String roleId) {
        this(x, y, size, index, roleId, false);
    }

    public GrimoireBluffWidget(int x, int y, int size, int index, String roleId, boolean lunatic) {
        super(x, y, size, size, Component.literal((lunatic ? "Lunatic" : "Demon") + " bluff " + (index + 1)));
        this.index = index;
        this.roleId = roleId == null ? "" : roleId;
        this.lunatic = lunatic;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ScriptRole role = resolve(roleId);
        if (role != null) {
            UiDrawing.roleToken(graphics, role, getX(), getY(), this.width);
            graphics.outline(getX() - 1, getY() - 1, this.width + 2, this.height + 2,
                    UiDrawing.opaque(UiDrawing.teamColor(role.getTeam())));
        } else {
            UiDrawing.emptyRoleSlot(graphics, getX(), getY(), this.width);
        }
        if (isHovered()) {
            graphics.outline(getX() - 1, getY() - 1, this.width + 2, this.height + 2, UiDrawing.GOLD);
            boolean editable = ClientGrimoireEdits.isLocalStoryteller();
            String kind = lunatic ? "Lunatic bluff" : "Demon bluff";
            GrimoireHoverHints.set(role == null
                    ? kind + " slot " + (index + 1) + (editable ? " — click to choose bluffs" : "")
                    : kind + ": " + role.getDisplayName() + (editable ? " — click to edit all three" : ""));
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!ClientGrimoireEdits.isLocalStoryteller()) return;
        if (lunatic) {
            Minecraft.getInstance().gui.setScreen(new LunaticBluffSelectionScreen(new AssignRolesScreen()));
        } else {
            Minecraft.getInstance().gui.setScreen(new DemonBluffSelectionScreen());
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    public ScriptRole displayRole() { return resolve(roleId); }

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
