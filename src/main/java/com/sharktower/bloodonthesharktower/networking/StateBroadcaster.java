package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.core.AlignmentOverride;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleType;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.daytime.VotingManager;
import com.sharktower.bloodonthesharktower.daytime.VotePresentationSettings;
import com.sharktower.bloodonthesharktower.daytime.ElectionState;
import com.sharktower.bloodonthesharktower.daytime.ExileSupportManager;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.nightorder.TriggeredNightOrderManager;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.timer.TimerManager;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bulk 26.2 port of BOTB StateBroadcaster's core gameplay state family.
 */
public final class StateBroadcaster {
    private StateBroadcaster() {}

    public static int sendCurrentStateTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SyncDayNightS2CPayload(
                ServerState.currentNight,
                ServerState.currentDay,
                ServerState.executionToday
        ));

        if (ServerState.currentScript != null) {
            ServerPlayNetworking.send(player, new SendScriptS2CPayload(ServerState.currentScript));
        }

        sendRoleTo(player, true);
        sendSeatsTo(player);
        sendDeathStatusTo(player);
        sendGrimoireTo(player, true);
        sendPlayerDirectoryTo(player);
        sendDaytimeStateTo(player);
        sendVoteStateTo(player);
        TimerManager.addPlayer(player);
        NightChatManager.sendRouteTo(player);
        sendEndGameStateTo(player);
        sendTriggeredNightOrderTo(player);
        sendStorytellerNightInfoTo(player);

        // The probe is deliberately last so its acknowledgement proves that
        // all earlier core-state packets were delivered/applied in order.
        return NetworkDiagnostics.sendProbe(player);
    }

    public static void sendRoleTo(ServerPlayer player, boolean silent) {
        PendingRoleAssignment actual = ServerState.PLAYER_ROLES.getOrDefault(
                player.getUUID(),
                new PendingRoleAssignment(Role.NO_ROLE, AlignmentOverride.DEFAULT)
        );
        PendingRoleAssignment assignment = playerFacingAssignment(player.getUUID(), actual);

        ServerPlayNetworking.send(player, SendRoleS2CPayload.ofAssignment(
                assignment,
                activePlayerCount(),
                travelerCount(),
                silent
        ));
    }

    public static void sendSeatsTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SendSeatsS2CPayload(effectiveSeatNumbersForSync()));
    }

    public static void sendDeathStatusTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SendDeathStatusS2CPayload(ServerState.PLAYER_DEATH_STATUS));
    }

    public static void sendGrimoireTo(ServerPlayer player, boolean targeted) {
        boolean storyteller = StorytellerState.isStoryteller(player.getUUID());
        boolean finalReveal = ServerState.rolesRevealed;

        java.util.Map<java.util.UUID, PendingRoleAssignment> roles;
        java.util.Map<java.util.UUID, PendingRoleAssignment> perceivedRoles;
        java.util.Map<java.util.UUID, Integer> seats;
        java.util.Map<java.util.UUID, java.util.List<com.sharktower.bloodonthesharktower.core.Reminder>> reminders;
        java.util.List<com.sharktower.bloodonthesharktower.core.ScriptRole> bluffs;

        if (finalReveal) {
            // Once the game is over the complete Grimoire is intentionally public.
            roles = ServerState.PLAYER_ROLES;
            perceivedRoles = storyteller ? ServerState.PLAYER_PERCEIVED_ROLES : java.util.Map.of();
            seats = ServerState.PLAYER_SEAT_NUMBERS;
            reminders = StorytellerState.REMINDERS;
            bluffs = StorytellerState.DEMON_BLUFFS;
        } else if (storyteller) {
            // Storytellers receive the complete working Grimoire, including pending setup edits.
            roles = StorytellerState.effectiveGrimoireRoles();
            perceivedRoles = StorytellerState.effectiveGrimoirePerceivedRoles();
            seats = StorytellerState.effectiveGrimoireSeats();
            reminders = StorytellerState.REMINDERS;
            bluffs = StorytellerState.DEMON_BLUFFS;
        } else {
            // Ordinary players only receive role information they are entitled to know.
            // This keeps the client-side Grimoire safe to use for floating world tokens.
            java.util.Map<java.util.UUID, PendingRoleAssignment> visible = new java.util.LinkedHashMap<>();
            PendingRoleAssignment actualOwn = ServerState.PLAYER_ROLES.get(player.getUUID());
            PendingRoleAssignment visibleOwn = playerFacingAssignment(player.getUUID(), actualOwn);
            if (isAssigned(visibleOwn)) visible.put(player.getUUID(), visibleOwn);

            // Traveller character identities are public, but their alignment is not.
            for (java.util.Map.Entry<java.util.UUID, PendingRoleAssignment> entry : ServerState.PLAYER_ROLES.entrySet()) {
                if (entry.getKey().equals(player.getUUID())) continue;
                PendingRoleAssignment assignment = entry.getValue();
                if (isAssigned(assignment) && assignment.getRoleType() == RoleType.TRAVELER) {
                    visible.put(entry.getKey(), publicRoleOnly(assignment));
                }
            }

            roles = visible;
            perceivedRoles = java.util.Map.of();
            seats = ServerState.PLAYER_SEAT_NUMBERS;
            reminders = java.util.Map.of();
            bluffs = isAssigned(actualOwn) && actualOwn.getRoleType() == RoleType.DEMON
                    ? StorytellerState.DEMON_BLUFFS
                    : java.util.List.of();
        }

        ServerPlayNetworking.send(player, SendGrimoireS2CPayload.fromStoryteller(
                roles,
                perceivedRoles,
                seats,
                reminders,
                bluffs,
                targeted
        ));
    }

    private static PendingRoleAssignment playerFacingAssignment(
            java.util.UUID playerId, PendingRoleAssignment actual) {
        if (!isAssigned(actual) || ServerState.rolesRevealed) return actual;
        if (!actual.isCustomRole() && (actual.role() == Role.DRUNK || actual.role() == Role.MARIONETTE)) {
            PendingRoleAssignment perceived = ServerState.PLAYER_PERCEIVED_ROLES.get(playerId);
            // Never leak the true Drunk/Marionette token if setup data is incomplete.
            return isAssigned(perceived)
                    ? perceived
                    : new PendingRoleAssignment(Role.NO_ROLE, AlignmentOverride.DEFAULT);
        }
        return actual;
    }

    private static PendingRoleAssignment publicRoleOnly(PendingRoleAssignment assignment) {
        if (assignment == null) return new PendingRoleAssignment(Role.NO_ROLE, AlignmentOverride.DEFAULT);
        if (assignment.isCustomRole() && assignment.customRole().isPresent()) {
            return new PendingRoleAssignment(assignment.customRole().get(), AlignmentOverride.DEFAULT);
        }
        return new PendingRoleAssignment(assignment.role(), AlignmentOverride.DEFAULT);
    }



    public static void sendPlayerDirectoryTo(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        java.util.Map<java.util.UUID, String> names = new java.util.LinkedHashMap<>();
        java.util.List<java.util.UUID> connected = new java.util.ArrayList<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            names.put(online.getUUID(), online.getName().getString());
            connected.add(online.getUUID());
        }
        ServerPlayNetworking.send(player, new PlayerDirectoryS2CPayload(
                names, connected, new java.util.ArrayList<>(StorytellerState.STORYTELLERS)
        ));
    }

    public static void sendEndGameStateTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, new EndGameStateS2CPayload(
                ServerState.gameEnded,
                ServerState.rolesRevealed,
                ServerState.winningTeam
        ));
    }


    public static void sendTriggeredNightOrderTo(ServerPlayer player) {
        String encoded = StorytellerState.isStoryteller(player.getUUID())
                ? TriggeredNightOrderManager.encodedSnapshot()
                : "";
        ServerPlayNetworking.send(player, new TriggeredNightOrderS2CPayload(encoded));
    }

    public static void broadcastTriggeredNightOrder(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTriggeredNightOrderTo(player);
        }
    }

    public static void sendStorytellerNightInfoTo(ServerPlayer player) {
        boolean storyteller = StorytellerState.isStoryteller(player.getUUID());
        ServerPlayNetworking.send(player, new StorytellerNightInfoS2CPayload(
                storyteller ? StorytellerState.lastExecutedRoleName : "",
                storyteller && StorytellerState.demonVotedToday,
                storyteller && StorytellerState.minionNominatedToday
        ));
    }

    public static void broadcastStorytellerNightInfo(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendStorytellerNightInfoTo(player);
        }
    }

    public static void broadcastEndGameState(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendEndGameStateTo(player);
        }
    }

    public static void broadcastPlayerDirectory(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendPlayerDirectoryTo(player);
    }

    public static void sendDaytimeStateTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, currentDaytimePayload());
    }

    public static void sendVoteStateTo(ServerPlayer player) {
        boolean exileSupport = DaytimeState.isExileSupportInProgress();
        boolean exileActive = DaytimeState.getCurrentExileTarget() != null;
        int threshold = exileActive ? ExileSupportManager.currentThreshold() : VotingManager.currentHandsRequired();
        int effectiveCount = exileSupport ? ExileSupportManager.effectiveSupportCount() : VotingManager.effectiveVoteCount();
        VotingManager.Result last = VotingManager.getLastResult();
        MinecraftServer server = player.level().getServer();

        SeatPositionManager.Position center = SeatPositionManager.clockCenter();
        boolean electionClock = DaytimeState.isVoteInProgress() || exileSupport;
        java.util.UUID electionTarget = exileActive ? DaytimeState.getCurrentExileTarget() : DaytimeState.getCurrentNominee();
        java.util.UUID electionInitiator = exileActive ? DaytimeState.getCurrentExileCaller() : DaytimeState.getCurrentNominator();
        java.util.UUID activeTarget = electionClock ? ElectionState.getCurrentVoter() : electionTarget;
        java.util.UUID referenceTarget = electionClock ? electionTarget : electionInitiator;
        SeatPositionManager.Position target = clockPosition(server, activeTarget);
        SeatPositionManager.Position reference = clockPosition(server, referenceTarget);

        ServerPlayNetworking.send(player, new VoteStateUpdateS2CPayload(
                DaytimeState.isVoteInProgress(),
                effectiveCount,
                threshold,
                VotingManager.raisedHandCount(),
                DaytimeState.getRaisedHands(),
                exileSupport ? DaytimeState.getExileSupportVotes() : DaytimeState.getCurrentVotes(),
                exileSupport ? DaytimeState.getLockedExileSupportVotes() : DaytimeState.getLockedVotes(),
                DaytimeState.getLeverStates(),
                DaytimeState.isOrganGrinderMode(),
                exileSupport,
                ElectionState.getCurrentVoter(),
                ElectionState.getCurrentPlayerIndex(),
                ElectionState.getElectionSize(),
                ElectionState.isVotingPhaseComplete(),
                DaytimeState.hasManualVoteCountOverride(),
                DaytimeState.getManualVoteCountOverride(),
                last.result().name(),
                last.nominee(),
                last.votes(),
                last.threshold(),
                center != null,
                center == null ? 0.0D : center.x(),
                center == null ? 0.0D : center.y(),
                center == null ? 0.0D : center.z(),
                SeatPositionManager.clockHandScale(),
                VotePresentationSettings.stepTicks(),
                target != null,
                target == null ? 0.0D : target.x(),
                target == null ? 0.0D : target.y(),
                target == null ? 0.0D : target.z(),
                reference != null,
                reference == null ? 0.0D : reference.x(),
                reference == null ? 0.0D : reference.y(),
                reference == null ? 0.0D : reference.z()
        ));
    }

    private static SeatPositionManager.Position clockPosition(MinecraftServer server, java.util.UUID id) {
        if (id == null) return null;
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                return new SeatPositionManager.Position(online.getX(), online.getY(), online.getZ());
            }
        }
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(id);
        return seat == null ? null : SeatPositionManager.townSquareSeat(seat);
    }

    private static SyncDaytimeStateS2CPayload currentDaytimePayload() {
        return new SyncDaytimeStateS2CPayload(
                DaytimeState.getCanNominateMap(),
                DaytimeState.getCanBeNominatedMap(),
                DaytimeState.getGhostVoteMap(),
                DaytimeState.getNominationsRemainingMap(),
                DaytimeState.getCurrentNominator(),
                DaytimeState.getCurrentNominee(),
                DaytimeState.getMarkedForExecution(),
                DaytimeState.getVotesForMarkedPlayer(),
                DaytimeState.areNominationsOpen(),
                DaytimeState.isOrganGrinderModeActiveToday(),
                DaytimeState.getStorytellerMFE(),
                DaytimeState.getStorytellerMFEVotes(),
                DaytimeState.canStorytellerBeNominated(),
                DaytimeState.getCanBeExiledMap(),
                DaytimeState.getExiledTravelerMap(),
                DaytimeState.getCurrentExileCaller(),
                DaytimeState.getCurrentExileTarget(),
                DaytimeState.isExileSupportInProgress(),
                DaytimeState.getLockedExileSupportCount(),
                DaytimeState.isVoudonModeActive(),
                DaytimeState.getVoudonPlayerUuid()
        );
    }

    public static int activePlayerCount() {
        int count = 0;
        for (PendingRoleAssignment assignment : ServerState.PLAYER_ROLES.values()) {
            if (isAssigned(assignment) && assignment.getRoleType() != RoleType.TRAVELER) {
                count++;
            }
        }
        return count;
    }

    public static int travelerCount() {
        int count = 0;
        for (java.util.Map.Entry<java.util.UUID, PendingRoleAssignment> entry : ServerState.PLAYER_ROLES.entrySet()) {
            PendingRoleAssignment assignment = entry.getValue();
            if (isAssigned(assignment) && assignment.getRoleType() == RoleType.TRAVELER
                    && !DaytimeState.isExiledTraveler(entry.getKey())) {
                count++;
            }
        }
        return count;
    }

    public static int seatedPlayerCount() {
        return ServerState.PLAYER_SEAT_NUMBERS.size();
    }

    public static int deadPlayerCount() {
        return ServerState.deadPlayers().size();
    }

    private static boolean isAssigned(PendingRoleAssignment assignment) {
        return assignment != null
                && (assignment.isCustomRole() || assignment.role() != Role.NO_ROLE);
    }

    public static void broadcastDayNightState(MinecraftServer server) {
        SyncDayNightS2CPayload payload = new SyncDayNightS2CPayload(
                ServerState.currentNight,
                ServerState.currentDay,
                ServerState.executionToday
        );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }


    public static void broadcastDaytimeState(MinecraftServer server) {
        SyncDaytimeStateS2CPayload payload = currentDaytimePayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void broadcastVoteState(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendVoteStateTo(player);
        }
    }

    public static void broadcastScript(MinecraftServer server) {
        if (ServerState.currentScript == null) return;
        SendScriptS2CPayload payload = new SendScriptS2CPayload(ServerState.currentScript);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void broadcastRoles(MinecraftServer server, boolean silent) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendRoleTo(player, silent);
        }
    }

    public static void broadcastSeats(MinecraftServer server) {
        SendSeatsS2CPayload payload = new SendSeatsS2CPayload(effectiveSeatNumbersForSync());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /**
     * During game setup the authoritative seating lives in the Storyteller's
     * pending setup map until SEND ROLES commits it. Sync that working map so
     * the setup HUD and other seat-aware client UI update immediately when a
     * player joins, leaves, or is reseated. Once the game is active, only the
     * committed live seat map is exposed.
     */
    private static java.util.Map<java.util.UUID, Integer> effectiveSeatNumbersForSync() {
        boolean setup = ServerState.currentNight == 0 && ServerState.currentDay == 0 && !ServerState.gameEnded;
        return setup ? StorytellerState.effectiveGrimoireSeats() : ServerState.PLAYER_SEAT_NUMBERS;
    }

    public static void broadcastDeathStatus(MinecraftServer server) {
        SendDeathStatusS2CPayload payload = new SendDeathStatusS2CPayload(ServerState.PLAYER_DEATH_STATUS);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
        broadcastTriggeredNightOrder(server);
    }

    public static void broadcastGrimoire(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendGrimoireTo(player, false);
        }
    }

    public static int broadcastCurrentState(MinecraftServer server) {
        int lastSequence = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            lastSequence = sendCurrentStateTo(player);
        }
        return lastSequence;
    }
}
