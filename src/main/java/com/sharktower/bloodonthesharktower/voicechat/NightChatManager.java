package com.sharktower.bloodonthesharktower.voicechat;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.networking.VoiceRouteS2CPayload;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Physical house voice rooms and manual ST conversations.
 * Houses remain private across phase changes. Outside uses normal Night
 * proximity or the existing daytime router; there is no shared Night group.
 * The legacy class/command name is retained for compatibility.
 */
public final class NightChatManager {
    private static final long INVITE_TTL_MS = 60_000L;
    private static final double HOUSE_EXIT_RADIUS = 8.0D;
    private static final int HOUSE_CHECK_INTERVAL_TICKS = 5;

    private static final Set<UUID> MANAGED_CONNECTIONS = new HashSet<>();
    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    private static final Map<UUID, PrivateSession> SESSIONS_BY_PARTICIPANT = new HashMap<>();
    private static final Map<UUID, String> LAST_SENT_ROUTES = new HashMap<>();
    private static final Map<UUID,Integer> HOUSE_ROOMS = new HashMap<>();
    private static volatile boolean active;
    private static volatile String lastRoutingError;
    private static int houseCheckTicks;
    private static MinecraftServer routingServer;

    private NightChatManager() {}

    public static boolean isActive() {
        return active;
    }

    public static synchronized int managedConnectionCount() {
        return MANAGED_CONNECTIONS.size();
    }

    public static synchronized int pendingInviteCount() {
        cleanupExpiredInvites();
        return INVITES.size();
    }

    public static synchronized int privateSessionCount() {
        return uniquePrivateSessionCount();
    }

    public static synchronized UUID privatePartner(UUID participantId) {
        PrivateSession session = SESSIONS_BY_PARTICIPANT.get(participantId);
        if (session == null) return null;
        return session.storytellerId().equals(participantId) ? session.playerId() : session.storytellerId();
    }

    public static synchronized String statusLine() {
        cleanupExpiredInvites();
        long deadSeated = ServerState.PLAYER_SEAT_NUMBERS.keySet().stream()
                .filter(id -> Boolean.TRUE.equals(com.sharktower.bloodonthesharktower.states.DeathVisibility.visible(ServerState.PLAYER_DEATH_STATUS, ServerState.rolesRevealed).get(id)))
                .count();
        return "House voice rooms: " + HOUSE_ROOMS.size()
                + ", sharedNight=REMOVED"
                + ", seated=" + ServerState.PLAYER_SEAT_NUMBERS.size()
                + " (dead=" + deadSeated + ")"
                + ", privateSessions=" + uniquePrivateSessionCount()
                + ", pendingInvites=" + INVITES.size()
                + ", managedConnections=" + MANAGED_CONNECTIONS.size();
    }

