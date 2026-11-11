package com.sharktower.bloodonthesharktower.profile;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Pure validation for player nicknames. A nickname replaces the Minecraft
 * username in chat, name tags, the tab list and Sharktower screens, so the
 * rules guard against impersonation: of other players, of the Storyteller or
 * server, and of the "Seat 3" labels the game itself prints.
 *
 * Only Latin letters (accents allowed), digits, spaces and {@code _ - ' .}
 * are accepted, so look-alike letters from other alphabets cannot be used to
 * copy someone else's name.
 */
public final class Nicknames {
    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 16;

    private static final List<String> RESERVED = List.of(
            "storyteller", "st", "server", "system", "console", "admin", "administrator",
            "operator", "op", "moderator", "mod", "staff", "owner", "everyone", "here",
            "sharktower", "nobody", "unknown", "player", "seat",
            "demon", "minion", "townsfolk", "outsider", "traveller", "traveler", "fabled",
            "good", "evil", "dead", "alive", "ghost"
    );

    private Nicknames() {}

    /** Trims and collapses runs of whitespace. Case is kept. */
    public static String normalise(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    /**
     * Comparison key: accents removed, lower case, letters and digits only.
     * "Zoë", "zoe" and "Z.o.e" share a key, so none may copy another.
     */
    public static String key(String raw) {
        if (raw == null) return "";
        String text = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Rules that do not depend on other players. Null when acceptable. */
    public static String refusal(String raw) {
        String text = normalise(raw);
        if (text.isEmpty()) return "A nickname cannot be empty. Use /nick clear to go back to your username.";
        if (text.length() < MIN_LENGTH || text.length() > MAX_LENGTH) {
            return "A nickname must be " + MIN_LENGTH + " to " + MAX_LENGTH + " characters long.";
        }
        boolean letter = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                if (Character.UnicodeScript.of(c) != Character.UnicodeScript.LATIN) {
                    return "Nicknames may only use Latin letters, so they cannot imitate another name.";
                }
                letter = true;
                continue;
            }
            boolean allowed = (c >= '0' && c <= '9') || c == ' ' || c == '_' || c == '-' || c == '\'' || c == '.';
            if (!allowed) {
                return "Nicknames may only contain letters, digits, spaces and _ - ' .";
            }
        }
        if (!letter) return "A nickname needs at least one letter.";
        String key = key(text);
        if (RESERVED.contains(key)) {
            return "\"" + text + "\" looks like a game or server term and cannot be used as a nickname.";
        }
        if (key.startsWith("storyteller") || key.matches("seat\\d+.*") || key.matches(".*seat\\d+")) {
            return "Nicknames cannot look like the Storyteller or a seat label.";
        }
        return null;
    }

    /**
     * Full check: the base rules plus "not another player's username or
     * nickname". {@code otherNames} must not include the player's own names.
     */
    public static String refusal(String raw, Collection<String> otherNames) {
        String base = refusal(raw);
        if (base != null) return base;
        String key = key(normalise(raw));
        for (String other : otherNames) {
            if (other != null && key.equals(key(other))) {
                return "That name is too close to another player's name.";
            }
        }
        return null;
    }

    public static boolean isValid(String raw) {
        return refusal(raw) == null;
    }
}
