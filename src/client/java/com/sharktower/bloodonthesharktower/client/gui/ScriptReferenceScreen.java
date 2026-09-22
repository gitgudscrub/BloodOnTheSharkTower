package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.Script;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 26.2 visual port of BOTB's tabbed ScriptReferenceScreen. */
public class ScriptReferenceScreen extends Screen {
    private enum Page { ROLES, NIGHT_ORDER, JINXES }

    private Page page = Page.ROLES;

    public ScriptReferenceScreen() {
        super(Component.literal("Script Reference"));
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int buttonWidth = 90;
        int gap = 10;
        int y = 25;

        // Match the original BOTB tab geometry: three 90px buttons separated
        // by 10px gaps, centred as one 290px strip. The previous coordinates
        // overlapped each neighbouring tab by 35px.
        int middleX = center - buttonWidth / 2;
        int leftX = middleX - gap - buttonWidth;
        int rightX = middleX + buttonWidth + gap;

        this.addRenderableWidget(Button.builder(Component.literal("Roles"), b -> page = Page.ROLES)
                .bounds(leftX, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Night Order"), b -> page = Page.NIGHT_ORDER)
                .bounds(middleX, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Jinxes"), b -> page = Page.JINXES)
                .bounds(rightX, y, buttonWidth, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        Script script = ClientState.currentScript;
        if (script == null) {
            drawCentered(graphics, "No script is currently assigned.", this.height / 2 - 5, UiDrawing.TEXT, true);
            return;
        }

        drawCentered(graphics, script.name(), 8, UiDrawing.TEXT, true);
        if (script.author() != null && !script.author().isBlank() && !"Unknown Author".equals(script.author())) {
            drawCentered(graphics, "by " + script.author(), 17, UiDrawing.MUTED, false);
        }

        switch (page) {
            case ROLES -> drawRoles(graphics, script, mouseX, mouseY);
            case NIGHT_ORDER -> drawNightOrder(graphics, script);
            case JINXES -> drawJinxes(graphics, script);
        }
    }

    private void drawRoles(GuiGraphicsExtractor graphics, Script script, int mouseX, int mouseY) {
        Map<RoleType, List<ScriptRole>> groups = new EnumMap<>(RoleType.class);
        for (ScriptRole role : script.allRoles()) groups.computeIfAbsent(role.getTeam(), ignored -> new ArrayList<>()).add(role);
        script.fabled().forEach(role -> groups.computeIfAbsent(RoleType.FABLED, ignored -> new ArrayList<>()).add(role));
        script.loric().forEach(role -> groups.computeIfAbsent(RoleType.LORIC, ignored -> new ArrayList<>()).add(role));

        RoleType[] order = {
                RoleType.TOWNSFOLK, RoleType.OUTSIDER, RoleType.MINION, RoleType.DEMON,
                RoleType.TRAVELER, RoleType.FABLED, RoleType.LORIC
        };

        int listWidth = Math.min(400, this.width - 16);
        int x0 = (this.width - listWidth) / 2;
        int y = 54;
        ScriptRole hovered = null;

        for (RoleType type : order) {
            List<ScriptRole> roles = groups.getOrDefault(type, List.of());
            if (roles.isEmpty()) continue;
            if (y + 62 > this.height - 20) break;

            String header = type.getDisplayName() + " (" + roles.size() + ")";
            graphics.text(this.font, header, x0 + 4, y, UiDrawing.teamColor(type), true);
            y += 14;

            int cellWidth = 75;
            int columns = Math.min(5, Math.max(1, roles.size()));
            int rows = (roles.size() + columns - 1) / columns;

            for (int row = 0; row < rows; row++) {
                int rowStart = row * columns;
                int rowCount = Math.min(columns, roles.size() - rowStart);
                int rowWidth = rowCount * cellWidth;
                int rowX = this.width / 2 - rowWidth / 2;

                for (int col = 0; col < rowCount; col++) {
                    ScriptRole role = roles.get(rowStart + col);
                    int cellX = rowX + col * cellWidth;
                    int tokenY = y + row * 67;
                    int tokenX = cellX + (cellWidth - 40) / 2;
                    UiDrawing.roleToken(graphics, role, tokenX, tokenY, 40);
                    String name = role.getDisplayName();
                    graphics.text(this.font, name, cellX + (cellWidth - this.font.width(name)) / 2,
                            tokenY + 45, UiDrawing.TEXT, false);
                    if (mouseX >= cellX && mouseX < cellX + cellWidth
                            && mouseY >= tokenY && mouseY < tokenY + 62) {
                        hovered = role;
                    }
                }
            }
            y += rows * 67;
        }

        if (script.bootlegger() != null && !script.bootlegger().isEmpty()) {
            String rules = "Bootlegger: " + String.join("  •  ", script.bootlegger());
            drawCentered(graphics, rules, this.height - 14, UiDrawing.GOLD, true);
        }

        if (hovered != null) drawRoleHover(graphics, hovered, mouseX, mouseY);
    }

    private void drawNightOrder(GuiGraphicsExtractor graphics, Script script) {
        int top = 58;
        int half = this.width / 2;
        graphics.text(this.font, "First Night", half / 2 - this.font.width("First Night") / 2, top, UiDrawing.GOLD, true);
        graphics.text(this.font, "Other Nights", half + half / 2 - this.font.width("Other Nights") / 2, top, UiDrawing.GOLD, true);

        List<String> first = script.firstNightOrder() == null ? List.of() : script.firstNightOrder();
        List<String> other = script.otherNightOrder() == null ? List.of() : script.otherNightOrder();
        int maxRows = Math.max(1, (this.height - top - 28) / 13);
        for (int i = 0; i < Math.min(maxRows, first.size()); i++) {
            String line = (i + 1) + ". " + first.get(i);
            graphics.text(this.font, line, 16, top + 18 + i * 13, UiDrawing.TEXT, false);
        }
        for (int i = 0; i < Math.min(maxRows, other.size()); i++) {
            String line = (i + 1) + ". " + other.get(i);
            graphics.text(this.font, line, half + 16, top + 18 + i * 13, UiDrawing.TEXT, false);
        }
        if (first.isEmpty() && other.isEmpty()) {
            drawCentered(graphics, "This script does not provide custom night-order data.", top + 42, UiDrawing.MUTED, false);
        }
    }

    private void drawJinxes(GuiGraphicsExtractor graphics, Script script) {
        int y = 60;
        drawCentered(graphics, "Jinxes / Special Rules", y, UiDrawing.GOLD, true);
        y += 22;

        boolean drew = false;
        if (script.bootlegger() != null) {
            for (String rule : script.bootlegger()) {
                y = drawWrapped(graphics, "• " + rule, 30, y, this.width - 60, 11, 5);
                y += 5;
                drew = true;
            }
        }
        if (!script.fabled().isEmpty() || !script.loric().isEmpty()) {
            graphics.text(this.font, "Fabled / Loric in play:", 30, y, UiDrawing.TEXT, true);
            y += 15;
            List<ScriptRole> extras = new ArrayList<>();
            extras.addAll(script.fabled());
            extras.addAll(script.loric());
            for (ScriptRole role : extras) {
                UiDrawing.roleToken(graphics, role, 34, y, 26);
                graphics.text(this.font, role.getDisplayName(), 68, y + 8, UiDrawing.TEXT, false);
                y += 31;
                drew = true;
                if (y > this.height - 30) break;
            }
        }
        if (!drew) drawCentered(graphics, "No jinx or special-rule data is present on this script.", y + 10, UiDrawing.MUTED, false);
    }

    private void drawRoleHover(GuiGraphicsExtractor graphics, ScriptRole role, int mouseX, int mouseY) {
        String ability = role.getAbility();
        if (ability == null || ability.isBlank()) return;
        int width = Math.min(190, this.width - 20);
        int x = Math.min(mouseX + 12, this.width - width - 6);
        int y = Math.min(mouseY + 10, this.height - 74);
        UiDrawing.panel(graphics, x, y, width, 66);
        graphics.text(this.font, role.getDisplayName(), x + 6, y + 6, UiDrawing.teamColor(role.getTeam()), true);
        drawWrapped(graphics, ability, x + 6, y + 20, width - 12, 10, 4);
    }

    private int drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int lineHeight, int maxLines) {
        StringBuilder line = new StringBuilder();
        int count = 0;
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && this.font.width(candidate) > width) {
                graphics.text(this.font, line.toString(), x, y + count * lineHeight, UiDrawing.TEXT, false);
                count++;
                if (count >= maxLines) return y + count * lineHeight;
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty() && count < maxLines) {
            graphics.text(this.font, line.toString(), x, y + count * lineHeight, UiDrawing.TEXT, false);
            count++;
        }
        return y + count * lineHeight;
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, colour, shadow);
    }
}
