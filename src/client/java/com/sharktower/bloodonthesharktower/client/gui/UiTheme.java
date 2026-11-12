package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.client.config.ClientSettings;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * The Clocktower look, in one place.
 *
 * Two modes share one structure: round parchment tokens with a gold rim,
 * framed panels, a serif display face (Cinzel) for titles and role names.
 * <b>Candlelit</b> is ink-dark panels with gold frames; <b>Parchment</b> is
 * light parchment panels with ink text. {@link #apply()} pushes the mode's
 * colours into {@link UiDrawing} and {@link SharktowerButton}, so every
 * screen that reads those fields follows the theme without knowing it.
 */
public final class UiTheme {
    public enum Mode {
        CANDLELIT("Candlelit"),
        PARCHMENT("Parchment");

        public final String label;

        Mode(String label) {
            this.label = label;
        }

        public Mode next() {
            return this == CANDLELIT ? PARCHMENT : CANDLELIT;
        }

        public static Mode parse(String id) {
            try {
                return valueOf(id == null ? "" : id.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return CANDLELIT;
            }
        }
    }

    /** Fixed across both modes: the game's meanings never change colour. */
    public static final int GOLD = 0xFFC9A24A;
    public static final int GOLD_DIM = 0xFF8A6D2F;
    public static final int GOLD_SHADOW = 0xFF5C4720;
    public static final int AMBER = 0xFFE0A446;
    public static final int BLOOD = 0xFF8E1B1B;
    public static final int BLOOD_LIGHT = 0xFFE26B5E;
    public static final int GOOD_BADGE = 0xFF2F5F9C;
    public static final int EVIL_BADGE = 0xFF8E1B1B;
    public static final int INK = 0xFF0E0B10;
    public static final int PARCHMENT = 0xFFE9DCC0;
    public static final int PARCHMENT_DARK = 0xFFCDB890;
    public static final int PARCHMENT_INK = 0xFF2B2118;
    public static final int CHIP_HARMFUL = 0xFFB23A2E;
    public static final int CHIP_PROTECTIVE = 0xFF3F8E4C;
    public static final int CHIP_NEUTRAL = 0xFFB8A878;

    private static final Identifier TOKEN_DISC = tex("token_disc");
    private static final Identifier DISC = tex("disc");
    private static final Identifier RING = tex("ring");
    private static final Identifier GLOW = tex("glow");
    private static final Identifier SHROUD = tex("shroud");

    private static final Style DISPLAY = Style.EMPTY.withFont(new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, "display")));
    private static final Style TITLE = Style.EMPTY.withFont(new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, "title")));

    private UiTheme() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, "textures/theme/" + name + ".png");
    }

    public static Mode mode() {
        return Mode.parse(ClientSettings.theme);
    }

    public static boolean dark() {
        return mode() == Mode.CANDLELIT;
    }

    /** Pushes the current mode's colours into the shared colour fields. */
    public static void apply() {
        if (dark()) {
            UiDrawing.PANEL = 0xF016121A;
            UiDrawing.PANEL_SOFT = 0xD01A1520;
            UiDrawing.BORDER = GOLD_DIM;
            UiDrawing.TEXT = 0xFFF1E9D6;
            UiDrawing.MUTED = 0xFFA89C85;
            UiDrawing.GOLD = AMBER;
            UiDrawing.DEAD = 0xFF8E8373;
            SharktowerButton.BACKGROUND = 0xF01E1620;
            SharktowerButton.BACKGROUND_HOVER = 0xF02E2228;
            SharktowerButton.BACKGROUND_INACTIVE = 0xB0141018;
            SharktowerButton.BORDER_SOFT = 0xFF6A5430;
            SharktowerButton.BORDER_HOVER = GOLD;
            SharktowerButton.TEXT_INACTIVE = 0xFF7D7466;
        } else {
            UiDrawing.PANEL = 0xF8EFE5CC;
            UiDrawing.PANEL_SOFT = 0xE8F3EAD5;
            UiDrawing.BORDER = 0xFF9C7A2E;
            UiDrawing.TEXT = PARCHMENT_INK;
            UiDrawing.MUTED = 0xFF7A6A4C;
            UiDrawing.GOLD = 0xFF8A5A10;
            UiDrawing.DEAD = 0xFF8E8373;
            SharktowerButton.BACKGROUND = 0xFFF7EFDC;
            SharktowerButton.BACKGROUND_HOVER = 0xFFFFF8E8;
            SharktowerButton.BACKGROUND_INACTIVE = 0xC0E3D7BB;
            SharktowerButton.BORDER_SOFT = 0xFF9C7A2E;
            SharktowerButton.BORDER_HOVER = 0xFF5C4315;
            SharktowerButton.TEXT_INACTIVE = 0xFF9C8E74;
        }
    }

    // ----------------------------------------------------------------- text

    /** Display face (Cinzel) at body size: role names, headers, button labels that fit. */
    public static Component display(String text) {
        return Component.literal(text).withStyle(DISPLAY);
    }

    /** Display face at title size, for screen titles. */
    public static Component title(String text) {
        return Component.literal(text).withStyle(TITLE);
    }

    /** Draws a centred screen title with a thin gold rule under it. */
    public static void drawTitle(GuiGraphicsExtractor graphics, Font font, String text, int centerX, int y) {
        Component title = title(text.toUpperCase(Locale.ROOT));
        int width = font.width(title);
        graphics.text(font, title, centerX - width / 2, y, dark() ? GOLD : 0xFF5C4315, dark());
        int ruleY = y + 16;
        int half = Math.max(40, width / 2 + 16);
        graphics.fill(centerX - half, ruleY, centerX + half, ruleY + 1, GOLD_DIM);
    }

    /** Small gold section label in the display face: "NIGHT CYCLE". */
    public static void drawSectionLabel(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
        graphics.text(font, display(text.toUpperCase(Locale.ROOT)), x, y, dark() ? GOLD : 0xFF7A5A1E, dark());
    }

    // --------------------------------------------------------------- panels

    /** Framed panel: fill, gold frame, then a dark hairline outside it. */
    public static void framedPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, UiDrawing.PANEL);
        graphics.outline(x, y, width, height, GOLD_DIM);
        graphics.outline(x - 1, y - 1, width + 2, height + 2, dark() ? 0xFF0A0810 : GOLD_SHADOW);
        if (!dark()) graphics.outline(x + 2, y + 2, width - 4, height - 4, 0xFFE4D5B2);
    }

    /** Header band across the top of a framed panel. */
    public static void headerBand(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int width) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + 18, dark() ? 0xFF1E1822 : 0xFFE6D9BB);
        graphics.fill(x + 1, y + 18, x + width - 1, y + 19, GOLD_SHADOW);
        Component label = display(text.toUpperCase(Locale.ROOT));
        graphics.text(font, label, x + 8, y + 5, dark() ? GOLD : 0xFF5C4315, dark());
    }

    /** Whole-screen backdrop behind a Sharktower screen: vignette (dark) or a parchment sheet (light). */
    public static void screenBackdrop(GuiGraphicsExtractor graphics, int width, int height) {
        if (dark()) {
            graphics.fillGradient(0, 0, width, height, 0x90120D14, 0xC808060A);
        } else {
            graphics.fill(0, 0, width, height, 0xF4EBDFC3);
            graphics.fillGradient(0, 0, width, 6, 0x60000000, 0x00000000);
            graphics.outline(3, 3, width - 6, height - 6, GOLD_DIM);
            graphics.outline(5, 5, width - 10, height - 10, 0xFFF5EDD9);
        }
    }

    // --------------------------------------------------------------- tokens

    /** Round parchment token with a gold rim and the role's art inside it. */
    public static void roundToken(GuiGraphicsExtractor graphics, ScriptRole role, int x, int y, int size) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TOKEN_DISC, x, y, 0, 0, size, size, size, size);
        if (role != null && role.getIcon() != null && size > 10) {
            int inset = Math.max(3, size / 7);
            UiDrawing.roleIcon(graphics, role, x + inset, y + inset, size - inset * 2);
        }
    }

    /** Empty round slot: a faint disc with a dashed feel, for unfilled bluffs and seats. */
    public static void emptyToken(GuiGraphicsExtractor graphics, int x, int y, int size) {
        disc(graphics, x, y, size, dark() ? 0x50E9DCC0 : 0x2A000000);
        ring(graphics, x, y, size, dark() ? 0x90C9A24A : 0x909C7A2E);
    }

    public static void disc(GuiGraphicsExtractor graphics, int x, int y, int size, int colour) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, DISC, x, y, 0, 0, size, size, size, size, colour);
    }

    /** A 3px (at 64) ring around a token; {@code size} is the ring's outer diameter. */
    public static void ring(GuiGraphicsExtractor graphics, int x, int y, int size, int colour) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, RING, x, y, 0, 0, size, size, size, size, colour);
    }

    /** Soft halo behind a token (on the block, hovered). */
    public static void glow(GuiGraphicsExtractor graphics, int x, int y, int size, int colour) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, GLOW, x, y, 0, 0, size, size, size, size, colour);
    }

    /** Death shroud badge, drawn at a token's top-left corner. */
    public static void shroud(GuiGraphicsExtractor graphics, int x, int y, int size) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, SHROUD, x, y, 0, 0, size, size, size, size);
    }

    /** Small lettered disc: "G"/"E" alignment badges, seat numbers. */
    public static void badge(GuiGraphicsExtractor graphics, int x, int y, int size, int fill, String letter, int textColour) {
        disc(graphics, x - 1, y - 1, size + 2, dark() ? INK : 0xFFF5EDD9);
        disc(graphics, x, y, size, fill);
        Font font = Minecraft.getInstance().font;
        int w = font.width(letter);
        graphics.text(font, letter, x + (size - w) / 2 + (size % 2 == 0 ? 1 : 0), y + (size - 8) / 2 + 1, textColour, false);
    }

    // ---------------------------------------------------------------- chips

    /** Reminder chip background: parchment on dark mode, ink on light mode, so it always contrasts. */
    public static int chipBackground() {
        return dark() ? 0xFFE6D9BB : 0xFF2B2118;
    }

    public static int chipText() {
        return dark() ? PARCHMENT_INK : 0xFFF1E9D6;
    }

    public static int chipBorder() {
        return dark() ? GOLD_DIM : 0xFF9C7A2E;
    }
}
