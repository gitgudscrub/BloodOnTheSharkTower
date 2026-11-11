package com.sharktower.bloodonthesharktower.profile;

import java.util.List;

/**
 * Plain Java regression executable for {@link Nicknames}: what a player may
 * call themselves, and the impersonation rules (other players, the
 * Storyteller, the server and the game's own "Seat 3" labels).
 */
public final class NicknamesRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void ok(String raw) {
        check(Nicknames.refusal(raw) == null, "should accept: " + raw + " (got " + Nicknames.refusal(raw) + ")");
    }

    private static void refused(String raw) {
        check(Nicknames.refusal(raw) != null, "should refuse: " + raw);
    }

    public static void main(String[] args) {
        // Ordinary names, including accents, digits and simple punctuation.
        ok("Ryan");
        ok("Zoë");
        ok("Mary-Jane");
        ok("O'Neill");
        ok("J.R. Smith");
        ok("Player_42");
        ok("Al");
        check(Nicknames.normalise("  Big   Al  ").equals("Big Al"), "whitespace is collapsed");

        // Length and content.
        refused("");
        refused("   ");
        refused("A");
        refused("ThisNameIsFarTooLongToFit");
        refused("1234");
        refused("--");
        refused("§cRed");
        refused("Na\u0000me");
        refused("Na\u0007me");
        refused("Bob!");
        refused("<Bob>");
        refused("Ryan​");
        refused("Аlice"); // Cyrillic А

        // Game and server terms.
        refused("Storyteller");
        refused("story teller");
        refused("Storyteller2");
        refused("ST");
        refused("Server");
        refused("Admin");
        refused("Demon");
        refused("Seat 3");
        refused("seat12");
        refused("Bob Seat 4");

        // Other players' names, by comparison key.
        check(Nicknames.key("Zoë").equals("zoe"), "accents are folded in the key");
        check(Nicknames.key("Z.o.e").equals("zoe"), "punctuation is ignored in the key");
        List<String> others = List.of("Zoe", "gitgud", "Big Al");
        check(Nicknames.refusal("zoë", others) != null, "accented copy of another player is refused");
        check(Nicknames.refusal("Git.Gud", others) != null, "punctuated copy of another player is refused");
        check(Nicknames.refusal("BigAl", others) != null, "spacing copy of another player is refused");
        check(Nicknames.refusal("Zed", others) == null, "a distinct name is accepted");
        check(Nicknames.refusal("Storyteller", others) != null, "base rules still apply with others");

        System.out.println("PASS: " + checks + " nickname validation checks");
    }
}
