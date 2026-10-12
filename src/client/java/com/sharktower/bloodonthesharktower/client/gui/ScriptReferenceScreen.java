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
import net.minecraft.util.FormattedCharSequence;

/** 26.2 visual port of BOTB's tabbed ScriptReferenceScreen. */
public class ScriptReferenceScreen extends Screen {
    private enum Page { ROLES, NIGHT_ORDER, JINXES }

    private Page page = Page.ROLES;
    private int referenceIndex;
    private Script displayedScript;
    private Button previous;
    private Button next;

    public ScriptReferenceScreen() {
        super(Component.literal("Script Reference"));
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int gap = 4;
        int buttonWidth = Math.min(90, (this.width - 16 - gap * 2) / 3);
        int y = 25;

        // Match the original BOTB tab geometry: three 90px buttons separated
        // by 10px gaps, centred as one 290px strip. The previous coordinates
        // overlapped each neighbouring tab by 35px.
        int middleX = center - buttonWidth / 2;
        int leftX = middleX - gap - buttonWidth;
        int rightX = middleX + buttonWidth + gap;

        this.addRenderableWidget(Button.builder(Component.literal("Roles"), b -> { page = Page.ROLES; referenceIndex = 0; })
                .bounds(leftX, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Night Order"), b -> { page = Page.NIGHT_ORDER; referenceIndex = 0; })
                .bounds(middleX, y, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Jinxes"), b -> { page = Page.JINXES; referenceIndex = 0; })
                .bounds(rightX, y, buttonWidth, 20).build());

        previous = this.addRenderableWidget(Button.builder(Component.literal("< Previous"), b -> {
            referenceIndex = Math.max(0, referenceIndex - 1);
        }).bounds(12, this.height - 27, 86, 20).build());
        next = this.addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
            referenceIndex++;
        }).bounds(this.width - 98, this.height - 27, 86, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Script script = ClientState.currentScript;
        if (script != displayedScript) {
            displayedScript = script;
            referenceIndex = 0;
        }
        int maxPage = maxReferencePage(script);
        referenceIndex = Math.min(referenceIndex, maxPage);
        boolean navigation = script != null && page != Page.ROLES;
        previous.visible = navigation;
        next.visible = navigation;
        previous.active = referenceIndex > 0;
        next.active = referenceIndex < maxPage;
        super.extractRenderState(graphics, mouseX, mouseY, delta);
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

        int totalHeight = 0;
        for (RoleType type : order) {
            int n = groups.getOrDefault(type, List.of()).size();
            if (n > 0) totalHeight += 14 + ((n + Math.min(5, n) - 1) / Math.min(5, n)) * 67;
        }
        float scale = Math.min(1.0F, Math.min((this.width - 16) / 400.0F,
                Math.max(1, this.height - 76) / (float) Math.max(1, totalHeight)));
        int nativeWidth = this.width;
        int canvasWidth = 400;
        int left = (nativeWidth - Math.round(canvasWidth * scale)) / 2;
        graphics.pose().pushMatrix();
        graphics.pose().translate(left, 54);
        graphics.pose().scale(scale, scale);
        mouseX = (int) ((mouseX - left) / scale);
        mouseY = (int) ((mouseY - 54) / scale);
        int x0 = 0;
        int y = 0;
        ScriptRole hovered = null;

        for (RoleType type : order) {
            List<ScriptRole> roles = groups.getOrDefault(type, List.of());
            if (roles.isEmpty()) continue;


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
                int rowX = canvasWidth / 2 - rowWidth / 2;

                for (int col = 0; col < rowCount; col++) {
                    ScriptRole role = roles.get(rowStart + col);
                    int cellX = rowX + col * cellWidth;
                    int tokenY = y + row * 67;
                    int tokenX = cellX + (cellWidth - 40) / 2;
                    UiDrawing.roleToken(graphics, role, tokenX, tokenY, 40);
                    String name = role.getDisplayName();
                    graphics.text(this.font, name, cellX + (cellWidth - this.font.width(name)) / 2,
                            tokenY + 45, UiDrawing.teamColor(type), false);
                    if (mouseX >= cellX && mouseX < cellX + cellWidth
                            && mouseY >= tokenY && mouseY < tokenY + 62) {
                        hovered = role;
                    }
                }
            }
            y += rows * 67;
        }

        graphics.pose().popMatrix();
        mouseX = Math.round(left + mouseX * scale);
        mouseY = Math.round(54 + mouseY * scale);

        if (script.bootlegger() != null && !script.bootlegger().isEmpty()) {
            String rules = "Bootlegger: " + String.join("  •  ", script.bootlegger());
            drawCentered(graphics, rules, this.height - 14, UiDrawing.GOLD, true);
        }

        if (hovered != null) drawRoleHover(graphics, hovered, mouseX, mouseY);
    }

    private int referenceRows() {
        return Math.max(3, (this.height - 107) / 12);
    }

