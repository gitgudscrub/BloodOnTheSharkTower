package com.sharktower.bloodonthesharktower.voicechat;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.setup.MapConfigurationStore;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Automatic daytime voice routing using entrance/exit trigger points.
 *
 * During DAY all seated players and active Storytellers share one hidden Day Chat
 * group. Before nominations open, seated non-Storyteller players are moved into an
 * isolated private group when they pass close to one of that area's entrance points.
 * They remain in that private group regardless of where they move inside the area,
 * and only return to Day Chat when they pass an exit point (or nominations open).
 *
 * This deliberately avoids trying to model the physical room/area itself, so private
 * spaces can be any shape. For a doorway that is used in both directions, place the
 * entrance marker just inside the doorway and the exit marker just outside it.
 */
public final class DayChatZoneManager {
    private static final UUID SHARED_DAY_GROUP_ID = UUID.nameUUIDFromBytes(
            "blood-on-the-sharktower:day:shared".getBytes(StandardCharsets.UTF_8));

    // A trigger is intentionally forgiving: players only need to walk through the
    // doorway, not stand on an exact block. Horizontal distance is checked separately
    // from height so steps/slabs at an entrance do not make the trigger unreliable.
    private static final double TRIGGER_RADIUS = 1.50D;
    private static final double TRIGGER_RADIUS_SQUARED = TRIGGER_RADIUS * TRIGGER_RADIUS;
    private static final double TRIGGER_VERTICAL_TOLERANCE = 1.50D;

    // Day Chat routing now scans every server tick so sprinting players cannot
    // skip across a doorway between checks. Keep a short grace period on both
    // sides of the doorway so nearby entrance/exit markers cannot immediately
    // bounce a player back to the route they just left.
    private static final int EXIT_COOLDOWN_TICKS = 15;
    private static final int ENTRY_EXIT_GRACE_TICKS = 10;

    private static final Map<String, ChatZone> ZONES = new TreeMap<>();
    private static final Set<UUID> DAY_ROUTED = new HashSet<>();
    private static final Map<UUID, String> PLAYER_ZONE = new HashMap<>();
    private static final Map<UUID, Integer> EXIT_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Integer> ENTRY_EXIT_GRACE = new HashMap<>();
    private static int lastObservedDay = -1;
    private static boolean privateWindowClosedForDay;

    private DayChatZoneManager() {}

    /** Serializable view of a configured entrance/exit marker. */
    public record TriggerPosition(double x, double y, double z) {}

    /** Serializable view of one daytime private-chat area. */
    public record PersistentZone(List<TriggerPosition> entrances, List<TriggerPosition> exits) {}

    /** Copy used by the persistent map-configuration store. */
    public static synchronized Map<String, PersistentZone> snapshotPersistentZones() {
        Map<String, PersistentZone> copy = new TreeMap<>();
        for (Map.Entry<String, ChatZone> entry : ZONES.entrySet()) {
            List<TriggerPosition> entrances = new ArrayList<>();
            for (TriggerPoint point : entry.getValue().entrances) {
                entrances.add(new TriggerPosition(point.x(), point.y(), point.z()));
            }
            List<TriggerPosition> exits = new ArrayList<>();
            for (TriggerPoint point : entry.getValue().exits) {
                exits.add(new TriggerPosition(point.x(), point.y(), point.z()));
            }
            copy.put(entry.getKey(), new PersistentZone(List.copyOf(entrances), List.copyOf(exits)));
        }
        return copy;
    }

    /** Restore configured markers on startup without touching active voice routing. */
    public static synchronized void restorePersistentZones(Map<String, PersistentZone> zones) {
        ZONES.clear();
        PLAYER_ZONE.clear();
        EXIT_COOLDOWN.clear();
        ENTRY_EXIT_GRACE.clear();
        DAY_ROUTED.clear();
        if (zones == null) return;
        for (Map.Entry<String, PersistentZone> entry : zones.entrySet()) {
            String name = normalizeName(entry.getKey());
            PersistentZone saved = entry.getValue();
            if (name == null || saved == null) continue;
            ChatZone zone = new ChatZone();
            if (saved.entrances() != null) {
                for (TriggerPosition point : saved.entrances()) {
                    if (point != null) zone.entrances.add(new TriggerPoint(point.x(), point.y(), point.z()));
                }
            }
            if (saved.exits() != null) {
                for (TriggerPosition point : saved.exits()) {
                    if (point != null) zone.exits.add(new TriggerPoint(point.x(), point.y(), point.z()));
                }
            }
            if (!zone.entrances.isEmpty() || !zone.exits.isEmpty()) ZONES.put(name, zone);
        }
    }

