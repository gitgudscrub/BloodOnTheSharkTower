package com.sharktower.bloodonthesharktower.client.gui.grimoire;

/**
 * One-frame hover hint bus for the Grimoire.
 *
 * AssignRolesScreen clears the hint immediately before widget extraction.
 * Hoverable Grim widgets may then publish the hint that belongs to the item
 * currently under the mouse. The screen draws only the final hint, so the
 * bottom edge stays clean when nothing is hovered.
 */
public final class GrimoireHoverHints {
    private static String hint = "";

    private GrimoireHoverHints() {}

    public static void clear() {
        hint = "";
    }

    public static void set(String text) {
        if (text != null && !text.isBlank()) hint = text;
    }

    public static String current() {
        return hint;
    }
}
