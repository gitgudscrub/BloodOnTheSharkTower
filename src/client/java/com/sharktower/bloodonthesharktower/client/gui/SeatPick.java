package com.sharktower.bloodonthesharktower.client.gui;

import net.minecraft.client.Minecraft;

/**
 * "Click a seat to choose" in the Grimoire. Two sources:
 * <ul>
 *   <li>a player's night choice sent by the server (Poisoner, Monk, Imp,
 *   Ravenkeeper); it stays open so they can change their mind until the
 *   Storyteller acts, and the server closes it;</li>
 *   <li>the Storyteller's <b>Choose seat</b> button in Resolve, which picks
 *   one seat for a player who is offline or told them out loud.</li>
 * </ul>
 * While a pick is open, clicking a seat's head or role token runs the
 * command with that seat number instead of opening its usual screen.
 */
public final class SeatPick {
    /** Only these commands can be run by a click, whatever the server sends. */
    private static final String[] ALLOWED = {"/poison ", "/protect ", "/attack ", "/raven ", "/bots automation "};

    private static String title = "";
    private static String command = "";
    private static int excludeSeat;
    private static boolean storyteller;
    private static int chosenSeat;
    private static int revision;
    // The Storyteller's local pick (Drunk / Marionette in setup): runs code instead of a command.
    private static java.util.function.IntConsumer localAction;
    private static String localDetail = "";

    // The player's own night choice, kept while the Storyteller's one-off pick is open.
    private static String nightTitle = "";
    private static String nightCommand = "";
    private static int nightExclude;

    private SeatPick() {}

    /** From the server: open (or, with an empty command, close) the player's night choice. */
    public static void setNightChoice(String newTitle, String newCommand, int exclude) {
        nightTitle = newTitle == null ? "" : newTitle;
        nightCommand = allowed(newCommand) ? newCommand : "";
        nightExclude = exclude;
        if (!storyteller) restoreNight();
    }

    /** From a Resolve button: pick one seat for the player. */
    public static void startStoryteller(String newTitle, String newCommand) {
        if (!allowed(newCommand)) return;
        title = newTitle + ": click a seat to choose for them";
        command = newCommand;
        excludeSeat = 0;
        storyteller = true;
        chosenSeat = 0;
        revision++;
    }

    /** A Storyteller pick handled on this client, such as choosing the Drunk after the deal. */
    public static void startStorytellerLocal(String banner, String detail, java.util.function.IntConsumer action) {
        title = banner;
        command = "local";
        excludeSeat = 0;
        storyteller = true;
        chosenSeat = 0;
        localAction = action;
        localDetail = detail;
        revision++;
    }

    public static void cancel() {
        if (storyteller) restoreNight();
    }

    public static void reset() {
        nightTitle = "";
        nightCommand = "";
        nightExclude = 0;
        restoreNight();
    }

    private static void restoreNight() {
        title = nightTitle;
        command = nightCommand;
        excludeSeat = nightExclude;
        storyteller = false;
        chosenSeat = 0;
        localAction = null;
        revision++;
    }

    private static boolean allowed(String text) {
        if (text == null || text.isEmpty()) return false;
        for (String prefix : ALLOWED) {
            if (text.startsWith(prefix)) return true;
        }
        return false;
    }

    public static boolean active() {
        return !command.isEmpty();
    }

    public static boolean forStoryteller() {
        return storyteller;
    }

    /** Changes whenever the pick opens, closes or a seat is chosen, so the Grimoire can redraw its banner. */
    public static int revision() {
        return revision;
    }

    /** The banner's first line: what to click. */
    public static String title() {
        return active() ? title : "";
    }

    /** The banner's second line: the current choice, or how to back out. */
    public static String detail() {
        if (!active()) return "";
        if (storyteller && localAction != null) return localDetail;
        if (storyteller) return "Their choice goes to Resolve for you to confirm.";
        return chosenSeat > 0 ? "You chose seat " + chosenSeat + ". Click another player to change."
                : "Or click a name in chat.";
    }

    /** A seat was clicked. Returns true when the click was used for the pick. */
    public static boolean tryPick(int seat) {
        if (!active()) return false;
        if (seat == excludeSeat) return true; // swallow it: the Monk cannot pick themselves
        if (localAction != null) {
            java.util.function.IntConsumer action = localAction;
            restoreNight();
            action.accept(seat);
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        String cmd = command + seat;
        minecraft.player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        if (storyteller) {
            restoreNight();
        } else {
            chosenSeat = seat;
            revision++;
        }
        return true;
    }

    public static boolean excluded(int seat) {
        return active() && seat == excludeSeat;
    }
}
