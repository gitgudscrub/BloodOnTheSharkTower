package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Every official role, grouped by team on the shared {@link RoleGridPanel},
 * with a search box and the ability text on hover. Open with the Catalog key
 * (K by default) or from Storyteller Tools.
 */
public class RoleCatalogScreen extends Screen {
    private static String searchQuery = "";

    private RoleGridPanel grid;

    public RoleCatalogScreen() {
        super(Component.literal("Role Catalog"));
    }

    @Override
    protected void init() {
        grid = new RoleGridPanel(this.font);
        grid.setRoles(allRoles(), searchQuery);

        int searchWidth = Math.min(200, Math.max(100, this.width - 40));
        EditBox search = new EditBox(this.font, (this.width - searchWidth) / 2, 24, searchWidth, 20,
                Component.literal("Search roles"));
        search.setHint(Component.literal("Search roles...").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(80);
        search.setValue(searchQuery);
        search.setResponder(value -> {
            searchQuery = value;
            grid.setRoles(allRoles(), searchQuery);
        });
        this.addRenderableWidget(search);

        int gridTop = 52;
        int gridBottom = this.height - 32;
        int gridWidth = Math.min(this.width - 24, RoleGridPanel.CELL_W * 7 + RoleGridPanel.SCROLLBAR_W + 4);
        grid.setBounds((this.width - gridWidth) / 2, gridTop, gridWidth, gridBottom - gridTop);

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 60, this.height - 26, 120, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (grid.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = this.title.getString();
        if (!searchQuery.isBlank()) title += "  ·  " + grid.shownCount() + " match";
        UiTheme.drawTitle(graphics, this.font, title, this.width / 2, 7);
        grid.draw(graphics, mouseX, mouseY);
        grid.drawHoverCard(graphics, this.width, this.height, mouseX, mouseY);
    }

    private static List<ScriptRole> allRoles() {
        return Arrays.stream(Role.values())
                .filter(role -> role != Role.NO_ROLE)
                .<ScriptRole>map(ScriptRole.Official::new)
                .toList();
    }
}
