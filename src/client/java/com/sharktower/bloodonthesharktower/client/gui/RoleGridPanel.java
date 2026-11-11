package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A scrolling, team-grouped grid of role tokens with a hover card.
 *
 * Shared by the Script Builder and the Role Catalog so both read the same way as
 * the Script Reference: a coloured header per team (Townsfolk, Outsider, Minion,
 * Demon, Traveller, Fabled, Loric), 36px tokens on a team backplate, the name
 * under each token, and the ability text on hover. The panel is not a widget:
 * the owning screen hands it bounds, a role list and a search query, forwards
 * clicks and wheel events, and draws it from its own extractRenderState.
 *
 * An optional {@link #selected} predicate draws a gold ring and tick on chosen
 * tokens (Script Builder); the catalog leaves it null.
 */
final class RoleGridPanel {
    static final int CELL_W = 78;
    static final int CELL_H = 66;
    static final int TOKEN = 36;
    static final int HEADER_H = 16;
    static final int GROUP_GAP = 6;
    static final int SCROLLBAR_W = 4;

    private static final RoleType[] ORDER = {
            RoleType.TOWNSFOLK, RoleType.OUTSIDER, RoleType.MINION, RoleType.DEMON,
            RoleType.TRAVELER, RoleType.FABLED, RoleType.LORIC
    };

    private record Group(RoleType team, List<ScriptRole> roles) {}

    private final Font font;
    private final List<Group> groups = new ArrayList<>();
    private int x;
    private int y;
    private int width;
    private int height;
    private int scroll;
    private int contentHeight;
    private int totalRoles;
    private ScriptRole hovered;

    Predicate<ScriptRole> selected;

    RoleGridPanel(Font font) {
        this.font = font;
    }

    void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(CELL_W, width);
        this.height = Math.max(CELL_H, height);
        clampScroll();
    }

    /** Replaces the roles shown, grouped by team and filtered by {@code query} (name or ability). */
    void setRoles(List<ScriptRole> roles, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Map<RoleType, List<ScriptRole>> byTeam = new EnumMap<>(RoleType.class);
        totalRoles = 0;
        for (ScriptRole role : roles) {
            if (!matches(role, needle)) continue;
            byTeam.computeIfAbsent(role.getTeam(), ignored -> new ArrayList<>()).add(role);
            totalRoles++;
        }
        groups.clear();
        for (RoleType team : ORDER) {
            List<ScriptRole> list = byTeam.get(team);
            if (list != null && !list.isEmpty()) groups.add(new Group(team, list));
        }
        clampScroll();
    }

    private static boolean matches(ScriptRole role, String needle) {
        if (needle.isEmpty()) return true;
        if (role.getDisplayName().toLowerCase(Locale.ROOT).contains(needle)) return true;
        String ability = role.getAbility();
        return ability != null && ability.toLowerCase(Locale.ROOT).contains(needle);
    }

    int shownCount() {
        return totalRoles;
    }

    ScriptRole hovered() {
        return hovered;
    }

    private int columns() {
        return Math.max(1, (width - SCROLLBAR_W - 4) / CELL_W);
    }

    private int gridLeft() {
        return x + (width - SCROLLBAR_W - columns() * CELL_W) / 2;
    }

    private void clampScroll() {
        int columns = columns();
        contentHeight = 0;
        for (Group group : groups) {
            int rows = (group.roles().size() + columns - 1) / columns;
            contentHeight += HEADER_H + rows * CELL_H + GROUP_GAP;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - height)));
    }

    /** Draws the grid inside its bounds; the hover card is drawn separately so it sits on top. */
    void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        clampScroll();
        hovered = null;
        boolean inside = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int columns = columns();
        int left = gridLeft();

        graphics.enableScissor(x, y, x + width, y + height);
        int cursor = y - scroll;
        for (Group group : groups) {
            List<ScriptRole> roles = group.roles();
            int rows = (roles.size() + columns - 1) / columns;
            if (cursor + HEADER_H > y && cursor < y + height) {
                String header = group.team().getDisplayName() + " (" + roles.size() + ")";
                graphics.text(font, header, left + 2, cursor + 3, UiDrawing.teamColor(group.team()), true);
            }
            cursor += HEADER_H;
            for (int row = 0; row < rows; row++) {
                int rowY = cursor + row * CELL_H;
                if (rowY + CELL_H <= y || rowY >= y + height) continue;
                int count = Math.min(columns, roles.size() - row * columns);
                for (int col = 0; col < count; col++) {
                    ScriptRole role = roles.get(row * columns + col);
                    int cellX = left + col * CELL_W;
                    drawCell(graphics, role, cellX, rowY);
                    if (inside && mouseX >= cellX && mouseX < cellX + CELL_W
                            && mouseY >= rowY && mouseY < rowY + CELL_H) {
                        hovered = role;
                        graphics.outline(cellX + (CELL_W - TOKEN) / 2 - 2, rowY + 2, TOKEN + 4, TOKEN + 4, UiDrawing.BORDER);
                    }
                }
            }
            cursor += rows * CELL_H + GROUP_GAP;
        }
        graphics.disableScissor();

        if (contentHeight > height) {
            int trackX = x + width - SCROLLBAR_W;
            graphics.fill(trackX, y, trackX + SCROLLBAR_W, y + height, 0x60000000);
            int thumbH = Math.max(12, height * height / contentHeight);
            int thumbY = y + (height - thumbH) * scroll / Math.max(1, contentHeight - height);
            graphics.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, UiDrawing.BORDER);
        }

        if (groups.isEmpty()) {
            String empty = "No roles match.";
            graphics.text(font, empty, x + (width - font.width(empty)) / 2, y + height / 2 - 4, UiDrawing.MUTED, false);
        }
    }

    private void drawCell(GuiGraphicsExtractor graphics, ScriptRole role, int cellX, int cellY) {
        int tokenX = cellX + (CELL_W - TOKEN) / 2;
        int tokenY = cellY + 4;
        UiDrawing.roleToken(graphics, role, tokenX, tokenY, TOKEN);
        boolean chosen = selected != null && selected.test(role);
        if (chosen) {
            graphics.outline(tokenX - 2, tokenY - 2, TOKEN + 4, TOKEN + 4, UiDrawing.GOLD);
            graphics.text(font, "✓", tokenX + TOKEN - 6, tokenY - 3, UiDrawing.GOLD, true);
        }
        int colour = chosen ? UiDrawing.GOLD : (selected != null ? UiDrawing.MUTED : UiDrawing.TEXT);
        int maxWidth = CELL_W - 4;
        List<FormattedCharSequence> lines = font.split(Component.literal(role.getDisplayName()), maxWidth);
        int lineY = tokenY + TOKEN + 4;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            FormattedCharSequence line = lines.get(i);
            graphics.text(font, line, cellX + (CELL_W - font.width(line)) / 2, lineY, colour, false);
            lineY += 10;
        }
    }

    /** Draws the name, team and ability of the hovered role; call after the rest of the screen. */
    void drawHoverCard(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (hovered == null) return;
        int cardWidth = Math.min(200, screenWidth - 20);
        List<FormattedCharSequence> lines = new ArrayList<>();
        String ability = hovered.getAbility();
        if (ability != null && !ability.isBlank()) {
            lines.addAll(font.split(Component.literal(ability), cardWidth - 12));
        }
        int cardHeight = 6 + 10 + 10 + (lines.isEmpty() ? 0 : 4 + lines.size() * 10) + 6;
        int cardX = Math.max(6, Math.min(mouseX + 12, screenWidth - cardWidth - 6));
        int cardY = Math.max(6, Math.min(mouseY + 10, screenHeight - cardHeight - 6));
        UiDrawing.panel(graphics, cardX, cardY, cardWidth, cardHeight);
        graphics.text(font, hovered.getDisplayName(), cardX + 6, cardY + 6, UiDrawing.teamColor(hovered.getTeam()), true);
        graphics.text(font, hovered.getTeam().getDisplayName(), cardX + 6, cardY + 16, UiDrawing.MUTED, false);
        int lineY = cardY + 30;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, cardX + 6, lineY, UiDrawing.TEXT, false);
            lineY += 10;
        }
    }

    /** The role under the pointer, or null; the owning screen decides what a click means. */
    ScriptRole roleAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return null;
        int columns = columns();
        int left = gridLeft();
        int cursor = y - scroll;
        for (Group group : groups) {
            List<ScriptRole> roles = group.roles();
            int rows = (roles.size() + columns - 1) / columns;
            cursor += HEADER_H;
            for (int row = 0; row < rows; row++) {
                int rowY = cursor + row * CELL_H;
                if (mouseY < rowY || mouseY >= rowY + CELL_H) continue;
                if (mouseX < left) return null;
                int col = (int) ((mouseX - left) / CELL_W);
                int index = row * columns + col;
                if (col >= columns || index >= roles.size()) return null;
                return roles.get(index);
            }
            cursor += rows * CELL_H + GROUP_GAP;
        }
        return null;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
        if (contentHeight <= height) return false;
        scroll -= (int) Math.round(scrollY * 24);
        clampScroll();
        return true;
    }
}
