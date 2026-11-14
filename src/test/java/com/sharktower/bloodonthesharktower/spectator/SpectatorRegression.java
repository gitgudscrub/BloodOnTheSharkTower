package com.sharktower.bloodonthesharktower.spectator;

import java.util.Map;

/** Plain Java regression executable: website voice room names. */
public final class SpectatorRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void same(String actual, String expected) {
        check(expected.equals(actual), "expected '" + expected + "' but got '" + actual + "'");
    }

    public static void main(String[] args) {
        String ada = "11111111-1111-1111-1111-111111111111";
        Map<String, String> names = Map.of(ada, "Ada");
        Map<Integer, String> seats = Map.of(3, "Bram");
        same(SpectatorBridge.roomName("near", names, seats), "Open voice (nearby)");
        same(SpectatorBridge.roomName("town", names, seats), "Town Square");
        same(SpectatorBridge.roomName("zone:the_library", names, seats), "Private chat: the library");
        same(SpectatorBridge.roomName("house:3", names, seats), "House 3 (Bram)");
        same(SpectatorBridge.roomName("house:9", names, seats), "House 9");
        same(SpectatorBridge.roomName("st:" + ada, names, seats), "Storyteller with Ada");
        same(SpectatorBridge.roomName("st:22222222-2222-2222-2222-222222222222", names, seats), "Storyteller chat");
        same(GrimoireShare.cleanName("  Ada§cRed\n "), "AdacRed");
        same(GrimoireShare.cleanName("x".repeat(40)), "x".repeat(32));
        System.out.println("SpectatorRegression passed (" + checks + " checks).");
    }
}
