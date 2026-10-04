package com.sharktower.bloodonthesharktower.networking;
import com.sharktower.bloodonthesharktower.daytime.AttentionHands;
import com.sharktower.bloodonthesharktower.states.ServerState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public final class SocialStateManager {
    private static final Map<UUID,Long> TALKING_UNTIL = new ConcurrentHashMap<>();
    private static Map<UUID,Boolean> lastTalking = Map.of();
    private static int ticks;
    private SocialStateManager() {}
    public static void speaking(UUID id, boolean voiced) { if (voiced) TALKING_UNTIL.put(id, System.nanoTime()+350_000_000L); else TALKING_UNTIL.remove(id); }
    private static Map<UUID,Boolean> talking() {
        long now=System.nanoTime(); Map<UUID,Boolean> result=new HashMap<>();
        TALKING_UNTIL.forEach((id,until)-> { if (until>now && ServerState.PLAYER_SEAT_NUMBERS.containsKey(id)) result.put(id,true); });
        return result;
    }
    public static void send(ServerPlayer player) { ServerPlayNetworking.send(player,new SocialStateS2CPayload(AttentionHands.positions(),talking())); }
    public static void broadcast(MinecraftServer server) { for (ServerPlayer p:server.getPlayerList().getPlayers()) send(p); }
    public static void tick(MinecraftServer server) {
        if (++ticks%5!=0) return;
        var before = AttentionHands.positions();
        if (ServerState.gameEnded || ServerState.currentDay<=0 || ServerState.currentNight!=ServerState.currentDay
                || !com.sharktower.bloodonthesharktower.daytime.DaytimeState.areNominationsOpen()) AttentionHands.clear();
        AttentionHands.positions().keySet().forEach(id->{ if (server.getPlayerList().getPlayer(id)==null || !ServerState.PLAYER_SEAT_NUMBERS.containsKey(id)) AttentionHands.set(id,false); });
        var state=talking(); if (!state.equals(lastTalking) || !before.equals(AttentionHands.positions())) { lastTalking=state; broadcast(server); }
    }
    public static void clear() { AttentionHands.clear(); TALKING_UNTIL.clear(); lastTalking=Map.of(); }
}
