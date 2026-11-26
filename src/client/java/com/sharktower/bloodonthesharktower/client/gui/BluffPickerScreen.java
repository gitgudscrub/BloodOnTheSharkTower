package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Three-bluff picker shared by the Demon and Lunatic screens: a paged grid of
 * good roles, then Clear, Confirm and Back. All three choices are collected
 * before anything is sent, so the Grimoire isn't closed slot by slot.
 */
abstract class BluffPickerScreen extends Screen {
    protected final LinkedHashSet<String> selected;
    private final int page;
    protected final Screen returnScreen;

    protected BluffPickerScreen(Component title, Set<String> selected, int page, Screen returnScreen) {
        super(title);
        this.selected = new LinkedHashSet<>(selected);
        this.page = Math.max(0, page);
        this.returnScreen = returnScreen;
    }

    /** The roles that can be picked, in display order. */
    protected abstract List<ScriptRole> roles();

    /** The line at the top of the screen. */
    protected abstract String heading();

    /** This picker again with another selection or page. */
    protected abstract Screen with(Set<String> selected, int page);

    protected abstract void confirm();

    protected abstract void back();

    private int columns() { return Math.max(1, (this.width - 24 + 6) / 128); }

    private int perPage() { return columns() * Math.max(1, (this.height - 50 - 90) / 25); }

    @Override
    protected void init() {
        List<ScriptRole> roles = roles();
        int maxPage = Math.max(0, (roles.size() - 1) / perPage());
        int actualPage = Math.min(page, maxPage);
        int start = actualPage * perPage();
        int end = Math.min(roles.size(), start + perPage());

        int columns = columns();
        int width = 122;
        int height = 20;
        int gapX = 6;
        int gapY = 5;
        int gridWidth = columns * width + (columns - 1) * gapX;
        int left = (this.width - gridWidth) / 2;
        int top = 50;

        for (int index = start; index < end; index++) {
            ScriptRole role = roles.get(index);
            int local = index - start;
            int col = local % columns;
            int row = local / columns;

            boolean chosen = selected.contains(key(role.getId()));
            Component label = Component.literal((chosen ? "✓ " : "") + role.getDisplayName())
                    .withStyle(style -> style.withColor(UiDrawing.teamColor(role.getTeam())));

            SharktowerButton button = SharktowerButton.builder(label, b -> toggle(role, actualPage))
                    .bounds(left + col * (width + gapX), top + row * (height + gapY), width, height)
                    .build();

            // Once three are selected, other unselected roles are disabled until
            // one of the selected choices is deselected.
            button.active = chosen || selected.size() < 3;
            this.addRenderableWidget(button);
        }

        int navY = this.height - 78;
        SharktowerButton previous = SharktowerButton.builder(Component.literal("<"), b ->
                        this.minecraft.gui.setScreen(with(selected, actualPage - 1)))
                .bounds(this.width / 2 - 120, navY, 40, 20).build();
        previous.active = actualPage > 0;
        this.addRenderableWidget(previous);

        SharktowerButton next = SharktowerButton.builder(Component.literal(">"), b ->
                        this.minecraft.gui.setScreen(with(selected, actualPage + 1)))
                .bounds(this.width / 2 + 80, navY, 40, 20).build();
        next.active = actualPage < maxPage;
        this.addRenderableWidget(next);

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Clear Selection"), b ->
                        this.minecraft.gui.setScreen(with(Set.of(), actualPage)))
                .bounds(this.width / 2 - 72, navY, 144, 20).build());

        SharktowerButton confirm = SharktowerButton.builder(Component.literal("Confirm 3 Bluffs").withStyle(ChatFormatting.GREEN), b ->
                        confirm())
                .bounds(this.width / 2 - 125, this.height - 30, 120, 20).build();
        confirm.active = selected.size() == 3;
        this.addRenderableWidget(confirm);

        this.addRenderableWidget(SharktowerButton.builder(Component.literal("Back"), b -> back())
                .bounds(this.width / 2 + 5, this.height - 30, 120, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String heading = heading();
        UiDrawing.text(graphics, this.font, heading,
                (this.width - this.font.width(heading)) / 2, 16, UiDrawing.GOLD, UiTheme.dark());

        String count = selected.size() + "/3 selected";
        UiDrawing.text(graphics, this.font, count,
                (this.width - this.font.width(count)) / 2, 29,
                selected.size() == 3 ? UiTheme.yes() : UiDrawing.MUTED, false);

        String names = selectedNames();
        if (!names.isBlank()) {
            UiDrawing.text(graphics, this.font, names,
                    (this.width - this.font.width(names)) / 2, 39, UiDrawing.TEXT, false);
        }
    }

    @Override
    public void onClose() {
        back();
    }

    private void toggle(ScriptRole role, int currentPage) {
        String id = key(role.getId());
        LinkedHashSet<String> next = new LinkedHashSet<>(selected);
        if (next.contains(id)) {
            next.remove(id);
        } else if (next.size() < 3) {
            next.add(id);
        }
        this.minecraft.gui.setScreen(with(next, currentPage));
    }

    private String selectedNames() {
        List<ScriptRole> available = roles();
        List<String> names = new ArrayList<>();
        for (String id : selected) {
            available.stream()
                    .filter(role -> key(role.getId()).equals(id))
                    .findFirst()
                    .ifPresent(role -> names.add(role.getDisplayName()));
        }
        return String.join(" • ", names);
    }

    /** The first three of {@code current} that can still be picked. */
    protected static LinkedHashSet<String> firstThree(List<ScriptRole> available, List<String> current) {
        Set<String> ids = available.stream()
                .map(role -> key(role.getId()))
                .collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String bluff : current) {
            String id = key(bluff);
            if (ids.contains(id) && out.size() < 3) out.add(id);
        }
        return out;
    }

    /** The script's Townsfolk and Outsiders, minus {@code excluded} ids, by team then name. */
    protected static List<ScriptRole> goodRoles(Set<String> excluded) {
        if (ClientState.currentScript == null) return List.of();
        List<ScriptRole> roles = new ArrayList<>();
        for (ScriptRole role : ClientState.currentScript.allRoles()) {
            if (role == null) continue;
            if (role.getTeam() != RoleType.TOWNSFOLK && role.getTeam() != RoleType.OUTSIDER) continue;
            if (excluded.contains(key(role.getId()))) continue;
            roles.add(role);
        }
        roles.sort(Comparator
                .comparingInt((ScriptRole role) -> role.getTeam().ordinal())
                .thenComparing(ScriptRole::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return roles;
    }

    protected static String key(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
