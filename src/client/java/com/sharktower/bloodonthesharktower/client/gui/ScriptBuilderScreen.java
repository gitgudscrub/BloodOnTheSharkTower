package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Storyteller script builder: pick the roles for the next game from every
 * official role (plus any custom roles on the current script).
 *
 * Roles are shown on the shared team-grouped {@link RoleGridPanel}, with a
 * search box that matches names and ability text. Clicking a token toggles it
 * in or out of the script; "Load Script" sends the selection to the server.
 */
public final class ScriptBuilderScreen extends Screen {
    private static final Set<String> SELECTED = new LinkedHashSet<>();
    private static String seededScriptIdentity = "";
    private static String searchQuery = "";

    private RoleGridPanel grid;
    private EditBox search;

    public ScriptBuilderScreen() {
        super(Component.literal("Blood on the Sharktower — Script Builder"));
        seedFromCurrentScript();
    }

    @Override
    protected void init() {
        grid = new RoleGridPanel(this.font);
        grid.selected = role -> SELECTED.contains(role.getId());
        grid.setRoles(palette(), searchQuery);

        int searchWidth = Math.min(200, Math.max(100, this.width - 250));
        search = new EditBox(this.font, (this.width - searchWidth) / 2, 10, searchWidth, 20,
                Component.literal("Search roles"));
        search.setHint(Component.literal("Search roles...").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(80);
        search.setValue(searchQuery);
        search.setResponder(value -> {
            searchQuery = value;
            grid.setRoles(palette(), searchQuery);
        });
        this.addRenderableWidget(search);

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Base 3"), b ->
                        this.minecraft.gui.setScreen(new BaseThreeScreen()))
                .bounds(this.width - 108, 10, 96, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Custom Scripts"), b ->
                        CustomScriptsScreen.openAndRefresh())
                .bounds(this.width - 108, 34, 96, 20).build());

        int gridTop = 60;
        int gridBottom = this.height - 56;
        int gridWidth = Math.min(this.width - 24, RoleGridPanel.CELL_W * 7 + RoleGridPanel.SCROLLBAR_W + 4);
        grid.setBounds((this.width - gridWidth) / 2, gridTop, gridWidth, gridBottom - gridTop);

        int navY = this.height - 50;
        this.addRenderableWidget(SharktowerButton.builder(SharktowerButton.label("Clear", ChatFormatting.GRAY), b -> {
            SELECTED.clear();
        }).bounds(this.width / 2 - 100, navY, 70, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(SharktowerButton.label("Load Script", ChatFormatting.GOLD), b -> load())
                .bounds(this.width / 2 - 20, navY, 120, 20).build());
        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Back to Grimoire"), b ->
                        this.minecraft.gui.setScreen(new AssignRolesScreen()))
                .bounds(this.width / 2 - 100, this.height - 26, 200, 20).build());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        ScriptRole role = grid.roleAt(event.x(), event.y());
        if (role == null) return false;
        if (!SELECTED.add(role.getId())) SELECTED.remove(role.getId());
        search.setFocused(false);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (grid.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void load() {
        if (SELECTED.isEmpty()) return;
        ClientStorytellerActions.send("load_script_selection", String.join("|", SELECTED));
        this.minecraft.gui.setScreen(new AssignRolesScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, "Script Builder", 12, 14, UiDrawing.GOLD, true);
        String count = "Selected: " + SELECTED.size();
        if (!searchQuery.isBlank()) count += "  ·  showing " + grid.shownCount();
        graphics.text(this.font, count, 12, 32, UiDrawing.MUTED, false);
        grid.draw(graphics, mouseX, mouseY);
        grid.drawHoverCard(graphics, this.width, this.height, mouseX, mouseY);
    }

    private static List<ScriptRole> palette() {
        Map<String, ScriptRole> merged = new LinkedHashMap<>();
        for (Role role : Role.SELECTABLE_ROLES) merged.put(role.getId(), new ScriptRole.Official(role));
        if (ClientState.currentScript != null) {
            for (ScriptRole role : ClientState.currentScript.allRoles()) merged.put(role.getId(), role);
        }
        List<ScriptRole> roles = new ArrayList<>(merged.values());
        roles.removeIf(role -> role.getTeam() == RoleType.FABLED || role.getTeam() == RoleType.LORIC);
        roles.sort(Comparator.comparingInt((ScriptRole r) -> r.getTeam().ordinal())
                .thenComparing(ScriptRole::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return roles;
    }

    static void invalidateSelectionCache() {
        seededScriptIdentity = "";
    }

    private static void seedFromCurrentScript() {
        String identity = currentScriptIdentity();
        if (identity.equals(seededScriptIdentity)) return;

        SELECTED.clear();
        seededScriptIdentity = identity;
        if (ClientState.currentScript == null) return;
        for (ScriptRole role : ClientState.currentScript.allRoles()) SELECTED.add(role.getId());
    }

    private static String currentScriptIdentity() {
        if (ClientState.currentScript == null) return "none";
        StringBuilder out = new StringBuilder(ClientState.currentScript.name()).append('|');
        for (ScriptRole role : ClientState.currentScript.allRoles()) out.append(role.getId()).append(',');
        return out.toString();
    }
}