    /** Server-side Simple Voice Chat connection diagnostics for one Minecraft player. */
    public static synchronized String voiceConnectionStatus(UUID playerId) {
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api == null) return "voice API offline";
        try {
            VoicechatConnection connection = api.getConnectionOf(playerId);
            if (connection == null) return "no voice connection";
            Group group = connection.getGroup();
            return "installed=" + connection.isInstalled()
                    + ", connected=" + connection.isConnected()
                    + ", disabled=" + connection.isDisabled()
                    + ", group=" + (group == null ? "none" : group.getName());
        } catch (Throwable t) {
            return "diagnostic error=" + shortError(t);
        }
    }

    public static synchronized String lastRoutingError() {
        return lastRoutingError;
    }

    /** Reconcile BOTS voice routing with the authoritative day/night counters. */
    public static synchronized Result syncToGamePhase() {
        return isAuthoritativeNight() ? start() : stop();
    }

    /** Human-readable route for diagnostics and multiplayer smoke tests. */
    public static synchronized String participantStatus(UUID participantId) {
        return "Voice route: " + routeCode(participantId) + ".";
    }

    /** Stable route code sent only to the participant whose HUD it describes. */
    public static synchronized String routeCode(UUID participantId) {
        if (participantId == null) return "PROXIMITY";
        PrivateSession session = SESSIONS_BY_PARTICIPANT.get(participantId);
        if (session != null) {
            if (session.playerId().equals(participantId) && !isStorytellerAttached(session)) return "PRIVATE_HOLD";
            return session.storytellerId().equals(participantId) ? "PRIVATE_STORYTELLER" : "PRIVATE";
        }
        if (HOUSE_ROOMS.containsKey(participantId)) return "HOUSE:" + HOUSE_ROOMS.get(participantId);
        String dayRoute = DayChatZoneManager.routeCode(participantId);
        return dayRoute == null ? "PROXIMITY" : dayRoute;
    }

    /** Send the current local route immediately, e.g. during initial login sync. */
    public static synchronized void sendRouteTo(ServerPlayer player) {
        if (player == null) return;
        String route = routeCode(player.getUUID());
        ServerPlayNetworking.send(player, new VoiceRouteS2CPayload(route));
        LAST_SENT_ROUTES.put(player.getUUID(), route);
    }

    /** Reconcile physical routing when Storyteller control changes. */
    public static synchronized void onStorytellerStatusChanged(UUID playerId) {
        if (playerId == null) return;

        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        PrivateSession session = SESSIONS_BY_PARTICIPANT.get(playerId);
        if (session != null) {
            if (session.storytellerId().equals(playerId)) {
                if (api != null) detachStorytellerInternal(api, session);
                else SESSIONS_BY_PARTICIPANT.remove(playerId);
            } else if (StorytellerState.isStoryteller(playerId)) {
                // A seated player claiming Storyteller control cannot remain in a
                // player-side private hold/session. End their player session first.
                if (api != null) endPlayerPrivateInternal(api, session);
                else SESSIONS_BY_PARTICIPANT.remove(playerId);
            }
        }

        if (routingServer != null) routingServer.execute(() -> reconcile(routingServer));
    }

    /** Clear bookkeeping if the voice server itself shuts down. */
    public static synchronized void onVoiceServerStopped() {
        active = false;
        routingServer = null;
        HOUSE_ROOMS.clear();
        INVITES.clear();
        SESSIONS_BY_PARTICIPANT.clear();
        MANAGED_CONNECTIONS.clear();
        LAST_SENT_ROUTES.clear();
        lastRoutingError = null;
    }

    /** Legacy Night command: enable physical routing, never a shared group. */
    public static synchronized Result start() {
        active = true;
        lastRoutingError = null;
        removeLegacyNightGroup();
        return VoicechatIntegrationState.serverApi() == null
                ? Result.fail("Simple Voice Chat server API is not online.")
                : Result.ok("House voice rooms enabled; outside houses uses proximity at Night.");
    }

    /** Phase change leaves house privacy intact. */
    public static synchronized Result stop() {
        active = false;
        lastRoutingError = null;
        removeLegacyNightGroup();
        return Result.ok("House rooms stay private until players leave; outside uses the normal daytime route.");
    }

    /** End manual night sessions at Dawn and restore physical routing. */
    public static synchronized Result stopForDawn() {
        active = false;
        lastRoutingError = null;
        INVITES.clear();
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api != null) endAllPrivateSessions(api);
        else {
            MANAGED_CONNECTIONS.removeAll(SESSIONS_BY_PARTICIPANT.keySet());
            SESSIONS_BY_PARTICIPANT.clear();
        }
        removeLegacyNightGroup();
        if (routingServer != null) reconcile(routingServer);
        LAST_SENT_ROUTES.clear();
        return Result.ok("Dawn voice routing ready. House rooms remain private until players leave.");
    }

    /**
     * Tear down every BOTS voice route, including private sessions and pending
     * invitations. This is for full game/reset cleanup, not ordinary phase changes.
     */
    public static synchronized Result resetAll() {
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        active = false;
        HOUSE_ROOMS.clear();
        lastRoutingError = null;
        INVITES.clear();

        int released = 0;
        if (api != null) {
            endAllPrivateSessions(api);
            for (UUID playerId : new HashSet<>(MANAGED_CONNECTIONS)) {
                VoicechatConnection connection = api.getConnectionOf(playerId);
                if (connection != null) {
                    connection.setGroup(null);
                    released++;
                }
            }
            try {
                removeLegacyNightGroup();
            } catch (Throwable ignored) {
            }
        } else {
            SESSIONS_BY_PARTICIPANT.clear();
        }

        MANAGED_CONNECTIONS.clear();
        LAST_SENT_ROUTES.clear();
        return Result.ok("All BOTS voice routing cleared; " + released
                + " connection(s) returned to proximity chat.");
    }

    /** Re-apply physical routing without disturbing active private sessions. */
    public static synchronized Result resync() {
        if (VoicechatIntegrationState.serverApi() == null) return Result.fail("Simple Voice Chat server API is not online.");
        if (routingServer != null) reconcile(routingServer);
        return Result.ok("House voice rooms resynchronized. Shared Night Chat is removed.");
    }

    /** Restore the authoritative route whenever Simple Voice Chat reconnects. */
    public static synchronized void onVoicePlayerConnected(UUID playerId) {
        if (playerId == null) return;
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api == null) return;
        if (SESSIONS_BY_PARTICIPANT.containsKey(playerId)) {
            assignCurrentPrivateRoute(api,playerId);
        } else if (houseParticipants().contains(playerId)) {
            // Position may have changed while voice was offline. Isolate the
            // reconnect until the server thread checks its current location;
            // never briefly restore an old house or public group.
            VoicechatConnection connection=api.getConnectionOf(playerId);
            if (connection != null && connection.isConnected())
                connection.setGroup(ensurePrivateGroup(api,reconnectGroupId(playerId)));
        }
        if (routingServer != null) routingServer.execute(() -> reconcile(routingServer));
    }

    /** Recover safely if one side of a private room loses voice connectivity. */
    public static synchronized void onVoicePlayerDisconnected(UUID playerId) {
        if (playerId == null) return;
        PrivateSession session = SESSIONS_BY_PARTICIPANT.get(playerId);
        if (session == null) return;

        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (session.storytellerId().equals(playerId) && isStorytellerAttached(session)) {
            // The Storyteller dropping out must never force the player public.
            SESSIONS_BY_PARTICIPANT.remove(session.storytellerId());
            MANAGED_CONNECTIONS.remove(session.storytellerId());
            return;
        }

        // A Simple Voice Chat dropout is not the same as leaving Minecraft.
        // Preserve the player's private room/hold so a transient reconnect never
        // exposes them to public audio. If the Storyteller was attached,
        // free only the Storyteller so they can continue running the night.
        if (isStorytellerAttached(session)) {
            if (api != null) {
                detachStorytellerInternal(api, session);
            } else {
                SESSIONS_BY_PARTICIPANT.remove(session.storytellerId());
                MANAGED_CONNECTIONS.remove(session.storytellerId());
            }
        }
        MANAGED_CONNECTIONS.remove(session.playerId());
    }

    /** Storyteller creates a normal phase-independent private invitation. */
    public static synchronized InviteResult createStorytellerInvite(UUID storytellerId, UUID targetId) {
        return createStorytellerInviteInternal(storytellerId, targetId, null);
    }

    /** Storyteller arriving at a configured house creates an auto-managed invitation. */
    public static synchronized InviteResult createStorytellerHouseInvite(
            UUID storytellerId, UUID targetId, int seat
    ) {
        // Only the most recent house visit should remain actionable. Otherwise a
        // player could accept an old invite after the Storyteller has moved on.
        INVITES.entrySet().removeIf(entry -> {
            Invite invite = entry.getValue();
            return invite.type() == InviteType.STORYTELLER_TO_PLAYER
                    && invite.requesterId().equals(storytellerId)
                    && invite.houseSeat() != null;
        });
        return createStorytellerInviteInternal(storytellerId, targetId, seat);
    }

    private static InviteResult createStorytellerInviteInternal(
            UUID storytellerId, UUID targetId, Integer houseSeat
    ) {
        cleanupExpiredInvites();
        if (storytellerId == null || !StorytellerState.isStoryteller(storytellerId)) {
            return InviteResult.fail("Claim Storyteller control first.");
        }
        if (targetId == null || !ServerState.PLAYER_SEAT_NUMBERS.containsKey(targetId)) {
            return InviteResult.fail("Target is not a seated player.");
        }
        if (StorytellerState.isStoryteller(targetId)) {
            return InviteResult.fail("Target is a Storyteller.");
        }
        if (SESSIONS_BY_PARTICIPANT.containsKey(storytellerId)) {
            return InviteResult.fail("You are already in a private conversation. Leave it first.");
        }
        if (SESSIONS_BY_PARTICIPANT.containsKey(targetId)) {
            return InviteResult.fail("That player is already in a private conversation.");
        }

        UUID token = UUID.randomUUID();
        INVITES.put(token, new Invite(token, InviteType.STORYTELLER_TO_PLAYER,
                storytellerId, targetId, System.currentTimeMillis() + INVITE_TTL_MS, houseSeat));
        return InviteResult.ok(token, "Private chat invitation created for seat "
                + ServerState.PLAYER_SEAT_NUMBERS.get(targetId) + ".");
    }

    /** Player requests a private conversation; any active Storyteller may accept. */
    public static synchronized InviteResult createPlayerRequest(UUID playerId) {
        return createPlayerRequest(playerId, null);
    }

    /** Player requests a private conversation with one specific Storyteller. */
    public static synchronized InviteResult createPlayerRequest(UUID playerId, UUID targetStorytellerId) {
        cleanupExpiredInvites();
        if (playerId == null || !ServerState.PLAYER_SEAT_NUMBERS.containsKey(playerId)) {
            return InviteResult.fail("You must be a seated player to request a private Storyteller chat.");
        }
        if (StorytellerState.isStoryteller(playerId)) {
            return InviteResult.fail("Storytellers should invite a player instead.");
        }
        if (targetStorytellerId != null && !StorytellerState.isStoryteller(targetStorytellerId)) {
            return InviteResult.fail("That player is not an active Storyteller.");
        }
        if (SESSIONS_BY_PARTICIPANT.containsKey(playerId)) {
            return InviteResult.fail("You are already in a private conversation.");
        }

        // Replace the player's previous unanswered request rather than stacking them.
        INVITES.entrySet().removeIf(entry -> entry.getValue().type() == InviteType.PLAYER_TO_STORYTELLER
                && entry.getValue().requesterId().equals(playerId));

        UUID token = UUID.randomUUID();
        INVITES.put(token, new Invite(token, InviteType.PLAYER_TO_STORYTELLER,
                playerId, targetStorytellerId, System.currentTimeMillis() + INVITE_TTL_MS, null));
        return InviteResult.ok(token, "Private Storyteller request created.");
    }

    /** Accept either a Storyteller invitation or a player's Storyteller request. */
    public static synchronized Result acceptInvite(UUID token, UUID accepterId) {
        cleanupExpiredInvites();
        if (token == null) return Result.fail("Invalid private chat invitation.");

        Invite invite = INVITES.get(token);
        if (invite == null) return Result.fail("That private chat invitation has expired or was already used.");

        UUID storytellerId;
        UUID playerId;
        if (invite.type() == InviteType.STORYTELLER_TO_PLAYER) {
            if (!invite.targetId().equals(accepterId)) {
                return Result.fail("That invitation is not for you.");
            }
            storytellerId = invite.requesterId();
            playerId = invite.targetId();
        } else {
            if (!StorytellerState.isStoryteller(accepterId)) {
                return Result.fail("Only a Storyteller can accept that request.");
            }
            if (invite.targetId() != null && !invite.targetId().equals(accepterId)) {
                return Result.fail("That request was sent to another Storyteller.");
            }
            storytellerId = accepterId;
            playerId = invite.requesterId();
        }

        if (!StorytellerState.isStoryteller(storytellerId)) {
            INVITES.remove(token);
            return Result.fail("The Storyteller is no longer available.");
        }
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(playerId)) {
            INVITES.remove(token);
            return Result.fail("The player is no longer seated.");
        }
        if (SESSIONS_BY_PARTICIPANT.containsKey(storytellerId)) {
            return Result.fail("That Storyteller is already in a private conversation.");
        }
        if (SESSIONS_BY_PARTICIPANT.containsKey(playerId)) {
            return Result.fail("That player is already in a private conversation.");
        }

        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api == null) return Result.fail("Simple Voice Chat server API is not online.");
        VoicechatConnection storyteller = api.getConnectionOf(storytellerId);
        VoicechatConnection player = api.getConnectionOf(playerId);
        if (storyteller == null || !storyteller.isConnected()) {
            return Result.fail("Storyteller voice connection is not online.");
        }
        if (player == null || !player.isConnected()) {
            return Result.fail("Player voice connection is not online.");
        }

        UUID groupId = UUID.nameUUIDFromBytes(("blood-on-the-sharktower:private:" + token)
                .getBytes(StandardCharsets.UTF_8));

        Group privateGroup = ensurePrivateGroup(api, groupId);
        storyteller.setGroup(privateGroup);
        player.setGroup(privateGroup);
        HOUSE_ROOMS.remove(storytellerId);
        HOUSE_ROOMS.remove(playerId);
        MANAGED_CONNECTIONS.add(storytellerId);
        MANAGED_CONNECTIONS.add(playerId);

        PrivateSession session = new PrivateSession(groupId, storytellerId, playerId, invite.houseSeat());
        SESSIONS_BY_PARTICIPANT.put(storytellerId, session);
        SESSIONS_BY_PARTICIPANT.put(playerId, session);

        // Consume every outstanding invite involving either participant.
        INVITES.entrySet().removeIf(entry -> entry.getValue().involves(storytellerId)
                || entry.getValue().involves(playerId));

        int seat = ServerState.PLAYER_SEAT_NUMBERS.getOrDefault(playerId, 0);
        return Result.ok("Private chat started with seat " + seat + ". Use /bots private leave when finished.");
    }

    /**
     * Leave a private room. Storyteller departure is intentionally asymmetric:
     * the Storyteller returns public immediately while the player stays private
     * until they decide to leave.
     */
    public static synchronized Result leavePrivate(UUID participantId) {
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api == null) return Result.fail("Simple Voice Chat server API is not online.");
        PrivateSession session = SESSIONS_BY_PARTICIPANT.get(participantId);
        if (session == null) {
            return Result.fail("You are not currently in a private Storyteller conversation.");
        }

        if (session.storytellerId().equals(participantId) && isStorytellerAttached(session)) {
            detachStorytellerInternal(api, session);
            if (routingServer != null) reconcile(routingServer);
            return Result.ok("Left the manual private chat. The player remains private until they leave it.");
        }

        endPlayerPrivateInternal(api, session);
        if (routingServer != null) reconcile(routingServer);
        return Result.ok("Manual private chat ended; physical house/daytime routing restored.");
    }

    /** Defensive cleanup when the Minecraft connection itself closes. */
    public static synchronized void onMinecraftPlayerDisconnected(UUID playerId) {
        if (playerId == null) return;
        INVITES.entrySet().removeIf(entry -> entry.getValue().involves(playerId));
        LAST_SENT_ROUTES.remove(playerId);
        HOUSE_ROOMS.remove(playerId);

        PrivateSession session = SESSIONS_BY_PARTICIPANT.get(playerId);
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (session != null) {
            if (session.storytellerId().equals(playerId)) {
                // Mirror the normal Storyteller-leaves behaviour: the player is
                // deliberately left in PRIVATE_HOLD until they choose to rejoin.
                SESSIONS_BY_PARTICIPANT.remove(playerId);
                MANAGED_CONNECTIONS.remove(playerId);
            } else if (api != null) {
                // A disconnected player cannot deliberately remain in a room;
                // free any attached Storyteller and discard the private group.
                endPlayerPrivateInternal(api, session);
            } else {
                SESSIONS_BY_PARTICIPANT.remove(session.playerId());
                if (SESSIONS_BY_PARTICIPANT.get(session.storytellerId()) == session) {
                    SESSIONS_BY_PARTICIPANT.remove(session.storytellerId());
                }
                MANAGED_CONNECTIONS.remove(session.playerId());
                MANAGED_CONNECTIONS.remove(session.storytellerId());
            }
        }
        MANAGED_CONNECTIONS.remove(playerId);
    }

    public static synchronized boolean isStorytellerAttachedToPrivate(UUID storytellerId) {
        PrivateSession session = SESSIONS_BY_PARTICIPANT.get(storytellerId);
        return session != null
                && session.storytellerId().equals(storytellerId)
                && isStorytellerAttached(session);
    }

    /**
     * Lightweight house-bound private-chat automation. Manual private chats are
     * deliberately ignored because they are not tied to a physical seat home.
     */
    public static synchronized void serverTick(MinecraftServer server) {
        if (server == null) return;

        // Physical houses and daytime doorways are reconciled every tick.
        // House-bound manual invitation cleanup is throttled below.
        reconcile(server);

        houseCheckTicks++;
        if (houseCheckTicks < HOUSE_CHECK_INTERVAL_TICKS) return;
        houseCheckTicks = 0;

        cleanupExpiredInvites();

        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api == null) {
            syncRouteHud(server);
            return;
        }

        // Cancel stale house invitations once the Storyteller has physically
        // moved away. This prevents an old invite from pulling them back later.
        for (Map.Entry<UUID, Invite> entry : new HashMap<>(INVITES).entrySet()) {
            Invite invite = entry.getValue();
            if (invite.type() != InviteType.STORYTELLER_TO_PLAYER || invite.houseSeat() == null) continue;

            ServerPlayer storyteller = connectedPlayer(server, invite.requesterId());
            if (storyteller != null
                    && SeatPositionManager.isPlayerNearHome(storyteller, invite.houseSeat(), HOUSE_EXIT_RADIUS)) {
                continue;
            }

            INVITES.remove(entry.getKey());
            ServerPlayer target = connectedPlayer(server, invite.targetId());
            if (target != null) {
                target.sendSystemMessage(Component.literal(
                        "The Storyteller moved on; that private-chat invitation has been cancelled.")
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        // For an accepted house chat, only detach the Storyteller. The player
        // remains safely isolated until they explicitly choose to leave.
        Set<UUID> handledGroups = new HashSet<>();
        for (PrivateSession session : new HashSet<>(SESSIONS_BY_PARTICIPANT.values())) {
            if (session.houseSeat() == null || !handledGroups.add(session.groupId())) continue;
            if (!isStorytellerAttached(session)) continue;

            ServerPlayer storyteller = connectedPlayer(server, session.storytellerId());
            if (storyteller != null
                    && SeatPositionManager.isPlayerNearHome(storyteller, session.houseSeat(), HOUSE_EXIT_RADIUS)) {
                continue;
            }

            detachStorytellerInternal(api, session);

            ServerPlayer player = connectedPlayer(server, session.playerId());
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                        "The Storyteller left your house. You remain in private chat until you choose to leave.")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (storyteller != null) {
                storyteller.sendSystemMessage(Component.literal("Left the private room; physical house/daytime routing restored.")
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        syncRouteHud(server);
    }

    public static synchronized void routeHouses(MinecraftServer server) {
        if (server == null) return;
        routingServer = server;
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api == null) return;
        Set<UUID> participants = houseParticipants();
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID(); online.add(id);
            if (SESSIONS_BY_PARTICIPANT.containsKey(id)) continue;
            Integer house = null;
            if (!ServerState.gameEnded && participants.contains(id)) {
                var distances = new java.util.TreeMap<Integer,Double>();
                for (int seat : new java.util.TreeSet<>(ServerState.PLAYER_SEAT_NUMBERS.values())) {
                    var home = SeatPositionManager.seatHome(seat);
                    if (home != null) distances.put(seat, player.distanceToSqr(home.x(),home.y(),home.z()));
                }
                house = HouseVoicePolicy.houseFor(ServerState.PLAYER_SEAT_NUMBERS.get(id),
                        StorytellerState.isStoryteller(id), distances, HOUSE_EXIT_RADIUS);
            }
            Integer previous = HOUSE_ROOMS.get(id);
            if (house == null) HOUSE_ROOMS.remove(id); else HOUSE_ROOMS.put(id,house);
            if (house != null) {
                DayChatZoneManager.yieldToHouse(id);
                assignCurrentPrivateRoute(api,id);
            } else {
                VoicechatConnection connection = api.getConnectionOf(id);
                Group current=connection==null ? null : connection.getGroup();
                if (previous != null || (current != null && reconnectGroupId(id).equals(current.getId()))) {
                    if (connection != null) connection.setGroup(null);
                    MANAGED_CONNECTIONS.remove(id);
                }
            }
        }
        HOUSE_ROOMS.keySet().retainAll(online);

    }

    public static synchronized boolean isHouseRouted(UUID id) {
        return HOUSE_ROOMS.containsKey(id) && !SESSIONS_BY_PARTICIPANT.containsKey(id);
    }
    public static synchronized void reconcile(MinecraftServer server) {
        if (server == null) return;
        routingServer = server;
        active = isAuthoritativeNight();
        routeHouses(server);
        DayChatZoneManager.serverTick(server);
    }
    private static void removeLegacyNightGroup() {
        VoicechatServerApi api=VoicechatIntegrationState.serverApi();
        if (api != null) try {
            api.removeGroup(UUID.nameUUIDFromBytes("blood-on-the-sharktower:night:shared".getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable ignored) {}
    }

    private static UUID reconnectGroupId(UUID id) {
        return UUID.nameUUIDFromBytes(("blood-on-the-sharktower:voice:reconnect:" + id).getBytes(StandardCharsets.UTF_8));
    }
    private static UUID houseGroupId(int seat) {
        return UUID.nameUUIDFromBytes(("blood-on-the-sharktower:house:" + seat).getBytes(StandardCharsets.UTF_8));
    }
    private static Group ensureHouseGroup(VoicechatServerApi api, int seat) {
        Group existing = findRegisteredGroup(api, houseGroupId(seat));
        if (existing != null) return existing;
        return api.groupBuilder().setId(houseGroupId(seat)).setName("BOTS House " + seat)
                .setPersistent(false).setHidden(true).setType(Group.Type.ISOLATED).build();
    }
    public static synchronized boolean sameNightRoom(UUID a, UUID b) {
        PrivateSession sa = SESSIONS_BY_PARTICIPANT.get(a), sb = SESSIONS_BY_PARTICIPANT.get(b);
        if (sa != null || sb != null) return sa != null && sa == sb && isStorytellerAttached(sa);
        return HouseVoicePolicy.sameRoom(HOUSE_ROOMS.get(a), HOUSE_ROOMS.get(b));
    }

    private static void syncRouteHud(MinecraftServer server) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            online.add(id);
            String route = routeCode(id);
            if (!route.equals(LAST_SENT_ROUTES.get(id))) {
                ServerPlayNetworking.send(player, new VoiceRouteS2CPayload(route));
                LAST_SENT_ROUTES.put(id, route);
            }
        }
        LAST_SENT_ROUTES.keySet().removeIf(id -> !online.contains(id));
    }

    /** Compatibility packet filter: privacy applies in every phase. */
    public static synchronized boolean shouldCancelSharedNightAudio(UUID senderId, UUID receiverId) {
        return senderId != null && receiverId != null && !senderId.equals(receiverId) && !sameNightRoom(senderId,receiverId);
    }

    private static boolean assignCurrentPrivateRoute(VoicechatServerApi api, UUID playerId) {
        try {
            VoicechatConnection connection = api.getConnectionOf(playerId);
            if (connection == null || !connection.isConnected()) return false;
            PrivateSession session = SESSIONS_BY_PARTICIPANT.get(playerId);
            Integer house = HOUSE_ROOMS.get(playerId);
            Group desired = session != null ? ensurePrivateGroup(api,session.groupId())
                    : house != null ? ensureHouseGroup(api,house) : null;
            if (desired == null) return false;
            Group current = connection.getGroup();
            if (current == null || !desired.getId().equals(current.getId())) connection.setGroup(desired);
            MANAGED_CONNECTIONS.add(playerId);
            return true;
        } catch (Throwable t) {
            lastRoutingError=shortError(t);
            BloodOnTheSharktower.LOGGER.error("Failed to route {} into a private voice room.",playerId,t);
            return false;
        }
    }

    private static String shortError(Throwable t) {
        if (t == null) return "unknown error";
        String message = t.getMessage();
        return t.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static Group ensurePrivateGroup(VoicechatServerApi api, UUID groupId) {
        Group existing = findRegisteredGroup(api, groupId);
        if (existing != null) return existing;
        return api.groupBuilder()
                .setId(groupId)
                .setName("BOTS Private Chat")
                .setPersistent(false)
                .setHidden(true)
                .setType(Group.Type.ISOLATED)
                .build();
    }

    /**
     * Resolve only groups that are actually registered with Simple Voice Chat.
     *
     * SVC 2.6.x getGroup(UUID) can return a non-null wrapper around a null
     * internal group when the requested UUID is absent. Calling getId() on that
     * wrapper then throws inside VoicechatConnection#setGroup. getGroups() only
     * exposes registered groups, so it is safe for existence checks.
     */
    private static Group findRegisteredGroup(VoicechatServerApi api, UUID groupId) {
        if (api == null || groupId == null) return null;
        try {
            for (Group group : api.getGroups()) {
                if (group != null && groupId.equals(group.getId())) return group;
            }
        } catch (Throwable t) {
            lastRoutingError = shortError(t);
            BloodOnTheSharktower.LOGGER.warn(
                    "Failed to enumerate Simple Voice Chat groups while looking for {}.", groupId, t);
        }
        return null;
    }

    private static Set<UUID> houseParticipants() {
        Set<UUID> participants = new HashSet<>(ServerState.PLAYER_SEAT_NUMBERS.keySet());
        participants.removeIf(StorytellerState::isStoryteller);
        participants.addAll(StorytellerState.STORYTELLERS);
        return participants;
    }

    private static boolean isStorytellerAttached(PrivateSession session) {
        return SESSIONS_BY_PARTICIPANT.get(session.storytellerId()) == session;
    }

    private static void routePublicOrProximity(VoicechatServerApi api, UUID participantId) {
        MANAGED_CONNECTIONS.remove(participantId);
        if (assignCurrentPrivateRoute(api,participantId)) return;
        VoicechatConnection connection = api.getConnectionOf(participantId);
        if (connection != null) connection.setGroup(null);
    }

    /** Detach only the Storyteller; the player remains isolated in the private room. */
    private static void detachStorytellerInternal(VoicechatServerApi api, PrivateSession session) {
        if (!isStorytellerAttached(session)) return;
        SESSIONS_BY_PARTICIPANT.remove(session.storytellerId());
        routePublicOrProximity(api, session.storytellerId());
    }

    /** Player leaves their private room. An attached Storyteller is freed too. */
    private static void endPlayerPrivateInternal(VoicechatServerApi api, PrivateSession session) {
        boolean storytellerAttached = isStorytellerAttached(session);
        SESSIONS_BY_PARTICIPANT.remove(session.playerId());
        if (storytellerAttached) {
            SESSIONS_BY_PARTICIPANT.remove(session.storytellerId());
        }

        routePublicOrProximity(api, session.playerId());
        if (storytellerAttached) {
            routePublicOrProximity(api, session.storytellerId());
        }

        try {
            api.removeGroup(session.groupId());
        } catch (Throwable ignored) {
            // Non-persistent group should disappear naturally when empty.
        }
    }

    private static ServerPlayer connectedPlayer(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (playerId.equals(player.getUUID())) return player;
        }
        return null;
    }

    private static void endAllPrivateSessions(VoicechatServerApi api) {
        Set<UUID> handledGroups = new HashSet<>();
        for (PrivateSession session : new HashSet<>(SESSIONS_BY_PARTICIPANT.values())) {
            if (!handledGroups.add(session.groupId())) continue;
            VoicechatConnection storyteller = api.getConnectionOf(session.storytellerId());
            if (storyteller != null) storyteller.setGroup(null);
            VoicechatConnection player = api.getConnectionOf(session.playerId());
            if (player != null) player.setGroup(null);
            MANAGED_CONNECTIONS.remove(session.storytellerId());
            MANAGED_CONNECTIONS.remove(session.playerId());
            try {
                api.removeGroup(session.groupId());
            } catch (Throwable ignored) {
            }
        }
        SESSIONS_BY_PARTICIPANT.clear();
    }

    private static int uniquePrivateSessionCount() {
        Set<UUID> groups = new HashSet<>();
        for (PrivateSession session : SESSIONS_BY_PARTICIPANT.values()) groups.add(session.groupId());
        return groups.size();
    }

    private static boolean isAuthoritativeNight() {
        return !(ServerState.currentNight == 0 && ServerState.currentDay == 0)
                && ServerState.currentNight != ServerState.currentDay;
    }

    private static void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        INVITES.entrySet().removeIf(entry -> entry.getValue().expiresAtMs() < now);
    }

    private enum InviteType {
        STORYTELLER_TO_PLAYER,
        PLAYER_TO_STORYTELLER
    }

    private record Invite(
            UUID token,
            InviteType type,
            UUID requesterId,
            UUID targetId,
            long expiresAtMs,
            Integer houseSeat
    ) {
        boolean involves(UUID playerId) {
            return requesterId.equals(playerId) || (targetId != null && targetId.equals(playerId));
        }
    }

    private record PrivateSession(UUID groupId, UUID storytellerId, UUID playerId, Integer houseSeat) {}

    public record Result(boolean ok, String message) {
        public static Result ok(String message) { return new Result(true, message); }
        public static Result fail(String message) { return new Result(false, message); }
    }

    public record InviteResult(boolean ok, UUID token, String message) {
        public static InviteResult ok(UUID token, String message) { return new InviteResult(true, token, message); }
        public static InviteResult fail(String message) { return new InviteResult(false, null, message); }
    }
}
