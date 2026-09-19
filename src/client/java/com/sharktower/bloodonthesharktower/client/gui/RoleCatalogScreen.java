package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 26.2 visual port of BOTB's RoleCatalogScreen.
 *
 * The original uses a 375px-wide list, five 75px cells per row and 40px role
 * tokens with a team-coloured backplate. We preserve that geometry here while
 * using page buttons in place of the old list-widget scrollbar for now.
 */
public class RoleCatalogScreen extends Screen {
    private boolean showingExtraRoles = false;
    private int page = 0;

    public RoleCatalogScreen() {
        super(Component.literal("Role Catalog"));
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        this.addRenderableWidget(Button.builder(
                        Component.literal(showingExtraRoles ? "Main Roles" : "Extra Roles"),
                        b -> {
                            showingExtraRoles = !showingExtraRoles;
                            page = 0;
                            b.setMessage(Component.literal(showingExtraRoles ? "Main Roles" : "Extra Roles"));
                        })
                .bounds(center + 105, 20, 90, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                    if (page > 0) page--;
                })
                .bounds(center - 48, this.height - 28, 28, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> page++)
                .bounds(center + 20, this.height - 28, 28, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        drawCentered(graphics, this.title.getString(), 8, UiDrawing.TEXT, true);

        List<Role> roles = currentRoles();
        int listWidth = Math.min(375, Math.max(75, this.width - 20));
        int cellWidth = listWidth / 5;
        int rowHeight = 70;
        int top = 50;
        int bottomReserve = 38;
        int rowsVisible = Math.max(1, (this.height - top - bottomReserve) / rowHeight);
        int perPage = rowsVisible * 5;
        int maxPage = Math.max(0, (roles.size() - 1) / perPage);
        if (page > maxPage) page = maxPage;

        int start = page * perPage;
        int end = Math.min(roles.size(), start + perPage);
        int listX = (this.width - listWidth) / 2;

        Role hovered = null;
        for (int i = start; i < end; i++) {
            Role role = roles.get(i);
            int local = i - start;
            int col = local % 5;
            int row = local / 5;
            int cellX = listX + col * cellWidth;
            int cellY = top + row * rowHeight;
            int tokenX = cellX + (cellWidth - 40) / 2;
            int tokenY = cellY + 5;

            ScriptRole scriptRole = new ScriptRole.Official(role);
            UiDrawing.roleToken(graphics, scriptRole, tokenX, tokenY, 40);

            String name = role.getDisplayName();
            int nameX = cellX + (cellWidth - this.font.width(name)) / 2;
            graphics.text(this.font, name, nameX, cellY + 49, UiDrawing.TEXT, false);

            if (mouseX >= cellX && mouseX < cellX + cellWidth
                    && mouseY >= cellY && mouseY < cellY + rowHeight) {
                hovered = role;
            }
        }

        String mode = showingExtraRoles ? "Extra Roles" : "Main Roles";
        String pageText = mode + "   " + (page + 1) + "/" + (maxPage + 1) + "   " + roles.size() + " roles";
        drawCentered(graphics, pageText, this.height - 42, UiDrawing.MUTED, false);

        if (hovered != null) {
            drawHoverDescription(graphics, hovered, mouseX, mouseY);
        }
    }

    private List<Role> currentRoles() {
        return Arrays.stream(Role.values())
                .filter(role -> role != Role.NO_ROLE)
                .filter(role -> showingExtraRoles
                        ? role.getType() == RoleType.TRAVELER || role.getType() == RoleType.FABLED || role.getType() == RoleType.LORIC
                        : role.getType() == RoleType.TOWNSFOLK || role.getType() == RoleType.OUTSIDER
                        || role.getType() == RoleType.MINION || role.getType() == RoleType.DEMON)
                .toList();
    }

    private void drawHoverDescription(GuiGraphicsExtractor graphics, Role role, int mouseX, int mouseY) {
        String description = role.getDescription();
        if (description == null || description.isBlank()) return;

        int width = Math.min(180, Math.max(120, this.width - 20));
        int x = Math.min(mouseX + 12, this.width - width - 6);
        int y = Math.min(mouseY + 10, this.height - 72);
        UiDrawing.panel(graphics, x, y, width, 62);
        graphics.text(this.font, role.getDisplayName(), x + 6, y + 6, UiDrawing.teamColor(role.getType()), true);
        drawWrapped(graphics, description, x + 6, y + 20, width - 12, 10, 4);
    }

    private void drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int lineHeight, int maxLines) {
        StringBuilder line = new StringBuilder();
        int lineCount = 0;
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && this.font.width(candidate) > maxWidth) {
                graphics.text(this.font, line.toString(), x, y + lineCount * lineHeight, UiDrawing.TEXT, false);
                lineCount++;
                if (lineCount >= maxLines) return;
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty() && lineCount < maxLines) {
            graphics.text(this.font, line.toString(), x, y + lineCount * lineHeight, UiDrawing.TEXT, false);
        }
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        int x = (this.width - this.font.width(text)) / 2;
        graphics.text(this.font, text, x, y, colour, shadow);
    }
}
