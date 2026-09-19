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
 * Shared Night Chat + temporary private Storyteller conversations.
 *
 * During NIGHT all seated players and active Storytellers are placed into one
 * hidden isolated Simple Voice Chat group. Storytellers therefore hear the public
 * night conversation automatically while they move between houses.
 *
 * A house-bound private conversation temporarily moves the Storyteller and one
 * player into an isolated room. When the Storyteller leaves that house, only the
 * Storyteller is detached and returned to public Night Chat. The player remains
 * isolated until they explicitly leave, avoiding an abrupt return to public audio.
 * Manual private conversations remain phase-independent.
 */
public final class NightChatManager {
    private static final UUID SHARED_NIGHT_GROUP_ID = UUID.nameUUIDFromBytes(
            "blood-on-the-sharktower:night:shared".getBytes(StandardCharsets.UTF_8));
    private static final long INVITE_TTL_MS = 60_000L;
    private static final double HOUSE_EXIT_RADIUS = 8.0D;
    private static final int HOUSE_CHECK_INTERVAL_TICKS = 5;

    private static final Set<UUID> MANAGED_CONNECTIONS = new HashSet<>();
    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    private static final Map<UUID, PrivateSession> SESSIONS_BY_PARTICIPANT = new HashMap<>();
    private static final Map<UUID, String> LAST_SENT_ROUTES = new HashMap<>();
    private static volatile boolean active;
    private static volatile String lastRoutingError;
    private static int houseCheckTicks;

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
                .filter(id -> Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(id)))
                .count();
        return "Night Chat: " + (active ? "ACTIVE" : "INACTIVE")
                + ", sharedNight=" + (active ? "ON" : "OFF")
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
        if (participantId == null) return "Voice route: unknown participant.";

        PrivateSession session = SESSIONS_BY_PARTICIPANT.get(participantId);
        if (session != null) {
            boolean storytellerAttached = isStorytellerAttached(session);
            if (session.playerId().equals(participantId) && !storytellerAttached) {
                return "Voice route: PRIVATE HOLD. The Storyteller has left; use /bots private leave when ready.";
            }
            UUID partner = session.storytellerId().equals(participantId)
                    ? session.playerId()
                    : session.storytellerId();
            return "Voice route: PRIVATE with " + partner + ".";
        }
        if (active && (StorytellerState.isStoryteller(participantId)
                || ServerState.PLAYER_SEAT_NUMBERS.containsKey(participantId))) {
            return MANAGED_CONNECTIONS.contains(participantId)
                    ? (StorytellerState.isStoryteller(participantId)
                        ? "Voice route: SHARED NIGHT CHAT (Storyteller monitoring)."
                        : "Voice route: SHARED NIGHT CHAT.")
                    : "Voice route: waiting for Simple Voice Chat connection; shared Night Chat will restore automatically.";
        }
        return "Voice route: proximity voice.";
    }

    /** Stable route code sent only to the participant whose HUD it describes. */
    public static synchronized String routeCode(UUID participantId) {
        if (participantId == null) return "PROXIMITY";

        PrivateSession session = SESSIONS_BY_PARTICIPANT.get(participantId);
        if (session != null) {
            if (session.playerId().equals(participantId) && !isStorytellerAttached(session)) {
                return "PRIVATE_HOLD";
            }
            return session.storytellerId().equals(participantId)
                    ? "PRIVATE_STORYTELLER"
                    : "PRIVATE";
        }

        if (active && publicNightParticipants().contains(participantId)) {
            if (!MANAGED_CONNECTIONS.contains(participantId)) return "WAITING";
            return StorytellerState.isStoryteller(participantId)
                    ? "SHARED_NIGHT_STORYTELLER"
                    : "SHARED_NIGHT";
        }

        // During the daytime automatic router, expose only this player's own
        // route. DAY_ZONE:<name> lets the client show a small private-area label;
        // DAY_SHARED clears that label again as soon as they leave the area.
        String dayRoute = DayChatZoneManager.routeCode(participantId);
        if (dayRoute != null) return dayRoute;

        return "PROXIMITY";
    }

    /** Send the current local route immediately, e.g. during initial login sync. */
    public static synchronized void sendRouteTo(ServerPlayer player) {
        if (player == null) return;
        String route = routeCode(player.getUUID());
        ServerPlayNetworking.send(player, new VoiceRouteS2CPayload(route));
        LAST_SENT_ROUTES.put(player.getUUID(), route);
    }

    /** Reconcile shared-night membership when Storyteller control changes. */
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

        if (api == null) return;
        if (active && (StorytellerState.isStoryteller(playerId)
                || ServerState.PLAYER_SEAT_NUMBERS.containsKey(playerId))) {
            assignSharedNight(api, playerId);
        } else if (!SESSIONS_BY_PARTICIPANT.containsKey(playerId)) {
            VoicechatConnection connection = api.getConnectionOf(playerId);
            if (connection != null) connection.setGroup(null);
            MANAGED_CONNECTIONS.remove(playerId);
        }
    }

    /** Clear bookkeeping if the voice server itself shuts down. */
    public static synchronized void onVoiceServerStopped() {
        active = false;
        INVITES.clear();
        SESSIONS_BY_PARTICIPANT.clear();
        MANAGED_CONNECTIONS.clear();
        LAST_SENT_ROUTES.clear();
        lastRoutingError = null;
    }

    /** Enter shared Night Chat using the committed seat map. */
    public static synchronized Result start() {
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api == null) return Result.fail("Simple Voice Chat server API is not online.");

        // During Night every seated player and Storyteller remains a member of the
        // shared room, even while a private Storyteller conversation is active.
        // Audio isolation is handled per sound packet instead of by changing group
        // membership. This keeps Simple Voice Chat's visible group-member roster
        // stable and prevents night-order meta from players disappearing/reappearing.
        active = true;
        lastRoutingError = null;

        Set<UUID> publicNightParticipants = publicNightParticipants();
        int assigned = 0;
        int waiting = 0;
        for (UUID participantId : publicNightParticipants) {
            if (assignSharedNight(api, participantId)) assigned++; else waiting++;
        }

        String summary = "Night Chat enabled: " + assigned
                + " player/Storyteller connection(s) joined the shared night room"
                + (waiting > 0 ? ", " + waiting + " waiting for voice connection." : ".");
        if (lastRoutingError != null) {
            return Result.fail(summary + " Last routing error: " + lastRoutingError);
        }
        return Result.ok(summary);
    }

    /**
     * Disable the shared Night Chat layer. Temporary private Storyteller
     * conversations are phase-independent and therefore remain active.
     */
    public static synchronized Result stop() {
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        active = false;
        lastRoutingError = null;

        int released = 0;
        if (api != null) {
            // At dawn the shared group is removed, so any private conversations
            // that are still active are materialized into their own SVC groups.
            // This preserves the existing phase-independent private-chat behavior
            // while allowing every shared-night head/icon to disappear together.
            materializePrivateSessions(api);

            for (UUID playerId : new HashSet<>(MANAGED_CONNECTIONS)) {
                // Private rooms survive day/setup transitions.
                if (SESSIONS_BY_PARTICIPANT.containsKey(playerId)) continue;

                VoicechatConnection connection = api.getConnectionOf(playerId);
                if (connection != null) {
                    connection.setGroup(null);
                    released++;
                }
                MANAGED_CONNECTIONS.remove(playerId);
            }
            try {
                api.removeGroup(SHARED_NIGHT_GROUP_ID);
            } catch (Throwable ignored) {
                // Group may already be gone; nothing to recover here.
            }
        } else {
            // Preserve private-session bookkeeping even if the API is briefly
            // unavailable; the voice lifecycle callbacks will clean it up.
            MANAGED_CONNECTIONS.removeIf(id -> !SESSIONS_BY_PARTICIPANT.containsKey(id));
        }

        return Result.ok("Shared Night Chat disabled; " + released
                + " voice connection(s) returned to proximity chat. Private chats remain available.");
    }

    /**
     * Dawn is a hard handoff from Night routing into shared Day Chat.
     *
     * Private Storyteller conversations and pending invitations that originated
     * during Night must not survive this transition, otherwise they continue to
     * claim the same Simple Voice Chat connections that DayChatZoneManager is
     * trying to place into BOTS Day Chat.
     *
     * New manual private conversations started after Dawn remain phase-independent.
     */
    public static synchronized Result stopForDawn() {
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        active = false;
        lastRoutingError = null;
        INVITES.clear();

        int privateSessions = uniquePrivateSessionCount();
        int released = 0;

        if (api != null) {
            endAllPrivateSessions(api);

            for (UUID playerId : new HashSet<>(MANAGED_CONNECTIONS)) {
                VoicechatConnection connection = api.getConnectionOf(playerId);
                if (connection != null) {
                    connection.setGroup(null);
                    released++;
                }
                MANAGED_CONNECTIONS.remove(playerId);
            }

            try {
                api.removeGroup(SHARED_NIGHT_GROUP_ID);
            } catch (Throwable ignored) {
                // Group may already be gone; nothing to recover here.
            }
        } else {
            SESSIONS_BY_PARTICIPANT.clear();
            MANAGED_CONNECTIONS.clear();
        }

        // Force the next state broadcast/tick to publish the new Day route instead
        // of retaining a cached PRIVATE/PRIVATE_HOLD HUD value.
        LAST_SENT_ROUTES.clear();

        return Result.ok("Dawn voice handoff complete: shared Night Chat stopped, "
                + privateSessions + " private session(s) ended, "
                + released + " remaining voice connection(s) released for Day Chat.");
    }

    /**
     * Tear down every BOTS voice route, including private sessions and pending
     * invitations. This is for full game/reset cleanup, not ordinary phase changes.
     */
    public static synchronized Result resetAll() {
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        active = false;
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
                api.removeGroup(SHARED_NIGHT_GROUP_ID);
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

    /** Re-apply shared-night routing without disturbing active private sessions. */
    public static synchronized Result resync() {
        if (!active) return Result.fail("Night Chat is not active.");
        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api == null) return Result.fail("Simple Voice Chat server API is not online.");

        cleanupExpiredInvites();

        Set<UUID> publicNightParticipants = publicNightParticipants();
        for (UUID participantId : new HashSet<>(MANAGED_CONNECTIONS)) {
            if (!publicNightParticipants.contains(participantId)) {
                VoicechatConnection connection = api.getConnectionOf(participantId);
                if (connection != null) connection.setGroup(null);
                MANAGED_CONNECTIONS.remove(participantId);
            }
        }

        int assigned = 0;
        int waiting = 0;
        for (UUID participantId : publicNightParticipants) {
            if (assignSharedNight(api, participantId)) assigned++; else waiting++;
        }

        return Result.ok("Night Chat resynced: " + assigned
                + " player/Storyteller connection(s) in shared night chat"
                + (waiting > 0 ? ", " + waiting + " waiting for voice connection." : "."));
    }

    /** Restore the authoritative route whenever Simple Voice Chat reconnects. */
    public static synchronized void onVoicePlayerConnected(UUID playerId) {
        if (playerId == null) return;

        if (!active && isAuthoritativeNight()) start();

        VoicechatServerApi api = VoicechatIntegrationState.serverApi();
        if (api == null) return;

        PrivateSession session = SESSIONS_BY_PARTICIPANT.get(playerId);
        if (session != null) {
            VoicechatConnection connection = api.getConnectionOf(playerId);
            if (connection == null || !connection.isConnected()) return;

            if (active) {
                // Private Night Chat participants remain members of the visible
                // shared roster; packet filtering preserves their isolation.
                assignSharedNight(api, playerId);
            } else {
                connection.setGroup(ensurePrivateGroup(api, session.groupId()));
                MANAGED_CONNECTIONS.add(playerId);
            }
            return;
        }

        if (!active) return;
        if (!publicNightParticipants().contains(playerId)) return;

        if (assignSharedNight(api, playerId)) {
            BloodOnTheSharktower.LOGGER.info("Restored shared Night Chat after voice reconnect for {}.", playerId);
        }
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
        // exposes them to public Night Chat. If the Storyteller was attached,
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

        if (active) {
            // Keep both participants in the shared Night Chat group so the group
            // roster stays visually unchanged. The voice plugin cancels packets
            // between this private pair and everyone else until the chat ends.
            assignSharedNight(api, storytellerId);
            assignSharedNight(api, playerId);
        } else {
            Group privateGroup = ensurePrivateGroup(api, groupId);
            storyteller.setGroup(privateGroup);
            player.setGroup(privateGroup);
            MANAGED_CONNECTIONS.add(storytellerId);
            MANAGED_CONNECTIONS.add(playerId);
        }

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
            return Result.ok(active
                    ? "Left private chat and returned to shared Night Chat. The player remains private until they leave."
                    : "Left private chat and returned to proximity voice. The player remains private until they leave.");
        }

        endPlayerPrivateInternal(api, session);
        return Result.ok(active
                ? "Private chat ended. Returned to shared Night Chat."
                : "Private chat ended. Returned to proximity voice.");
    }

    /** Defensive cleanup when the Minecraft connection itself closes. */
    public static synchronized void onMinecraftPlayerDisconnected(UUID playerId) {
        if (playerId == null) return;
        INVITES.entrySet().removeIf(entry -> entry.getValue().involves(playerId));
        LAST_SENT_ROUTES.remove(playerId);

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
        houseCheckTicks++;
        if (houseCheckTicks < HOUSE_CHECK_INTERVAL_TICKS) return;
        houseCheckTicks = 0;

        cleanupExpiredInvites();

        // Reconcile daytime public/private-zone routing on the same throttled
        // cadence as the existing Night Chat house checks.
        DayChatZoneManager.serverTick(server);

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
                storyteller.sendSystemMessage(Component.literal(active
                        ? "Left the private room and rejoined shared Night Chat."
                        : "Left the private room and returned to proximity voice.")
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        syncRouteHud(server);
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

    /**
     * Decide whether a Simple Voice Chat group packet should be hidden from one
     * receiver while Night Chat is active. Keeping everyone in the same SVC group
     * preserves the visible member roster; this method supplies the privacy layer.
     */
    public static synchronized boolean shouldCancelSharedNightAudio(UUID senderId, UUID receiverId) {
        if (!active || senderId == null || receiverId == null || senderId.equals(receiverId)) return false;

        PrivateSession senderSession = SESSIONS_BY_PARTICIPANT.get(senderId);
        PrivateSession receiverSession = SESSIONS_BY_PARTICIPANT.get(receiverId);
        if (senderSession == null && receiverSession == null) return false;

        // Only the two participants in the same still-attached private session
        // may hear one another. A player in PRIVATE_HOLD hears nobody until they
        // deliberately leave the hold, exactly as before.
        return senderSession == null
                || receiverSession == null
                || senderSession != receiverSession
                || !isStorytellerAttached(senderSession);
    }

    /** Move active private sessions out of the shared Night group at dawn. */
    private static void materializePrivateSessions(VoicechatServerApi api) {
        Set<UUID> handledGroups = new HashSet<>();
        for (PrivateSession session : new HashSet<>(SESSIONS_BY_PARTICIPANT.values())) {
            if (!handledGroups.add(session.groupId())) continue;

            Group privateGroup = ensurePrivateGroup(api, session.groupId());
            VoicechatConnection player = api.getConnectionOf(session.playerId());
            if (player != null && player.isConnected()) {
                player.setGroup(privateGroup);
                MANAGED_CONNECTIONS.add(session.playerId());
            }

            if (isStorytellerAttached(session)) {
                VoicechatConnection storyteller = api.getConnectionOf(session.storytellerId());
                if (storyteller != null && storyteller.isConnected()) {
                    storyteller.setGroup(privateGroup);
                    MANAGED_CONNECTIONS.add(session.storytellerId());
                }
            }
        }
    }

    private static boolean assignSharedNight(VoicechatServerApi api, UUID playerId) {
        try {
            VoicechatConnection connection = api.getConnectionOf(playerId);
            if (connection == null || !connection.isConnected()) return false;
            connection.setGroup(ensureSharedNightGroup(api));
            MANAGED_CONNECTIONS.add(playerId);
            return true;
        } catch (Throwable t) {
            lastRoutingError = shortError(t);
            BloodOnTheSharktower.LOGGER.error(
                    "Failed to route {} into shared Night Chat.", playerId, t);
            MANAGED_CONNECTIONS.remove(playerId);
            return false;
        }
    }

    private static String shortError(Throwable t) {
        if (t == null) return "unknown error";
        String message = t.getMessage();
        return t.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static Group ensureSharedNightGroup(VoicechatServerApi api) {
        Group existing = findRegisteredGroup(api, SHARED_NIGHT_GROUP_ID);
        if (existing != null) return existing;
        return api.groupBuilder()
                .setId(SHARED_NIGHT_GROUP_ID)
                .setName("BOTS Night Chat")
                .setPersistent(true)
                .setHidden(true)
                .setType(Group.Type.ISOLATED)
                .build();
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

    private static Set<UUID> publicNightParticipants() {
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
        if (active && publicNightParticipants().contains(participantId)) {
            assignSharedNight(api, participantId);
            return;
        }
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
