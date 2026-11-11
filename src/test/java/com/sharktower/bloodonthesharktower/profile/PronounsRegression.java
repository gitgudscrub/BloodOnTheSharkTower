package com.sharktower.bloodonthesharktower.profile;

/**
 * Plain Java regression executable: pronoun text validation and normalisation.
 * Pronouns are public cosmetic text, so the rules only stop abuse of the slot.
 */
public final class PronounsRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (String preset : Pronouns.PRESETS) {
            check(Pronouns.isValid(preset), "preset accepted: " + preset);
            check(preset.equals(Pronouns.normalise(preset)), "preset already normalised: " + preset);
        }

        check("he/him".equals(Pronouns.normalise("  He / Him ")), "case and spacing normalised");
        check("they/them".equals(Pronouns.normalise("THEY/THEM")), "upper case lowered");
        check("xe/xem".equals(Pronouns.normalise("xe/xem")), "custom neopronouns kept");
        check(Pronouns.isValid("xe/xem"), "custom neopronouns accepted");
        check(Pronouns.isValid("fae/faer"), "custom pronouns accepted");
        check(Pronouns.isValid("he/him or they/them"), "spaces between forms accepted");
        check("".equals(Pronouns.normalise(null)), "null normalises to empty");

        check(Pronouns.refusal("") != null, "empty refused");
        check(Pronouns.refusal("   ") != null, "blank refused");
        check(Pronouns.refusal("a".repeat(Pronouns.MAX_LENGTH + 1)) != null, "over-long refused");
        check(Pronouns.isValid("a".repeat(Pronouns.MAX_LENGTH)), "max length accepted");
        check(Pronouns.refusal("§cred") != null, "section-sign formatting refused");
        check(Pronouns.refusal("they/them\n") == null, "trailing newline is trimmed");
        check(Pronouns.refusal("they\u0007them") != null, "control character refused");
        check(Pronouns.refusal("they/them!") != null, "punctuation refused");
        check(Pronouns.refusal("he/him 2") != null, "digits refused");
        check(Pronouns.refusal("/them") != null, "leading slash refused");
        check(Pronouns.refusal("they/") != null, "trailing slash refused");
        check(Pronouns.refusal("they//them") != null, "double slash refused");

        check(Pronouns.refusal("Storyteller") != null, "Storyteller refused");
        check(Pronouns.refusal("the/demon") != null, "demon refused");
        check(Pronouns.refusal("good/evil") != null, "alignment words refused");
        check(Pronouns.refusal("dead") != null, "dead refused");
        check(Pronouns.refusal("server") != null, "server refused");
        check(Pronouns.isValid("goodness/gracious"), "reserved words only match whole forms");

        System.out.println("PronounsRegression passed (" + checks + " checks).");
    }
}
