package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.client.ClientLunaticBluffs;
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
import java.util.Set;

/** Separate three-bluff picker used only for an assigned Lunatic. */
public final class LunaticBluffSelectionScreen extends Screen {
    private int perPage() {
        return Math.max(1, (this.width - 24 + 6) / 128)
                * Math.max(1, (this.height - 50 - 90) / 25);
    }

    private final LinkedHashSet<String> selected;
    private final int page;
    private final Screen returnScreen;

    public LunaticBluffSelectionScreen() {
        this(new NightTeamInfoScreen(true));
    }

    public LunaticBluffSelectionScreen(Screen returnScreen) {
        this(initialSelection(), 0, returnScreen);
    }

    private LunaticBluffSelectionScreen(Set<String> selected, int page, Screen returnScreen) {
        super(Component.literal("Choose 3 Lunatic Bluffs"));
        this.selected = new LinkedHashSet<>(selected);
        this.page = Math.max(0, page);
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        List<ScriptRole> roles = availableRoles();
        int maxPage = Math.max(0, (roles.size() - 1) / perPage());
        int actualPage = Math.min(page, maxPage);
        int start = actualPage * perPage();
        int end = Math.min(roles.size(), start + perPage());

        int columns = Math.max(1, (this.width - 24 + 6) / 128);
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
            Button button = Button.builder(label, b -> toggle(role, actualPage))
                    .bounds(left + col * (width + gapX), top + row * (height + gapY), width, height)
                    .build();
            button.active = chosen || selected.size() < 3;
            this.addRenderableWidget(button);
        }

        int navY = this.height - 78;
        Button previous = Button.builder(Component.literal("<"), b ->
                        this.minecraft.gui.setScreen(new LunaticBluffSelectionScreen(selected, actualPage - 1, returnScreen)))
                .bounds(this.width / 2 - 120, navY, 40, 20).build();
        previous.active = actualPage > 0;
        this.addRenderableWidget(previous);

        Button next = Button.builder(Component.literal(">"), b ->
                        this.minecraft.gui.setScreen(new LunaticBluffSelectionScreen(selected, actualPage + 1, returnScreen)))
                .bounds(this.width / 2 + 80, navY, 40, 20).build();
        next.active = actualPage < maxPage;
        this.addRenderableWidget(next);

        this.addRenderableWidget(Button.builder(Component.literal("Clear Selection"), b ->
                        this.minecraft.gui.setScreen(new LunaticBluffSelectionScreen(Set.of(), actualPage, returnScreen)))
                .bounds(this.width / 2 - 72, navY, 144, 20).build());

        Button confirm = Button.builder(Component.literal("Confirm 3 Bluffs").withStyle(ChatFormatting.GREEN), b -> confirm())
                .bounds(this.width / 2 - 125, this.height - 30, 120, 20).build();
        confirm.active = selected.size() == 3;
        this.addRenderableWidget(confirm);

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> back())
                .bounds(this.width / 2 + 5, this.height - 30, 120, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String heading = "Choose 3 fake good roles for the Lunatic — in-play roles are allowed";
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
        if (next.contains(id)) next.remove(id);
        else if (next.size() < 3) next.add(id);
        this.minecraft.gui.setScreen(new LunaticBluffSelectionScreen(next, currentPage, returnScreen));
    }

    private void confirm() {
        if (selected.size() != 3) return;
        ClientLunaticBluffs.set(new ArrayList<>(selected));
        back();
    }

    private void back() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(returnScreen != null ? returnScreen : new NightTeamInfoScreen(true));
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
        for (String bluff : ClientLunaticBluffs.current()) {
            String id = key(bluff);
            if (available.contains(id) && out.size() < 3) out.add(id);
        }
        return out;
    }

    private static List<ScriptRole> availableRoles() {
        if (ClientState.currentScript == null) return List.of();

        // Lunatic bluff information may be false in exactly this way: unlike a
        // real Demon's bluff set, an in-play good character is a valid fake bluff.
        List<ScriptRole> roles = new ArrayList<>();
        for (ScriptRole role : ClientState.currentScript.allRoles()) {
            if (role == null) continue;
            if (role.getTeam() != RoleType.TOWNSFOLK && role.getTeam() != RoleType.OUTSIDER) continue;
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
