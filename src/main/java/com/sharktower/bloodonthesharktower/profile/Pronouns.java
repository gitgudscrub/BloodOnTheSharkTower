package com.sharktower.bloodonthesharktower.profile;

import java.util.List;
import java.util.Locale;

/**
 * Pure validation for player pronoun text. Pronouns are cosmetic and social
 * only; they are shown to everyone and must never carry game information, so
 * the rules here only guard against abuse of the display slot.
 */
public final class Pronouns {
    public static final int MAX_LENGTH = 24;

    /** Offered as command suggestions; custom text is accepted too. */
    public static final List<String> PRESETS = List.of(
            "he/him", "she/her", "they/them", "he/they", "she/they", "it/its", "any", "ask"
    );

    private static final List<String> RESERVED = List.of(
            "storyteller", "server", "admin", "operator", "op", "moderator", "mod",
            "demon", "minion", "townsfolk", "outsider", "traveller", "traveler",
            "good", "evil", "dead", "alive", "ghost"
    );

    private Pronouns() {}

    /** Collapses whitespace and lower-cases the text so "He / Him" stores as "he/him". */
    public static String normalise(String raw) {
        if (raw == null) return "";
        String text = raw.trim().replaceAll("\\s+", " ");
        text = text.replaceAll("\\s*/\\s*", "/");
        return text.toLowerCase(Locale.ROOT);
    }

    /**
     * Returns null when {@code raw} is acceptable, otherwise a short message for
     * the player explaining what to change. Validates the normalised form.
     */
    public static String refusal(String raw) {
        String text = normalise(raw);
        if (text.isEmpty()) return "Pronouns cannot be empty. Use /pronouns clear to remove them.";
        if (text.length() > MAX_LENGTH) {
            return "Pronouns must be " + MAX_LENGTH + " characters or fewer.";
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean allowed = Character.isLetter(c) || c == '/' || c == ' ' || c == '-' || c == '\'';
            if (!allowed) {
                return "Pronouns may only contain letters, spaces, slashes, hyphens and apostrophes.";
            }
        }
        if (text.startsWith("/") || text.endsWith("/") || text.contains("//")) {
            return "Use a slash between forms, for example they/them.";
        }
        for (String part : text.split("[/ ]")) {
            if (RESERVED.contains(part)) {
                return "\"" + part + "\" looks like a game or server term and cannot be used as a pronoun.";
            }
        }
        return null;
    }

    public static boolean isValid(String raw) {
        return refusal(raw) == null;
    }
}