    /** Called from NightChatManager's existing throttled server tick. */
    public static synchronized void serverTick(MinecraftServer server) {
        if (server == null) return;

        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api == null) return;

        tickCooldowns();

        if (NightChatManager.isActive()) {
            // NightChatManager has already reassigned participants to the shared
            // night group. Only forget daytime bookkeeping; never null their group.
            forgetDayRouting(api);
            return;
        }

        if (!isDayPhase() || ServerState.gameEnded) {
            releaseDayRouting(api);
            return;
        }

        if (ServerState.currentDay != lastObservedDay) {
            lastObservedDay = ServerState.currentDay;
            privateWindowClosedForDay = false;
            EXIT_COOLDOWN.clear();
            ENTRY_EXIT_GRACE.clear();
        }
        if (DaytimeState.areNominationsOpen()) privateWindowClosedForDay = true;

        boolean privateChatsEnabled = !privateWindowClosedForDay;
        Set<UUID> participants = dayParticipants();

        // Anyone no longer participating, or currently in a manual Storyteller
        // private session, must not be managed by the automatic day router.
        for (UUID id : new HashSet<>(DAY_ROUTED)) {
            if (participants.contains(id) && NightChatManager.privatePartner(id) == null) continue;
            DAY_ROUTED.remove(id);
            PLAYER_ZONE.remove(id);
            EXIT_COOLDOWN.remove(id);
            ENTRY_EXIT_GRACE.remove(id);
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (!participants.contains(id)) continue;
            if (NightChatManager.privatePartner(id) != null) continue;

            // Storytellers always remain in public Day Chat. Physical private-chat
            // gates are a player-to-player whisper mechanic, not ST eavesdropping.
            if (!privateChatsEnabled || StorytellerState.isStoryteller(id)) {
                assignSharedDay(api, id);
                continue;
            }

            String currentZone = PLAYER_ZONE.get(id);
            if (currentZone != null) {
                ChatZone zone = ZONES.get(currentZone);

                // If a configured area has been removed or is no longer usable,
                // safely return the player to the shared Day Chat.
                if (zone == null || !zone.ready()) {
                    assignSharedDay(api, id);
                    continue;
                }

                // While inside a private chat, position does not matter. The player
                // stays private until they physically pass an exit trigger. A brief
                // post-entry grace prevents an entrance/exit pair on opposite sides
                // of one doorway from firing back-to-back while the player clears it.
                if (ENTRY_EXIT_GRACE.getOrDefault(id, 0) > 0) {
                    assignPrivateZone(api, id, currentZone);
                } else if (zone.nearAnyExit(player)) {
                    assignSharedDay(api, id);
                    EXIT_COOLDOWN.put(id, EXIT_COOLDOWN_TICKS);
                    ENTRY_EXIT_GRACE.remove(id);
                } else {
                    assignPrivateZone(api, id, currentZone);
                }
                continue;
            }

            // A player who just left a room is briefly prevented from immediately
            // retriggering an entrance placed close to the same doorway.
            if (EXIT_COOLDOWN.getOrDefault(id, 0) > 0) {
                assignSharedDay(api, id);
                continue;
            }

            String entering = findEntrance(player);
            if (entering == null) {
                assignSharedDay(api, id);
            } else {
                assignPrivateZone(api, id, entering);
                ENTRY_EXIT_GRACE.put(id, ENTRY_EXIT_GRACE_TICKS);
            }
        }

