package com.sharktower.bloodonthesharktower.client.gui;

import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.core.Script;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Colours character names in Resolve panel text by team, so "the Imp" or
 * "the real Slayer" stands out. Only capitalised names count ("Drunk" is the
 * character, "drunk or poisoned" is a state), matched as whole words.
 */
final class ResolveText {
    private ResolveText() {}

    static Component colourRoles(String text, Script script) {
        MutableComponent out = Component.empty();
        if (text == null || text.isEmpty()) return out;
        Map<String, RoleType> names = names(script);
        int plainStart = 0;
        int i = 0;
        while (i < text.length()) {
            boolean wordStart = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
            String match = null;
            if (wordStart && Character.isUpperCase(text.charAt(i))) {
                for (String name : names.keySet()) {
                    int end = i + name.length();
                    if (end > text.length() || (match != null && name.length() <= match.length())) continue;
                    String found = text.substring(i, end);
                    boolean capitalised = found.equals(name) || found.equals(name.toUpperCase(Locale.ROOT));
                    boolean wordEnd = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
                    if (capitalised && wordEnd) match = name;
                }
            }
            if (match == null) {
                i++;
                continue;
            }
            if (plainStart < i) out.append(Component.literal(text.substring(plainStart, i)));
            int end = i + match.length();
            int colour = ink(names.get(match)) & 0xFFFFFF;
            out.append(Component.literal(text.substring(i, end))
                    .withStyle(style -> style.withColor(TextColor.fromRgb(colour))));
            i = end;
            plainStart = end;
        }
        if (plainStart < text.length()) out.append(Component.literal(text.substring(plainStart)));
        return out;
    }

    /** Team colour, darkened on the light Parchment panels so it still reads. */
    static int ink(RoleType type) {
        int colour = type == null ? UiDrawing.TEXT : type.getColor();
        if (UiTheme.dark() || type == RoleType.DEMON) return colour;
        int r = (int) (((colour >> 16) & 0xFF) * 0.7);
        int g = (int) (((colour >> 8) & 0xFF) * 0.7);
        int b = (int) ((colour & 0xFF) * 0.7);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static Map<String, RoleType> names(Script script) {
        Map<String, RoleType> names = new HashMap<>();
        for (Role role : Role.values()) put(names, role.getDisplayName(), role.getType());
        if (script != null) {
            for (ScriptRole role : script.allRoles()) put(names, role.getDisplayName(), role.getTeam());
        }
        return names;
    }

    private static void put(Map<String, RoleType> names, String raw, RoleType type) {
        if (raw == null || raw.length() < 3 || type == null || type == RoleType.NONE) return;
        names.put(pretty(raw), type);
    }

    /** "IMP" -> "Imp", "FORTUNE TELLER" -> "Fortune Teller"; mixed case is kept. */
    static String pretty(String name) {
        if (!name.equals(name.toUpperCase(Locale.ROOT))) return name;
        StringBuilder out = new StringBuilder(name.length());
        boolean start = true;
        for (char ch : name.toCharArray()) {
            out.append(start ? ch : Character.toLowerCase(ch));
            start = !Character.isLetter(ch) && ch != '\'';
        }
        return out.toString();
    }
}
