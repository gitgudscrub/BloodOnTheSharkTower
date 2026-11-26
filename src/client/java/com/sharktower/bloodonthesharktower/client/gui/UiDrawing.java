package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Shared UI drawing helpers and the colour rule every screen follows.
 *
 * Buttons are {@link SharktowerButton}s (dark panel, soft border, gold hover ring).
 * Label colours mean something, so use them consistently:
 * <ul>
 *   <li>white: navigation, toggles and movement (Back, Done, Shuffle, Send to Seats);</li>
 *   <li>gold: the action that advances the game (Send Roles, Nominate, Start Vote);</li>
 *   <li>green: confirm or resolve (Execute - lives, Confirm);</li>
 *   <li>red: irreversible or evil-team actions (Game End, Execute - dies);</li>
 *   <li>grey/muted: cancel and secondary actions;</li>
 *   <li>light purple: Traveller and exile flows only;</li>
 *   <li>team colours are reserved for role tokens and names, never for buttons.</li>
 * </ul>
 */
public final class UiDrawing {
    // Drawing a Component makes Minecraft run its right-to-left text pass
    // (ICU Bidi + Arabic shaping) on every frame, even for English. The HUD
    // redraws the same few dozen labels every frame, so keep the result.
    // Render thread only. Cleared when the language changes.
    private static final int ORDERED_CACHE_SIZE = 512;
    private static final java.util.Map<net.minecraft.network.chat.Component, net.minecraft.util.FormattedCharSequence> ORDERED =
            new java.util.LinkedHashMap<>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<net.minecraft.network.chat.Component, net.minecraft.util.FormattedCharSequence> eldest) {
                    return size() > ORDERED_CACHE_SIZE;
                }
            };
    // Plain strings take the same right-to-left pass in GuiGraphicsExtractor.
    private static final java.util.Map<String, net.minecraft.util.FormattedCharSequence> ORDERED_STRINGS =
            new java.util.LinkedHashMap<>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, net.minecraft.util.FormattedCharSequence> eldest) {
                    return size() > ORDERED_CACHE_SIZE;
                }
            };
    private static net.minecraft.locale.Language orderedLanguage;

    private static net.minecraft.locale.Language currentLanguage() {
        net.minecraft.locale.Language language = net.minecraft.locale.Language.getInstance();
        if (language != orderedLanguage) {
            ORDERED.clear();
            ORDERED_STRINGS.clear();
            orderedLanguage = language;
        }
        return language;
    }

    /** The draw-ready form of a plain string, cached (see ORDERED_STRINGS). */
    public static net.minecraft.util.FormattedCharSequence ordered(String text) {
        net.minecraft.locale.Language language = currentLanguage();
        net.minecraft.util.FormattedCharSequence cached = ORDERED_STRINGS.get(text);
        if (cached == null) {
            cached = language.getVisualOrder(net.minecraft.network.chat.FormattedText.of(text));
            ORDERED_STRINGS.put(text, cached);
        }
        return cached;
    }

    /** The draw-ready form of a Component, cached (see ORDERED). */
    public static net.minecraft.util.FormattedCharSequence ordered(net.minecraft.network.chat.Component text) {
        currentLanguage();
        net.minecraft.util.FormattedCharSequence cached = ORDERED.get(text);
        if (cached == null) {
            cached = text.getVisualOrderText();
            // Key on a copy so a caller changing its Component later cannot corrupt the map.
            ORDERED.put(text.copy(), cached);
        }
        return cached;
    }

    public static void text(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                            net.minecraft.network.chat.Component text, int x, int y, int colour, boolean shadow) {
        graphics.text(font, ordered(text), x, y, colour, shadow);
    }

    public static void text(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                            net.minecraft.network.chat.Component text, int x, int y, int colour) {
        graphics.text(font, ordered(text), x, y, colour);
    }

    public static void text(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                            String text, int x, int y, int colour, boolean shadow) {
        graphics.text(font, ordered(text), x, y, colour, shadow);
    }

    public static void text(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                            String text, int x, int y, int colour) {
        graphics.text(font, ordered(text), x, y, colour);
    }

    public static void text(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                            net.minecraft.util.FormattedCharSequence text, int x, int y, int colour, boolean shadow) {
        graphics.text(font, text, x, y, colour, shadow);
    }

    public static void text(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                            net.minecraft.util.FormattedCharSequence text, int x, int y, int colour) {
        graphics.text(font, text, x, y, colour);
    }

    public static void centeredText(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                                    net.minecraft.network.chat.Component text, int x, int y, int colour) {
        graphics.centeredText(font, ordered(text), x, y, colour);
    }

    public static void centeredText(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                                    String text, int x, int y, int colour) {
        graphics.centeredText(font, ordered(text), x, y, colour);
    }

    public static void centeredText(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                                    net.minecraft.util.FormattedCharSequence text, int x, int y, int colour) {
        graphics.centeredText(font, text, x, y, colour);
    }

    /** Width of a Component using the cached draw-ready form. */
    public static int width(net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text) {
        return font.width(ordered(text));
    }
    // Theme-dependent (set by UiTheme.apply(); read at draw time, so not final).
    public static int PANEL = 0xD0101014;
    public static int PANEL_SOFT = 0xB8202028;
    public static int BORDER = 0xFF8E8E98;
    public static int TEXT = 0xFFFFFFFF;
    public static int MUTED = 0xFFB7B7C0;
    public static int GOLD = 0xFFFFD166;
    public static int DEAD = 0xFF8A8A8A;
    // Fixed meanings.
    public static final int GOOD = 0xFF55AAFF;
    public static final int EVIL = 0xFFFF5555;
    public static final int YES = 0xFF55FF55;
    public static final int NO = 0xFFFF5555;
    public static final int BLACK = 0xFF000000;

    private static final Identifier EMPTY_ROLE_SLOT = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/icons/empty_role_slot.png");
    private static final Identifier EMPTY_REMINDER_SLOT = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/icons/empty_reminder_slot.png");

    private UiDrawing() {}

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        UiTheme.framedPanel(graphics, x, y, width, height);
    }

    public static void softPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_SOFT);
        graphics.outline(x, y, width, height, UiTheme.GOLD_SHADOW);
    }

    /** Draws the full role texture scaled to the requested square. */
    public static void roleIcon(GuiGraphicsExtractor graphics, ScriptRole role, int x, int y, int size) {
        if (role == null || role.getIcon() == null || size <= 0) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, role.getIcon(), x, y, 0, 0, size, size, size, size);
    }

    public static Identifier emptyRoleSlotTexture() {
        return EMPTY_ROLE_SLOT;
    }

    /** Round parchment token (the Clocktower look); an empty round slot when there is no role. */
    public static void roleToken(GuiGraphicsExtractor graphics, ScriptRole role, int x, int y, int size) {
        if (role == null || role.getIcon() == null) {
            UiTheme.emptyToken(graphics, x, y, size);
            return;
        }
        UiTheme.roundToken(graphics, role, x, y, size);
    }

    /**
     * Compact BOTC-style death marker over a player portrait.
     *
     * Only half of the supplied portrait is covered so the player's face stays
     * visible while the shroud remains an immediate dead-player cue.
     */
    public static void deathShroud(GuiGraphicsExtractor graphics, int x, int y, int size) {
        if (size <= 0) return;
        // Dim the portrait and hang the shroud at its top-left corner, as on tokens.
        graphics.fill(x, y, x + size, y + size, UiTheme.dark() ? 0x70120D14 : 0x50EBDFC3);
        UiTheme.shroud(graphics, x - 3, y - 3, Math.max(8, size / 2));
    }

    public static ScriptRole roleOf(PendingRoleAssignment assignment) {
        return assignment == null ? null : assignment.getScriptRole();
    }

    public static int teamColor(RoleType type) {
        if (type == null) return TEXT;
        return type.getColor();
    }

    /**
     * One line of text that always stays inside {@code maxWidth}: drawn at
     * full size when it fits, shrunk (down to 60%) when it does not, and only
     * then shortened with "...".
     */
    public static void textScaledToFit(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                                       net.minecraft.network.chat.Component text, int x, int y, int maxWidth,
                                       int colour, boolean shadow) {
        net.minecraft.util.FormattedCharSequence ordered = ordered(text);
        int natural = font.width(ordered);
        if (natural <= maxWidth) {
            graphics.text(font, ordered, x, y, colour, shadow);
            return;
        }
        float scale = Math.max(0.6F, maxWidth / (float) natural);
        int room = (int) (maxWidth / scale);
        String plain = text.getString();
        if (font.width(plain) > room) {
            plain = font.plainSubstrByWidth(plain, Math.max(0, room - font.width("\u2026"))) + "\u2026";
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + (1 - scale) * font.lineHeight / 2);
        graphics.pose().scale(scale, scale);
        graphics.text(font, ordered(plain), 0, 0, colour, shadow);
        graphics.pose().popMatrix();
    }

    public static int opaque(int colour) {
        return colour | 0xFF000000;
    }

}