    private int maxReferencePage(Script script) {
        if (script == null) return 0;
        int count;
        if (page == Page.NIGHT_ORDER) {
            count = Math.max(ScriptReferenceData.nightOrder(script, true).size(),
                    ScriptReferenceData.nightOrder(script, false).size());
        } else if (page == Page.JINXES) {
            count = jinxLines(script).size();
        } else return 0;
        return Math.max(0, (count - 1) / referenceRows());
    }

    private void drawNightOrder(GuiGraphicsExtractor graphics, Script script) {
        List<String> first = ScriptReferenceData.nightOrder(script, true);
        List<String> other = ScriptReferenceData.nightOrder(script, false);
        int top = 59;
        int half = this.width / 2;
        graphics.text(this.font, "First Night", (half - this.font.width("First Night")) / 2,
                top, UiDrawing.GOLD, true);
        graphics.text(this.font, "Other Nights", half + (half - this.font.width("Other Nights")) / 2,
                top, UiDrawing.GOLD, true);
        int start = referenceIndex * referenceRows();
        int availableWidth = Math.max(50, half - 26);
        int end = start + referenceRows();
        for (int i = start; i < end; i++) {
            int y = top + 17 + (i - start) * 12;
            if (i < first.size()) {
                drawNightEntry(graphics, first.get(i), i, 12, y, availableWidth);
            }
            if (i < other.size()) {
                drawNightEntry(graphics, other.get(i), i, half + 12, y, availableWidth);
            }
        }
        if (first.isEmpty() && other.isEmpty()) {
            drawCentered(graphics, "No night-order data for this script.", top + 43, UiDrawing.MUTED, false);
        }
        drawPageCount(graphics, script);
    }

    private void drawNightEntry(GuiGraphicsExtractor graphics, String name,
                                int index, int x, int y, int maxWidth) {
        String line = (index + 1) + ". " + name;
        if (this.font.width(line) > maxWidth) {
            line = this.font.plainSubstrByWidth(line, Math.max(1, maxWidth - 10)) + "...";
        }
        graphics.text(this.font, line, x, y, UiDrawing.TEXT, false);
    }

    private record ReferenceLine(FormattedCharSequence sequence, int color) {}

    private List<ReferenceLine> jinxLines(Script script) {
        List<ReferenceLine> lines = new ArrayList<>();
        List<ScriptReferenceData.Jinx> jinxes = ScriptReferenceData.jinxes(script);
        if (jinxes.isEmpty()) {
            appendReferenceText(lines, "No character jinxes on this script.", UiDrawing.MUTED);
        } else {
            for (ScriptReferenceData.Jinx jinx : jinxes) {
                appendReferenceText(lines, jinx.first() + " + " + jinx.second(), UiDrawing.GOLD);
                appendReferenceText(lines, jinx.reason(), UiDrawing.TEXT);
                appendReferenceText(lines, "", UiDrawing.TEXT);
            }
        }
        if (script.bootlegger() != null && !script.bootlegger().isEmpty()) {
            appendReferenceText(lines, "Bootlegger / Special Rules", UiDrawing.GOLD);
            for (String rule : script.bootlegger()) {
                appendReferenceText(lines, "- " + rule, UiDrawing.TEXT);
                appendReferenceText(lines, "", UiDrawing.TEXT);
            }
        }
        return lines;
    }

    private void appendReferenceText(List<ReferenceLine> lines, String text, int color) {
        if (text.isBlank()) {
            lines.add(new ReferenceLine(Component.literal(" ").getVisualOrderText(), color));
            return;
        }
        int wrapWidth = Math.min(440, Math.max(120, this.width - 42));
        for (FormattedCharSequence line : this.font.split(Component.literal(text), wrapWidth)) {
            lines.add(new ReferenceLine(line, color));
        }
    }

    private void drawJinxes(GuiGraphicsExtractor graphics, Script script) {
        List<ReferenceLine> lines = jinxLines(script);
        drawCentered(graphics, "Jinxes / Special Rules", 57, UiDrawing.GOLD, true);
        int start = referenceIndex * referenceRows();
        int end = Math.min(lines.size(), start + referenceRows());
        int x = Math.max(12, (this.width - 440) / 2);
        for (int i = start; i < end; i++) {
            ReferenceLine line = lines.get(i);
            graphics.text(this.font, line.sequence(), x,
                    76 + (i - start) * 12, line.color(), false);
        }
        drawPageCount(graphics, script);
    }

    private void drawPageCount(GuiGraphicsExtractor graphics, Script script) {
        int pages = maxReferencePage(script) + 1;
        String label = "Page " + (referenceIndex + 1) + " / " + pages;
        drawCentered(graphics, label, this.height - 21, UiDrawing.MUTED, false);
    }

    private void drawRoleHover(GuiGraphicsExtractor graphics, ScriptRole role, int mouseX, int mouseY) {
        String ability = role.getAbility();
        if (ability == null || ability.isBlank()) return;
        int width = Math.min(190, this.width - 20);
        int x = Math.max(6, Math.min(mouseX + 12, this.width - width - 6));
        int y = Math.max(6, Math.min(mouseY + 10, this.height - 74));
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
