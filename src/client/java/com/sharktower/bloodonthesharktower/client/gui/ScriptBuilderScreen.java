package com.sharktower.bloodonthesharktower.client.gui;

import com.google.gson.Gson;
import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Functional 26.2 Script Builder bridge.
 *
 * It keeps the original BOTB idea (palette + current script), but deliberately
 * stays compact while the private port is being stabilised. It can build and
 * load official-role scripts immediately; custom-role authoring remains a
 * later Sharktower enhancement.
 */
public final class ScriptBuilderScreen extends Screen {
    private static final int PAGE_SIZE = 24;
    private static final Gson GSON = new Gson();
    private static final Set<String> SELECTED = new LinkedHashSet<>();
    private static String seededScriptIdentity = "";

    private int page;

    public ScriptBuilderScreen() {
        this(0);
    }

    private ScriptBuilderScreen(int page) {
        super(Component.literal("Blood on the Sharktower — Script Builder"));
        this.page = Math.max(0, page);
        seedFromCurrentScript();
    }

    @Override
    protected void init() {
        List<Role> roles = palette();
        int maxPage = Math.max(0, (roles.size() - 1) / PAGE_SIZE);
        if (page > maxPage) page = maxPage;

        int columns = 4;
        int w = 125;
        int h = 20;
        int gapX = 6;
        int gapY = 5;
        int gridW = columns * w + (columns - 1) * gapX;
        int left = (this.width - gridW) / 2;
        int top = 48;
        int start = page * PAGE_SIZE;
        int end = Math.min(roles.size(), start + PAGE_SIZE);

        for (int i = start; i < end; i++) {
            Role role = roles.get(i);
            int local = i - start;
            int col = local % columns;
            int row = local / columns;
            boolean chosen = SELECTED.contains(role.getId());
            String label = (chosen ? "[x] " : "[ ] ") + role.getDisplayName();
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
                        if (!SELECTED.add(role.getId())) SELECTED.remove(role.getId());
                        this.minecraft.gui.setScreen(new ScriptBuilderScreen(page));
                    })
                    .bounds(left + col * (w + gapX), top + row * (h + gapY), w, h).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Base 3"), b ->
                        this.minecraft.gui.setScreen(new BaseThreeScreen()))
                .bounds(this.width - 108, 10, 96, 20).build());

        int navY = this.height - 70;
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (page > 0) this.minecraft.gui.setScreen(new ScriptBuilderScreen(page - 1));
        }).bounds(this.width / 2 - 120, navY, 40, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (page < maxPage) this.minecraft.gui.setScreen(new ScriptBuilderScreen(page + 1));
        }).bounds(this.width / 2 - 70, navY, 40, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            SELECTED.clear();
            this.minecraft.gui.setScreen(new ScriptBuilderScreen(0));
        }).bounds(this.width / 2 - 20, navY, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Load Script"), b -> load())
                .bounds(this.width / 2 + 60, navY, 110, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back to Grimoire"), b ->
                        this.minecraft.gui.setScreen(new AssignRolesScreen()))
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private void load() {
        if (SELECTED.isEmpty()) return;
        List<Object> json = new ArrayList<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", "_meta");
        meta.put("name", "Sharktower Custom Script");
        meta.put("author", "Private Sharktower Builder");
        json.add(meta);
        json.addAll(SELECTED);
        ClientStorytellerActions.send("load_script_json", GSON.toJson(json));
        this.minecraft.gui.setScreen(new AssignRolesScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String heading = "Script Builder";
        graphics.text(this.font, heading, (this.width - this.font.width(heading)) / 2, 14, UiDrawing.GOLD, true);
        String info = SELECTED.size() + " roles selected   •   Click roles to add/remove";
        graphics.text(this.font, info, (this.width - this.font.width(info)) / 2, 29, UiDrawing.MUTED, false);
    }

    private static List<Role> palette() {
        List<Role> roles = new ArrayList<>(Role.SELECTABLE_ROLES);
        roles.removeIf(role -> role.getType() == RoleType.FABLED || role.getType() == RoleType.LORIC);
        roles.sort(Comparator.comparingInt((Role r) -> r.getType().ordinal())
                .thenComparing(Role::getDisplayName, String.CASE_INSENSITIVE_ORDER));
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