        // Prune stale online bookkeeping for players who left Minecraft.
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) online.add(player.getUUID());
        DAY_ROUTED.removeIf(id -> !online.contains(id));
        PLAYER_ZONE.keySet().removeIf(id -> !online.contains(id));
        EXIT_COOLDOWN.keySet().removeIf(id -> !online.contains(id));
        ENTRY_EXIT_GRACE.keySet().removeIf(id -> !online.contains(id));
    }

    public static synchronized String statusLine() {
        long readyZones = ZONES.values().stream().filter(ChatZone::ready).count();
        return "Day Chat: " + (isDayPhase() ? "ACTIVE" : "INACTIVE")
                + ", nominations=" + (DaytimeState.areNominationsOpen() ? "OPEN" : "CLOSED")
                + ", privateWindow=" + (privateWindowClosedForDay ? "CLOSED FOR DAY" : "OPEN")
                + ", privateChats=" + readyZones + "/" + ZONES.size()
                + ", routed=" + DAY_ROUTED.size()
                + ", currentlyPrivate=" + PLAYER_ZONE.size();
    }

    public static synchronized int configuredZoneCount() {
        return (int) ZONES.values().stream().filter(ChatZone::ready).count();
    }

    /**
     * Local HUD route for daytime automatic voice routing. The existing
     * VoiceRouteS2CPayload is deliberately reused so no extra networking packet
     * is needed just for the room label.
     */
    public static synchronized String routeCode(UUID playerId) {
        if (playerId == null) return null;
        String zone = PLAYER_ZONE.get(playerId);
        if (zone != null) return "DAY_ZONE:" + zone;
        return DAY_ROUTED.contains(playerId) ? "DAY_SHARED" : null;
    }

    public static synchronized String addEntrance(String rawName, ServerPlayer player) {
        return addTrigger(rawName, player, true);
    }

    public static synchronized String addExit(String rawName, ServerPlayer player) {
        return addTrigger(rawName, player, false);
    }

    private static String addTrigger(String rawName, ServerPlayer player, boolean entrance) {
        if (player == null) return "A player position is required.";
        String name = normalizeName(rawName);
        if (name == null) return "Private chat names may contain letters, numbers, _ and -.";

        ChatZone zone = ZONES.computeIfAbsent(name, ignored -> new ChatZone());
        TriggerPoint point = TriggerPoint.from(player);
        List<TriggerPoint> list = entrance ? zone.entrances : zone.exits;

        // Avoid accidental duplicate markers if the command is run twice while
        // standing in the same doorway.
        for (TriggerPoint existing : list) {
            if (existing.distanceSquared(point) < 0.25D) {
                return (entrance ? "Entrance" : "Exit") + " for private chat '" + name
                        + "' is already marked here.";
            }
        }

        list.add(point);
        MapConfigurationStore.saveQuietly();
        return "Added " + (entrance ? "entrance" : "exit") + " " + list.size()
                + " for private chat '" + name + "' at " + point.describe()
                + (zone.ready() ? ". Private chat is ready." : ". Add at least one "
                + (entrance ? "exit" : "entrance") + " to activate it.");
    }

    public static synchronized String clearEntrances(String rawName) {
        return clearTriggers(rawName, true);
    }

    public static synchronized String clearExits(String rawName) {
        return clearTriggers(rawName, false);
    }

    private static String clearTriggers(String rawName, boolean entrances) {
        String name = normalizeName(rawName);
        ChatZone zone = name == null ? null : ZONES.get(name);
        if (zone == null) return "No private chat named '" + rawName + "' exists.";
        List<TriggerPoint> list = entrances ? zone.entrances : zone.exits;
        int count = list.size();
        list.clear();
        MapConfigurationStore.saveQuietly();
        return "Cleared " + count + " " + (entrances ? "entrance" : "exit")
                + (count == 1 ? "" : "s") + " for private chat '" + name + "'.";
    }

    public static synchronized String removeZone(String rawName) {
        String name = normalizeName(rawName);
        if (name == null || ZONES.remove(name) == null) {
            return "No private chat named '" + rawName + "' exists.";
        }
        PLAYER_ZONE.entrySet().removeIf(entry -> name.equals(entry.getValue()));
        ENTRY_EXIT_GRACE.keySet().removeIf(id -> !PLAYER_ZONE.containsKey(id));
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api != null) removeGroup(api, zoneGroupId(name));
        MapConfigurationStore.saveQuietly();
        return "Removed daytime private chat '" + name + "'.";
    }

    public static synchronized String clearZones() {
        int count = ZONES.size();
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api != null) {
            for (String name : ZONES.keySet()) removeGroup(api, zoneGroupId(name));
        }
        ZONES.clear();
        PLAYER_ZONE.clear();
        EXIT_COOLDOWN.clear();
        ENTRY_EXIT_GRACE.clear();
        MapConfigurationStore.saveQuietly();
        return "Cleared " + count + " daytime private chat" + (count == 1 ? "" : "s") + ".";
    }

    public static synchronized List<String> zoneLines() {
        List<String> lines = new ArrayList<>();
        if (ZONES.isEmpty()) {
            lines.add("No daytime private chats are configured.");
            return lines;
        }
        for (Map.Entry<String, ChatZone> entry : ZONES.entrySet()) {
            ChatZone zone = entry.getValue();
            lines.add(entry.getKey() + ": " + zone.entrances.size() + " entrance"
                    + (zone.entrances.size() == 1 ? "" : "s") + ", "
                    + zone.exits.size() + " exit" + (zone.exits.size() == 1 ? "" : "s")
                    + (zone.ready() ? " [READY]" : " [NEEDS BOTH]"));
            for (int i = 0; i < zone.entrances.size(); i++) {
                lines.add("  entrance " + (i + 1) + " " + zone.entrances.get(i).describe());
            }
            for (int i = 0; i < zone.exits.size(); i++) {
                lines.add("  exit " + (i + 1) + " " + zone.exits.get(i).describe());
            }
        }
        return lines;
    }

    private static void assignSharedDay(VoicechatServerApi api, UUID playerId) {
        try {
            VoicechatConnection connection = api.getConnectionOf(playerId);
            if (connection == null || !connection.isConnected()) return;
            String oldZone = PLAYER_ZONE.remove(playerId);
            connection.setGroup(ensureSharedDayGroup(api));
            DAY_ROUTED.add(playerId);
            if (oldZone != null && !PLAYER_ZONE.containsValue(oldZone)) removeGroup(api, zoneGroupId(oldZone));
        } catch (Throwable t) {
            BloodOnTheSharktower.LOGGER.error("Failed to route {} into shared Day Chat.", playerId, t);
            DAY_ROUTED.remove(playerId);
            PLAYER_ZONE.remove(playerId);
        }
    }

    private static void assignPrivateZone(VoicechatServerApi api, UUID playerId, String zoneName) {
        try {
            VoicechatConnection connection = api.getConnectionOf(playerId);
            if (connection == null || !connection.isConnected()) return;
            String oldZone = PLAYER_ZONE.put(playerId, zoneName);
            connection.setGroup(ensureZoneGroup(api, zoneName));
            DAY_ROUTED.add(playerId);
            if (oldZone != null && !oldZone.equals(zoneName) && !PLAYER_ZONE.containsValue(oldZone)) {
                removeGroup(api, zoneGroupId(oldZone));
            }
        } catch (Throwable t) {
            BloodOnTheSharktower.LOGGER.error(
                    "Failed to route {} into daytime private chat '{}'.", playerId, zoneName, t);
            DAY_ROUTED.remove(playerId);
            PLAYER_ZONE.remove(playerId);
        }
    }

    private static void releaseDayRouting(VoicechatServerApi api) {
        for (UUID id : new HashSet<>(DAY_ROUTED)) {
            // Manual Storyteller private rooms own their own routing.
            if (NightChatManager.privatePartner(id) != null) continue;
            try {
                VoicechatConnection connection = api.getConnectionOf(id);
                if (connection != null) connection.setGroup(null);
            } catch (Throwable ignored) {
            }
        }
        forgetDayRouting(api);
    }

    private static void forgetDayRouting(VoicechatServerApi api) {
        DAY_ROUTED.clear();
        PLAYER_ZONE.clear();
        EXIT_COOLDOWN.clear();
        ENTRY_EXIT_GRACE.clear();
        removeGroup(api, SHARED_DAY_GROUP_ID);
        for (String name : ZONES.keySet()) removeGroup(api, zoneGroupId(name));
    }

    private static Group ensureSharedDayGroup(VoicechatServerApi api) {
        Group existing = findRegisteredGroup(api, SHARED_DAY_GROUP_ID);
        if (existing != null) return existing;
        return api.groupBuilder()
                .setId(SHARED_DAY_GROUP_ID)
                .setName("BOTS Day Chat")
                .setPersistent(true)
                .setHidden(true)
                .setType(Group.Type.ISOLATED)
                .build();
    }

    private static Group ensureZoneGroup(VoicechatServerApi api, String zoneName) {
        UUID id = zoneGroupId(zoneName);
        Group existing = findRegisteredGroup(api, id);
        if (existing != null) return existing;
        return api.groupBuilder()
                .setId(id)
                .setName("BOTS Private - " + zoneName)
                .setPersistent(true)
                .setHidden(true)
                .setType(Group.Type.ISOLATED)
                .build();
    }

    private static Group findRegisteredGroup(VoicechatServerApi api, UUID id) {
        try {
            for (Group group : api.getGroups()) {
                if (group != null && id.equals(group.getId())) return group;
            }
        } catch (Throwable t) {
            BloodOnTheSharktower.LOGGER.warn("Could not enumerate Simple Voice Chat groups.", t);
        }
        return null;
    }

    private static void removeGroup(VoicechatServerApi api, UUID id) {
        try {
            api.removeGroup(id);
        } catch (Throwable ignored) {
        }
    }

    private static UUID zoneGroupId(String zoneName) {
        return UUID.nameUUIDFromBytes(("blood-on-the-sharktower:day:zone:" + zoneName)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String findEntrance(ServerPlayer player) {
        for (Map.Entry<String, ChatZone> entry : ZONES.entrySet()) {
            ChatZone zone = entry.getValue();
            if (zone.ready() && zone.nearAnyEntrance(player)) return entry.getKey();
        }
        return null;
    }

    private static void tickCooldowns() {
        tickCooldownMap(EXIT_COOLDOWN);
        tickCooldownMap(ENTRY_EXIT_GRACE);
    }

    private static void tickCooldownMap(Map<UUID, Integer> cooldowns) {
        if (cooldowns.isEmpty()) return;
        for (UUID id : new HashSet<>(cooldowns.keySet())) {
            int next = cooldowns.getOrDefault(id, 0) - 1;
            if (next <= 0) cooldowns.remove(id);
            else cooldowns.put(id, next);
        }
    }

    private static Set<UUID> dayParticipants() {
        Set<UUID> participants = new HashSet<>(ServerState.PLAYER_SEAT_NUMBERS.keySet());
        participants.removeIf(StorytellerState::isStoryteller);
        participants.addAll(StorytellerState.STORYTELLERS);
        return participants;
    }

    private static boolean isDayPhase() {
        return ServerState.currentDay > 0 && ServerState.currentNight == ServerState.currentDay;
    }

    private static String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return value.matches("[a-z0-9_-]{1,32}") ? value : null;
    }

    private static final class ChatZone {
        private final List<TriggerPoint> entrances = new ArrayList<>();
        private final List<TriggerPoint> exits = new ArrayList<>();

        private boolean ready() {
            return !entrances.isEmpty() && !exits.isEmpty();
        }

        private boolean nearAnyEntrance(ServerPlayer player) {
            return nearAny(player, entrances);
        }

        private boolean nearAnyExit(ServerPlayer player) {
            return nearAny(player, exits);
        }

        private static boolean nearAny(ServerPlayer player, List<TriggerPoint> points) {
            for (TriggerPoint point : points) {
                if (point.matches(player)) return true;
            }
            return false;
        }
    }

    private record TriggerPoint(double x, double y, double z) {
        static TriggerPoint from(ServerPlayer player) {
            return new TriggerPoint(player.getX(), player.getY(), player.getZ());
        }

        boolean matches(ServerPlayer player) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            double dy = Math.abs(player.getY() - y);
            return (dx * dx + dz * dz) <= TRIGGER_RADIUS_SQUARED
                    && dy <= TRIGGER_VERTICAL_TOLERANCE;
        }

        double distanceSquared(TriggerPoint other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }

        String describe() {
            return String.format(java.util.Locale.ROOT, "(%.1f, %.1f, %.1f)", x, y, z);
        }
    }
}
