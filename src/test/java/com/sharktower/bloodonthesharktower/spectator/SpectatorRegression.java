package com.sharktower.bloodonthesharktower.spectator;

import com.google.gson.JsonObject;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager.Position;

import java.util.List;
import java.util.Map;

/** Plain Java regression executable: website voice room names and the Watch live map. */
public final class SpectatorRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void same(String actual, String expected) {
        check(expected.equals(actual), "expected '" + expected + "' but got '" + actual + "'");
    }

    private static void map(String ada) {
        String bram = "22222222-2222-2222-2222-222222222222";
        // Half-block rounding keeps a still player's snapshot unchanged.
        check(SpectatorMap.round(10.24) == 10.0 && SpectatorMap.round(10.26) == 10.5 && SpectatorMap.round(-3.74) == -3.5,
                "positions round to half a block");
        JsonObject m = SpectatorMap.build(Map.of(2, new Position(5, 64, 9), 1, new Position(-4.3, 64, 0.2)),
                Map.of(1, new Position(0, 64, 3)), new Position(0.1, 64, 0.9),
                List.of(new SpectatorMap.Dot(ada, 1.2, 2.7)), null);
        same(m.get("homes").toString(), "[{\"x\":-4.5,\"z\":0.0,\"seat\":1},{\"x\":5.0,\"z\":9.0,\"seat\":2}]");
        same(m.get("center").toString(), "{\"x\":0.0,\"z\":1.0}");
        same(m.get("people").toString(), "[{\"x\":1.0,\"z\":2.5,\"id\":\"" + ada + "\"}]");
        check(!m.has("clock"), "no clock without a nomination");
        check(!SpectatorMap.build(Map.of(), Map.of(), null, List.of(), null).has("center"), "no centre when none is set");
        JsonObject nominated = SpectatorMap.build(Map.of(), Map.of(), null, List.of(),
                new SpectatorMap.Clock(false, false, bram, ada, null, 0, 0)).getAsJsonObject("clock");
        same(nominated.toString(), "{\"active\":false,\"nominee\":\"" + bram + "\",\"nominator\":\"" + ada + "\"}");
        JsonObject voting = SpectatorMap.build(Map.of(), Map.of(), null, List.of(),
                new SpectatorMap.Clock(true, true, bram, ada, ada, 2, 7)).getAsJsonObject("clock");
        check(voting.get("voter").getAsString().equals(ada) && voting.get("index").getAsInt() == 2
                && voting.get("total").getAsInt() == 7 && voting.get("exile").getAsBoolean(), "the hand shows whose turn it is");
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
        // Voice batch timer only runs while someone listens.
        check(!SpectatorBridge.audioTimerRunning(), "voice timer should be idle at start");
        SpectatorBridge.setAudioWanted(true);
        check(SpectatorBridge.audioTimerRunning(), "voice timer should start when a listener joins");
        SpectatorBridge.setAudioWanted(true);
        check(SpectatorBridge.audioTimerRunning(), "a second 'wanted' keeps one timer running");
        SpectatorBridge.setAudioWanted(false);
        check(!SpectatorBridge.audioTimerRunning(), "voice timer should stop when nobody listens");
        SpectatorBridge.setAudioWanted(true);
        check(SpectatorBridge.audioTimerRunning(), "voice timer should restart for a new listener");
        SpectatorBridge.setAudioWanted(false);
        map(ada);
        System.out.println("SpectatorRegression passed (" + checks + " checks).");
    }
}
