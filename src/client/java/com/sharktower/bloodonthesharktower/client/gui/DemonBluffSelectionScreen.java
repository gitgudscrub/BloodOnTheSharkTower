package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.networking.ClientStorytellerActions;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Multi-select Demon bluff picker.
 *
 * All three bluff choices are collected before anything is sent to the server,
 * avoiding the old slot-by-slot flow that repeatedly closed the Grimoire.
 */
public final class DemonBluffSelectionScreen extends Screen {
    private static final int PAGE_SIZE = 24;

    private final LinkedHashSet<String> selected;
    private final int page;

    public DemonBluffSelectionScreen() {
        this(initialSelection(), 0);
    }

    private DemonBluffSelectionScreen(Set<String> selected, int page) {
        super(Component.literal("Choose 3 Demon Bluffs"));
        this.selected = new LinkedHashSet<>(selected);
        this.page = Math.max(0, page);
    }

    @Override
    protected void init() {
        List<ScriptRole> roles = availableRoles();
        int maxPage = Math.max(0, (roles.size() - 1) / PAGE_SIZE);
        int actualPage = Math.min(page, maxPage);

        int start = actualPage * PAGE_SIZE;
        int end = Math.min(roles.size(), start + PAGE_SIZE);

        int columns = 4;
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
                    .withStyle(chosen ? ChatFormatting.GREEN : ChatFormatting.AQUA);

            Button button = Button.builder(label, b -> toggle(role, actualPage))
                    .bounds(left + col * (width + gapX), top + row * (height + gapY), width, height)
                    .build();

            // Once three are selected, other unselected roles are disabled until
            // one of the selected choices is deselected.
            button.active = chosen || selected.size() < 3;
            this.addRenderableWidget(button);
        }

        int navY = this.height - 78;
        Button previous = Button.builder(Component.literal("<"), b ->
                        this.minecraft.gui.setScreen(new DemonBluffSelectionScreen(selected, actualPage - 1)))
                .bounds(this.width / 2 - 120, navY, 40, 20).build();
        previous.active = actualPage > 0;
        this.addRenderableWidget(previous);

        Button next = Button.builder(Component.literal(">"), b ->
                        this.minecraft.gui.setScreen(new DemonBluffSelectionScreen(selected, actualPage + 1)))
                .bounds(this.width / 2 + 80, navY, 40, 20).build();
        next.active = actualPage < maxPage;
        this.addRenderableWidget(next);

        this.addRenderableWidget(Button.builder(Component.literal("Clear Selection"), b ->
                        this.minecraft.gui.setScreen(new DemonBluffSelectionScreen(Set.of(), actualPage)))
                .bounds(this.width / 2 - 72, navY, 144, 20).build());

        Button confirm = Button.builder(Component.literal("Confirm 3 Bluffs").withStyle(ChatFormatting.GREEN), b ->
                        confirm())
                .bounds(this.width / 2 - 125, this.height - 30, 120, 20).build();
        confirm.active = selected.size() == 3;
        this.addRenderableWidget(confirm);

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b ->
                        back())
                .bounds(this.width / 2 + 5, this.height - 30, 120, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String heading = "Choose 3 out-of-play good roles";
        graphics.text(this.font, heading,
                (this.width - this.font.width(heading)) / 2, 16, UiDrawing.GOLD, true);

        String count = selected.size() + "/3 selected";
        graphics.text(this.font, count,
                (this.width - this.font.width(count)) / 2, 29,
                selected.size() == 3 ? UiDrawing.YES : UiDrawing.MUTED, false);

        String names = selectedNames();
        if (!names.isBlank()) {
            graphics.text(this.font, names,
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

        this.minecraft.gui.setScreen(new DemonBluffSelectionScreen(next, currentPage));
    }

    private void confirm() {
        if (selected.size() != 3) return;

        ClientStorytellerActions.send("set_bluffs", String.join("|", selected));
        back();
    }

    private void back() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(new AssignRolesScreen());
    }

    private String selectedNames() {
        List<ScriptRole> available = availableRoles();
        List<String> names = new ArrayList<>();
        for (String id : selected) {
            available.stream()
                    .filter(role -> key(role.getId()).equals(id))
                    .findFirst()
                    .ifPresent(role -> names.add(role.getDisplayName()));
        }
        return String.join(" • ", names);
    }

    private static LinkedHashSet<String> initialSelection() {
        Set<String> available = availableRoles().stream()
                .map(role -> key(role.getId()))
                .collect(java.util.stream.Collectors.toSet());

        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String bluff : ClientState.demonBluffs) {
            String id = key(bluff);
            if (available.contains(id) && out.size() < 3) out.add(id);
        }
        return out;
    }

    private static List<ScriptRole> availableRoles() {
        if (ClientState.currentScript == null) return List.of();

        Set<String> unavailable = new java.util.HashSet<>();

        ClientState.grimoireRoles.values().stream()
                .filter(Objects::nonNull)
                .map(PendingRoleAssignment::getRoleId)
                .filter(Objects::nonNull)
                .map(DemonBluffSelectionScreen::key)
                .forEach(unavailable::add);

        ClientState.grimoirePerceivedRoles.values().stream()
                .filter(Objects::nonNull)
                .map(PendingRoleAssignment::getRoleId)
                .filter(Objects::nonNull)
                .map(DemonBluffSelectionScreen::key)
                .forEach(unavailable::add);

        List<ScriptRole> roles = new ArrayList<>();
        for (ScriptRole role : ClientState.currentScript.allRoles()) {
            if (role == null) continue;
            if (role.getTeam() != RoleType.TOWNSFOLK && role.getTeam() != RoleType.OUTSIDER) continue;
            if (unavailable.contains(key(role.getId()))) continue;
            roles.add(role);
        }

        roles.sort(Comparator
                .comparingInt((ScriptRole role) -> role.getTeam().ordinal())
                .thenComparing(ScriptRole::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return roles;
    }

    private static String key(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
