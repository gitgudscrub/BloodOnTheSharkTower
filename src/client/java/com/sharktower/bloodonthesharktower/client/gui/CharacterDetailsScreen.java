package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 26.2 visual port of BOTB's CharacterDetailsScreen.
 *
 * The original screen is deliberately sparse: a large character token at the
 * top, character/team headings, wrapped ability text, then detail/almanac
 * content below. This keeps that visual hierarchy without the temporary card
 * used by 0.6.0.
 */
public class CharacterDetailsScreen extends Screen {
    private final ScriptRole role;

    public CharacterDetailsScreen(ScriptRole role) {
        super(Component.literal(role == null ? "Character Details" : role.getDisplayName()));
        this.role = role;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (role == null) {
            drawCentered(graphics, "No role is currently assigned.", this.height / 2 - 5, UiDrawing.MUTED, false);
            return;
        }

        // BOTB uses a large, unframed token at the top of the role details page.
        int iconSize = 64;
        int top = 18;
        UiDrawing.roleIcon(graphics, role, this.width / 2 - iconSize / 2, top, iconSize);

        int y = top + iconSize + 5;
        drawCentered(graphics, role.getDisplayName(), y, UiDrawing.TEXT, true);
        y += 12;
        drawCentered(graphics, role.getTeam().getDisplayName(), y, UiDrawing.teamColor(role.getTeam()), false);
        y += 19;

        String ability = role.getAbility();
        if (ability == null || ability.isBlank()) ability = "No ability text available.";
        y = drawCenteredWrapped(graphics, ability, y, Math.min(360, this.width - 40), UiDrawing.TEXT, 11, 7);

        y += 8;
        String alignment = role.isDefaultGood() ? "Good" : "Evil";
        drawCentered(graphics, "Default alignment: " + alignment, y,
                role.isDefaultGood() ? UiDrawing.GOOD : UiDrawing.EVIL, false);

        // Custom/Fabled details are populated later by the Almanac port. Keep
        // the same lower-page breathing room as the original rather than using
        // a full-screen panel.
        if (y + 25 < this.height - 35) {
            drawCentered(graphics, "Blood on the Sharktower", this.height - 42, UiDrawing.MUTED, false);
        }
    }

    private int drawCenteredWrapped(
            GuiGraphicsExtractor graphics,
            String text,
            int startY,
            int maxWidth,
            int colour,
            int lineHeight,
            int maxLines
    ) {
        StringBuilder line = new StringBuilder();
        int y = startY;
        int lines = 0;
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && this.font.width(candidate) > maxWidth) {
                drawCentered(graphics, line.toString(), y, colour, false);
                y += lineHeight;
                lines++;
                if (lines >= maxLines) return y;
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty() && lines < maxLines) {
            drawCentered(graphics, line.toString(), y, colour, false);
            y += lineHeight;
        }
        return y;
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int y, int colour, boolean shadow) {
        graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, y, colour, shadow);
    }
}
