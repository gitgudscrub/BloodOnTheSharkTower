package com.sharktower.bloodonthesharktower.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.core.AlignmentOverride;
import com.sharktower.bloodonthesharktower.core.GamePhase;
import com.sharktower.bloodonthesharktower.core.LegacyPortInfo;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.PhaseOperations;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleModelDiagnostics;
import com.sharktower.bloodonthesharktower.core.Script;
import com.sharktower.bloodonthesharktower.core.ScriptNetworkTestData;
import com.sharktower.bloodonthesharktower.daytime.DaytimeState;
import com.sharktower.bloodonthesharktower.daytime.ExecutionManager;
import com.sharktower.bloodonthesharktower.daytime.ExileManager;
import com.sharktower.bloodonthesharktower.daytime.ExileSupportManager;
import com.sharktower.bloodonthesharktower.daytime.NominationManager;
import com.sharktower.bloodonthesharktower.daytime.VotingManager;
import com.sharktower.bloodonthesharktower.daytime.VotePresentationSettings;
import com.sharktower.bloodonthesharktower.networking.NetworkDiagnostics;
import com.sharktower.bloodonthesharktower.networking.StateBroadcaster;
import com.sharktower.bloodonthesharktower.networking.StorytellerActionHandler;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import com.sharktower.bloodonthesharktower.setup.SeatPositionManager;
import com.sharktower.bloodonthesharktower.setup.SetupOperations;
import com.sharktower.bloodonthesharktower.setup.AutoSeatManager;
import com.sharktower.bloodonthesharktower.snapshot.MatchSnapshotManager;
import com.sharktower.bloodonthesharktower.timer.TimerManager;
import com.sharktower.bloodonthesharktower.voicechat.VoicechatIntegrationState;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import com.sharktower.bloodonthesharktower.voicechat.DayChatZoneManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Blood on the Sharktower command root.
 *
 * The original private BOTB /botb tree is being bulk-ported under /bots.
 * Diagnostic commands stay here while the original command handlers are wired
 * back in during the playable-core/game-flow batches.
 */
public final class BotsCommands {
    public static final String ROOT = "bots";

    /** Synthetic UUIDs created only by /bots testseats for Grimoire layout testing. */
    private static final Set<UUID> SYNTHETIC_TEST_PLAYERS = new HashSet<>();

    /**
     * Deliberately varied Trouble Brewing palette so a populated test Grimoire
     * exercises Townsfolk, Outsider, Minion and Demon token rendering.
     */
    private static final List<Role> TEST_GRIMOIRE_ROLES = List.of(
            Role.EMPATH,
            Role.FORTUNE_TELLER,
            Role.UNDERTAKER,
            Role.MONK,
            Role.RAVENKEEPER,
            Role.VIRGIN,
            Role.SLAYER,
            Role.SOLDIER,
            Role.MAYOR,
            Role.BUTLER,
            Role.DRUNK,
            Role.RECLUSE,
            Role.SAINT,
            Role.POISONER,
            Role.SPY,
            Role.SCARLET_WOMAN,
            Role.BARON,
            Role.IMP,
            Role.WASHERWOMAN,
            Role.INVESTIGATOR
    );

    private BotsCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal(ROOT)
                        .executes(BotsCommands::executeStatus)
                        .then(Commands.literal("status")
                                .executes(BotsCommands::executeStatus))
                        .then(Commands.literal("voice")
                                .executes(BotsCommands::executeVoiceStatus)
                                .then(Commands.literal("status")
                                        .executes(BotsCommands::executeVoiceStatus)))
                        .then(Commands.literal("daychat")
                                .executes(BotsCommands::executeDayChatStatus)
                                .then(Commands.literal("status").executes(BotsCommands::executeDayChatStatus))
                                .then(Commands.literal("zones").executes(BotsCommands::executeDayChatZones))
                                .then(Commands.literal("clearZones").executes(BotsCommands::executeDayChatClearZones))
                                .then(Commands.literal("zone")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .then(Commands.literal("entrance").executes(BotsCommands::executeDayChatZoneEntrance))
                                                .then(Commands.literal("exit").executes(BotsCommands::executeDayChatZoneExit))
                                                .then(Commands.literal("clearEntrances").executes(BotsCommands::executeDayChatZoneClearEntrances))
                                                .then(Commands.literal("clearExits").executes(BotsCommands::executeDayChatZoneClearExits))
                                                .then(Commands.literal("remove").executes(BotsCommands::executeDayChatZoneRemove)))))
                        .then(Commands.literal("nightchat")
                                .executes(BotsCommands::executeNightChatStatus)
                                .then(Commands.literal("status").executes(BotsCommands::executeNightChatStatus))
                                .then(Commands.literal("whoami").executes(BotsCommands::executeNightChatWhoAmI))
                                .then(Commands.literal("start").executes(BotsCommands::executeNightChatStart))
                                .then(Commands.literal("stop").executes(BotsCommands::executeNightChatStop))
                                .then(Commands.literal("resync").executes(BotsCommands::executeNightChatResync))
                                // Compatibility aliases for the original 1.0.2-dev test commands.
                                // "join" now sends an invitation rather than forcibly moving the player.
                                .then(Commands.literal("join")
                                        .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                                .executes(BotsCommands::executePrivateInvite)))
                                .then(Commands.literal("leave").executes(BotsCommands::executePrivateLeave)))
                        .then(Commands.literal("private")
                                .then(Commands.literal("request").executes(BotsCommands::executePrivateRequest))
                                .then(Commands.literal("invite")
                                        .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                                .executes(BotsCommands::executePrivateInvite)))
                                .then(Commands.literal("accept")
                                        .then(Commands.argument("token", StringArgumentType.word())
                                                .executes(BotsCommands::executePrivateAccept)))
                                .then(Commands.literal("leave").executes(BotsCommands::executePrivateLeave)))
                        .then(Commands.literal("sync")
                                .executes(BotsCommands::executeSync))
                        .then(Commands.literal("testscript")
                                .executes(BotsCommands::executeTestScript))
                        .then(Commands.literal("testrole")
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .executes(BotsCommands::executeTestRole)))
                        .then(Commands.literal("testseat")
                                .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                        .executes(BotsCommands::executeTestSeat)))
                        .then(Commands.literal("testseats")
                                .then(Commands.literal("clear")
                                        .executes(BotsCommands::executeClearTestSeats))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                        .executes(BotsCommands::executeTestSeats)))
                        .then(Commands.literal("testdead")
                                .then(Commands.argument("dead", BoolArgumentType.bool())
                                        .executes(BotsCommands::executeTestDead)))
                        .then(Commands.literal("dusk").executes(BotsCommands::executeDusk))
                        .then(Commands.literal("dawn").executes(BotsCommands::executeDawn))
                        .then(Commands.literal("testphase")
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .executes(BotsCommands::executeTestPhase)))
                        .then(Commands.literal("storyteller")
                                .then(Commands.literal("claim").executes(BotsCommands::executeStorytellerClaim))
                                .then(Commands.literal("release").executes(BotsCommands::executeStorytellerRelease)))
                        .then(Commands.literal("sharegrimoire")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(BotsCommands::executeShareGrimoire)))
                        .then(Commands.literal("setupStatus").executes(BotsCommands::executeSetupStatus))
                        .then(Commands.literal("seatPlayer")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                                .executes(BotsCommands::executeSeatPlayer))))
                        .then(Commands.literal("unseatPlayer")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(BotsCommands::executeUnseatPlayer)))
                        .then(Commands.literal("seatAll").executes(BotsCommands::executeSeatAll))
                        .then(Commands.literal("assignRole")
                                .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .executes(BotsCommands::executeAssignRole))))
                        .then(Commands.literal("clearRole")
                                .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                        .executes(BotsCommands::executeClearRole)))
                        .then(Commands.literal("alignment")
                                .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("alignment", StringArgumentType.word())
                                                .executes(BotsCommands::executeAlignment))))
                        .then(Commands.literal("swapRoles")
                                .then(Commands.argument("seatA", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("seatB", IntegerArgumentType.integer(1))
                                                .executes(BotsCommands::executeSwapRoles))))
                        .then(Commands.literal("swapSeats")
                                .then(Commands.argument("seatA", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("seatB", IntegerArgumentType.integer(1))
                                                .executes(BotsCommands::executeSwapSeats))))
                        .then(Commands.literal("shuffleRoles").executes(BotsCommands::executeShuffleRoles))
                        .then(Commands.literal("shuffleSeats").executes(BotsCommands::executeShuffleSeats))
                        .then(Commands.literal("randomizeRoles").executes(BotsCommands::executeRandomizeRoles))
                        .then(Commands.literal("bluffs")
                                .then(Commands.literal("randomize").executes(BotsCommands::executeRandomizeBluffs))
                                .then(Commands.literal("clear").executes(BotsCommands::executeClearBluffs))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .executes(BotsCommands::executeAddBluff))))
                        .then(Commands.literal("reminder")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(BotsCommands::executeAddReminder))))
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                                .executes(BotsCommands::executeClearReminders)))
                                .then(Commands.literal("clearAll").executes(BotsCommands::executeClearAllReminders)))
                        .then(Commands.literal("sendRoles").executes(BotsCommands::executeSendRoles))
                        .then(Commands.literal("discardSetup").executes(BotsCommands::executeDiscardSetup))
                        .then(Commands.literal("resetGame").executes(BotsCommands::executeResetGame))
                        .then(Commands.literal("resetGameHard").executes(BotsCommands::executeResetGameHard))
                        .then(Commands.literal("gameComplete").executes(BotsCommands::executeGameComplete))
                        .then(Commands.literal("endGame")
                                .then(Commands.literal("good").executes(BotsCommands::executeEndGameGood))
                                .then(Commands.literal("evil").executes(BotsCommands::executeEndGameEvil))
                                .then(Commands.literal("cancel").executes(BotsCommands::executeEndGameCancel)))
                        .then(Commands.literal("resetForNextGame").executes(BotsCommands::executeResetForNextGame))
                        .then(Commands.literal("snapshot")
                                .executes(BotsCommands::executeSnapshotStatus)
                                .then(Commands.literal("status").executes(BotsCommands::executeSnapshotStatus))
                                .then(Commands.literal("corner1").executes(BotsCommands::executeSnapshotCorner1))
                                .then(Commands.literal("corner2").executes(BotsCommands::executeSnapshotCorner2))
                                .then(Commands.literal("clearRegion").executes(BotsCommands::executeSnapshotClearRegion))
                                .then(Commands.literal("capture").executes(BotsCommands::executeSnapshotCapture))
                                .then(Commands.literal("restore").executes(BotsCommands::executeSnapshotRestore))
                                .then(Commands.literal("restorePrevious").executes(BotsCommands::executeSnapshotRestorePrevious)))
                        .then(Commands.literal("setSeatHome")
                                .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                        .executes(BotsCommands::executeSetSeatHome)))
                        .then(Commands.literal("setTownSquareSeat")
                                .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                        .executes(BotsCommands::executeSetTownSquareSeat)))
                        .then(Commands.literal("setClockCenter").executes(BotsCommands::executeSetClockCenter))
                        .then(Commands.literal("setClockHandScale")
                                .then(Commands.argument("scale", DoubleArgumentType.doubleArg(0.5D, 12.0D))
                                        .executes(BotsCommands::executeSetClockHandScale)))
                        .then(Commands.literal("setVoteSpeed")
                                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.5D, 3.0D))
                                        .executes(BotsCommands::executeSetVoteSpeed)))
                        .then(Commands.literal("sendToSeats").executes(BotsCommands::executeSendToSeats))
                        .then(Commands.literal("sendHome").executes(BotsCommands::executeSendHome))
                        .then(Commands.literal("teleportToSeat")
                                .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                        .executes(BotsCommands::executeTeleportToSeat)))
                        .then(Commands.literal("nominations")
                                .then(Commands.literal("open").executes(BotsCommands::executeOpenNominations))
                                .then(Commands.literal("close").executes(BotsCommands::executeCloseNominations)))
                        .then(Commands.literal("nominate")
                                .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                        .executes(BotsCommands::executeNominate)))
                        .then(Commands.literal("runVote").executes(BotsCommands::executeRunVote))
                        .then(Commands.literal("hand")
                                .executes(BotsCommands::executeToggleHand)
                                .then(Commands.literal("up").executes(BotsCommands::executeRaiseHand))
                                .then(Commands.literal("down").executes(BotsCommands::executeLowerHand))
                                .then(Commands.literal("status").executes(BotsCommands::executeHandStatus)))
                        .then(Commands.literal("vote")
                                .then(Commands.argument("yes", BoolArgumentType.bool())
                                        .executes(BotsCommands::executeVote)))
                        .then(Commands.literal("resolveVote").executes(BotsCommands::executeResolveVote))
                        .then(Commands.literal("cancelVote").executes(BotsCommands::executeCancelVote))
                        .then(Commands.literal("resetVote").executes(BotsCommands::executeResetVote))
                        .then(Commands.literal("execute").executes(BotsCommands::executeMarkedPlayer))
                        .then(Commands.literal("executionFail").executes(BotsCommands::executeMarkedPlayerFail))
                        .then(Commands.literal("callForExile")
                                .then(Commands.argument("seat", IntegerArgumentType.integer(1))
                                        .executes(BotsCommands::executeCallForExile)))
                        .then(Commands.literal("runExileSupport").executes(BotsCommands::executeRunExileSupport))
                        .then(Commands.literal("exileVote")
                                .then(Commands.argument("yes", BoolArgumentType.bool())
                                        .executes(BotsCommands::executeExileVote)))
                        .then(Commands.literal("resolveExile").executes(BotsCommands::executeResolveExile))
                        .then(Commands.literal("cancelExileVote").executes(BotsCommands::executeCancelExileVote))
                        .then(Commands.literal("setExileCount")
                                .then(Commands.argument("count", IntegerArgumentType.integer(0, 99))
                                        .executes(BotsCommands::executeSetExileCount)))
                        .then(Commands.literal("clearExileCount").executes(BotsCommands::executeClearExileCount))
                        .then(Commands.literal("exileStatus").executes(BotsCommands::executeExileStatus))
                        .then(Commands.literal("resetExile").executes(BotsCommands::executeResetExile))
                        .then(Commands.literal("gameflowdemo").executes(BotsCommands::executeGameFlowDemo))
                        .then(Commands.literal("timer")
                                .then(Commands.literal("start")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                                .executes(BotsCommands::executeTimerStart)))
                                .then(Commands.literal("pause").executes(BotsCommands::executeTimerPause))
                                .then(Commands.literal("resume").executes(BotsCommands::executeTimerResume))
                                .then(Commands.literal("stop").executes(BotsCommands::executeTimerStop))))
        );
        BloodOnTheSharktower.LOGGER.info("Registered /bots command root (private BOTB -> BOTS port).");
    }

    private static int executeStatus(CommandContext<CommandSourceStack> context) {
        String version = FabricLoader.getInstance()
                .getModContainer(BloodOnTheSharktower.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        GamePhase phase = currentPhase();
        RoleModelDiagnostics.Result roleModel = RoleModelDiagnostics.run();
        Script serverScript = ServerState.currentScript;
        String serverScriptName = serverScript == null ? "none" : serverScript.name();
        int serverScriptRoleCount = serverScript == null ? 0 : serverScript.allRoles().size();
        int serverSeats = StateBroadcaster.seatedPlayerCount();
        int serverDead = StateBroadcaster.deadPlayerCount();
        int serverGrimoire = ServerState.PLAYER_ROLES.size();

        send(context, "Blood on the Sharktower");
        send(context, "Version: " + version);
        send(context, "Command root: /bots");
        send(context, "Server systems: ONLINE");
        send(context, "Voice chat integration: "
                + (VoicechatIntegrationState.isInitialized() ? "API ONLINE" : "API WAITING")
                + ", server=" + (VoicechatIntegrationState.isServerStarted() ? "ONLINE" : "WAITING")
                + ", localClient=" + (VoicechatIntegrationState.isClientConnected() ? "CONNECTED" : "NOT CONNECTED"));
        send(context, NightChatManager.statusLine());
        send(context, "BOTB port source: " + LegacyPortInfo.SOURCE_VERSION);
        send(context, "Server phase: " + phase);
        send(context, "Game flow: nominations=" + (DaytimeState.areNominationsOpen() ? "OPEN" : "CLOSED")
                + ", nominee=" + seatLabel(DaytimeState.getCurrentNominee())
                + ", marked=" + seatLabel(DaytimeState.getMarkedForExecution())
                + " (" + DaytimeState.getVotesForMarkedPlayer() + " votes)");
        send(context, "Vote state: " + (DaytimeState.isVoteInProgress() ? "ACTIVE" : "IDLE")
                + ", current=" + VotingManager.effectiveVoteCount()
                + ", threshold=" + VotingManager.calculateThreshold(VotingManager.alivePlayerCount())
                + ", ghostVotesUsed=" + DaytimeState.getGhostVoteMap().values().stream().filter(Boolean.TRUE::equals).count());
        send(context, "Exile state: target=" + seatLabel(DaytimeState.getCurrentExileTarget())
                + ", support=" + (DaytimeState.isExileSupportInProgress() ? "ACTIVE" : "IDLE")
                + " (" + DaytimeState.getLockedExileSupportCount() + " yes)");
        send(context, "Timer: " + (TimerManager.isActive() ? (TimerManager.isPaused() ? "PAUSED" : "RUNNING") : "IDLE")
                + " " + TimerManager.getRemainingSeconds() + "/" + TimerManager.getTotalSeconds() + "s");
        send(context, "Role model: " + (roleModel.ok() ? "ONLINE" : "FAILED")
                + " (" + roleModel.enumEntries() + " enum / "
                + roleModel.selectableRoles() + " selectable)");
        send(context, "Script parser: " + (roleModel.ok() ? "ONLINE" : "FAILED")
                + " (" + roleModel.parsedSmokeRoles() + " smoke-test roles)");
        send(context, "Server script: " + serverScriptName + " (" + serverScriptRoleCount + " roles)");
        send(context, "Server core state: seats=" + serverSeats
                + ", dead=" + serverDead
                + ", grimoire=" + serverGrimoire
                + ", reminders=" + StorytellerState.REMINDERS.size());
        send(context, "Network sync: " + (NetworkDiagnostics.ackedClientCount() > 0 ? "ONLINE" : "WAITING")
                + " (" + NetworkDiagnostics.ackedClientCount() + " client ack"
                + (NetworkDiagnostics.ackedClientCount() == 1 ? "" : "s") + ")");

        NetworkDiagnostics.latestAck().ifPresent(ack -> {
            boolean scriptMatches = serverScriptName.equals(ack.scriptName())
                    && serverScriptRoleCount == ack.scriptRoleCount();
            boolean coreMatches = serverSeats == ack.seatCount()
                    && serverDead == ack.deadCount()
                    && serverGrimoire == ack.grimoirePlayerCount();

            send(context, "Client script: " + ack.scriptName() + " (" + ack.scriptRoleCount() + " roles)");
            send(context, "Script sync: " + (scriptMatches ? "ONLINE" : "MISMATCH"));
            send(context, "Client core state: seats=" + ack.seatCount()
                    + ", dead=" + ack.deadCount()
                    + ", grimoire=" + ack.grimoirePlayerCount());
            send(context, "Core state parity: " + (coreMatches ? "ONLINE" : "MISMATCH"));
            send(context, "Game-flow phase sync: " + (phase.name().equals(ack.clientPhase()) ? "ONLINE" : "MISMATCH"));

            PendingRoleAssignment serverAssignment = ServerState.PLAYER_ROLES.get(ack.playerId());
            String serverRoleId = roleId(serverAssignment);
            String serverRoleName = roleName(serverAssignment);
            boolean serverGood = serverAssignment == null || serverAssignment.isFinalGood();
            send(context, "Server role: " + serverRoleName + " (" + alignmentName(serverGood) + ")");

            NetworkDiagnostics.roleAck(ack.playerId()).ifPresentOrElse(roleAck -> {
                String clientRoleName = roleName(roleAck.roleId(), serverScript);
                boolean roleMatches = serverRoleId.equals(roleAck.roleId())
                        && serverGood == roleAck.good();
                send(context, "Client role: " + clientRoleName + " (" + alignmentName(roleAck.good()) + ")");
                send(context, "Role sync: " + (roleMatches ? "ONLINE" : "MISMATCH"));
            }, () -> send(context, "Role sync: WAITING"));

            send(context, "Latest client state: phase=" + ack.clientPhase()
                    + ", script=" + ack.scriptName()
                    + ", roles=" + ack.scriptRoleCount()
                    + ", seats=" + ack.seatCount()
                    + ", dead=" + ack.deadCount()
                    + ", grimoire=" + ack.grimoirePlayerCount()
                    + ", seq=" + ack.sequence());
        });

        send(context, "Player counts: active=" + StateBroadcaster.activePlayerCount()
                + ", travelers=" + StateBroadcaster.travelerCount());
        send(context, "End-game state: " + (ServerState.gameEnded
                ? ("REVEAL — " + ServerState.winningTeam + " WINS")
                : "inactive"));
        send(context, "Setup backend: " + SetupOperations.summary());
        send(context, "Seat positions: homes=" + SeatPositionManager.configuredSeatHomes()
                + ", townSquare=" + SeatPositionManager.configuredTownSquareSeats());
        send(context, "Legacy assets staged: " + LegacyPortInfo.IMPORTED_ASSET_FILE_COUNT + " files");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeVoiceStatus(CommandContext<CommandSourceStack> context) {
        String voicechatVersion = FabricLoader.getInstance()
                .getModContainer("voicechat")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("not detected");
        String apiVersion = FabricLoader.getInstance()
                .getModContainer("voicechat_api")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("not detected");

        send(context, "Blood on the Sharktower - Simple Voice Chat");
        send(context, "Simple Voice Chat mod: " + voicechatVersion);
        send(context, "Voice Chat API: " + apiVersion);
        send(context, "BOTS plugin initialized: " + (VoicechatIntegrationState.isInitialized() ? "YES" : "NO"));
        send(context, "Voice server lifecycle: " + (VoicechatIntegrationState.isServerStarted() ? "ONLINE" : "WAITING"));
        send(context, "Dedicated-server note: client connection state is reported per online player below.");
        send(context, "Audio routing: " + (NightChatManager.isActive()
                ? "BOTS SHARED NIGHT CHAT + TEMP PRIVATE ROOMS"
                : (PhaseOperations.isDay()
                    ? "BOTS SHARED DAY CHAT + AUTOMATIC PRIVATE ZONES"
                    : "VANILLA SIMPLE VOICE CHAT")));
        send(context, NightChatManager.statusLine());
        send(context, DayChatZoneManager.statusLine());
        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            send(context, "Voice " + player.getName().getString() + ": "
                    + NightChatManager.voiceConnectionStatus(player.getUUID()));
        }
        String routingError = NightChatManager.lastRoutingError();
        if (routingError != null) {
            send(context, "Last Night Chat routing error: " + routingError);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSync(CommandContext<CommandSourceStack> context) {
        int playerCount = context.getSource().getServer().getPlayerList().getPlayers().size();
        int sequence = StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        if (playerCount == 0) {
            send(context, "No connected players to sync.");
            return Command.SINGLE_SUCCESS;
        }
        send(context, "Sent BOTS bulk core state to " + playerCount + " player"
                + (playerCount == 1 ? "" : "s") + ". Last sequence: " + sequence);
        send(context, "Run /bots status to verify server/client parity.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTestScript(CommandContext<CommandSourceStack> context) {
        Script script = ScriptNetworkTestData.create();
        ServerState.currentScript = script;
        int sequence = StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Loaded network test script: " + script.name()
                + " (" + script.allRoles().size() + " roles). Sequence: " + sequence);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTestRole(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "testrole");
        if (player == null) return 0;

        String requested = StringArgumentType.getString(context, "role");
        if (requested.equalsIgnoreCase("none") || requested.equalsIgnoreCase("clear")) {
            ServerState.PLAYER_ROLES.remove(player.getUUID());
            ServerState.PLAYER_PERCEIVED_ROLES.remove(player.getUUID());
            int sequence = StateBroadcaster.sendCurrentStateTo(player);
            send(context, "Cleared your test role. Sequence: " + sequence);
            return Command.SINGLE_SUCCESS;
        }

        Role role = Role.findById(requested);
        if (role == null || role == Role.NO_ROLE) {
            send(context, "Unknown role '" + requested + "'. Try empath or imp.");
            return 0;
        }

        PendingRoleAssignment assignment = new PendingRoleAssignment(role, AlignmentOverride.DEFAULT);
        ServerState.PLAYER_ROLES.put(player.getUUID(), assignment);
        int sequence = StateBroadcaster.sendCurrentStateTo(player);
        send(context, "Assigned test role: " + assignment.getDisplayName()
                + " (" + alignmentName(assignment.isFinalGood()) + "). Sequence: " + sequence);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTestSeat(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "testseat");
        if (player == null) return 0;

        int seat = IntegerArgumentType.getInteger(context, "seat");
        ServerState.PLAYER_SEAT_NUMBERS.put(player.getUUID(), seat);
        StorytellerState.PENDING_SEAT_NUMBERS.put(player.getUUID(), seat);
        StorytellerState.nextSeatNumber = Math.max(StorytellerState.nextSeatNumber, seat + 1);
        ServerState.PLAYER_DEATH_STATUS.putIfAbsent(player.getUUID(), false);
        int sequence = StateBroadcaster.sendCurrentStateTo(player);
        send(context, "Assigned test seat " + seat + ". Sequence: " + sequence);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTestSeats(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "testseats");
        if (player == null) return 0;

        int count = IntegerArgumentType.getInteger(context, "count");
        clearSyntheticTestSeats();

        // Use the real executing player as seat 1 so personal role/HUD sync still
        // has a genuine connected player, then fill the rest with deterministic
        // synthetic UUIDs that exist only in BOTS state maps.
        ServerState.PLAYER_SEAT_NUMBERS.put(player.getUUID(), 1);
        StorytellerState.PENDING_SEAT_NUMBERS.put(player.getUUID(), 1);
        ServerState.PLAYER_DEATH_STATUS.put(player.getUUID(), false);
        ServerState.PLAYER_ROLES.put(player.getUUID(), new PendingRoleAssignment(
                TEST_GRIMOIRE_ROLES.get(0), AlignmentOverride.DEFAULT));

        for (int seat = 2; seat <= count; seat++) {
            UUID uuid = syntheticTestUuid(seat);
            SYNTHETIC_TEST_PLAYERS.add(uuid);
            ServerState.PLAYER_SEAT_NUMBERS.put(uuid, seat);
            StorytellerState.PENDING_SEAT_NUMBERS.put(uuid, seat);
            ServerState.PLAYER_DEATH_STATUS.put(uuid, false);

            Role role = TEST_GRIMOIRE_ROLES.get((seat - 1) % TEST_GRIMOIRE_ROLES.size());
            ServerState.PLAYER_ROLES.put(uuid, new PendingRoleAssignment(role, AlignmentOverride.DEFAULT));
        }

        StorytellerState.nextSeatNumber = Math.max(StorytellerState.nextSeatNumber, count + 1);
        int sequence = StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Generated " + count + " Grimoire test seats. Sequence: " + sequence);
        send(context, "Open the Grimoire with R. Use /bots testseats clear to remove synthetic seats.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeClearTestSeats(CommandContext<CommandSourceStack> context) {
        int removed = clearSyntheticTestSeats();
        int sequence = StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Removed " + removed + " synthetic Grimoire test seat" + (removed == 1 ? "" : "s")
                + ". Your real player state was kept. Sequence: " + sequence);
        return Command.SINGLE_SUCCESS;
    }

    private static int clearSyntheticTestSeats() {
        int removed = 0;
        for (UUID uuid : new HashSet<>(SYNTHETIC_TEST_PLAYERS)) {
            if (ServerState.PLAYER_SEAT_NUMBERS.remove(uuid) != null) removed++;
            StorytellerState.PENDING_SEAT_NUMBERS.remove(uuid);
            ServerState.PLAYER_DEATH_STATUS.remove(uuid);
            ServerState.PLAYER_ROLES.remove(uuid);
            ServerState.PLAYER_PERCEIVED_ROLES.remove(uuid);
            StorytellerState.PENDING_PERCEIVED_ROLES.remove(uuid);
            StorytellerState.REMINDERS.remove(uuid);
        }
        SYNTHETIC_TEST_PLAYERS.clear();
        return removed;
    }

    private static UUID syntheticTestUuid(int seat) {
        return UUID.nameUUIDFromBytes(("blood-on-the-sharktower:test-seat:" + seat)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static int executeTestDead(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "testdead");
        if (player == null) return 0;

        boolean dead = BoolArgumentType.getBool(context, "dead");
        ServerState.PLAYER_DEATH_STATUS.put(player.getUUID(), dead);
        int sequence = StateBroadcaster.sendCurrentStateTo(player);
        send(context, "Set death state to " + (dead ? "DEAD" : "ALIVE") + ". Sequence: " + sequence);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDusk(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        PhaseOperations.Result result = PhaseOperations.enterNight(context.getSource().getServer());
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeDawn(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        PhaseOperations.Result result = PhaseOperations.enterDay(context.getSource().getServer());
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeTestPhase(CommandContext<CommandSourceStack> context) {
        String requested = StringArgumentType.getString(context, "phase").toLowerCase();
        PhaseOperations.Result result = switch (requested) {
            case "setup" -> PhaseOperations.enterSetupForTest(context.getSource().getServer());
            case "night" -> PhaseOperations.enterNight(context.getSource().getServer());
            case "day" -> PhaseOperations.enterDay(context.getSource().getServer());
            default -> PhaseOperations.Result.fail("Unknown phase '" + requested + "'. Use setup, night, or day.");
        };
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }



    private static int executeDayChatStatus(CommandContext<CommandSourceStack> context) {
        send(context, DayChatZoneManager.statusLine());
        send(context, "Before nominations: crossing a configured entrance moves seated players into that area's private Day Chat.");
        send(context, "They stay private until they cross one of that area's exits. Nominations return everyone to shared Day Chat.");
        send(context, "Mark gates with /bots daychat zone <name> entrance|exit. Repeat to add multiple entrances/exits.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDayChatZones(CommandContext<CommandSourceStack> context) {
        send(context, "Daytime private chat entrances/exits:");
        for (String line : DayChatZoneManager.zoneLines()) send(context, " - " + line);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDayChatZoneEntrance(CommandContext<CommandSourceStack> context) {
        return executeDayChatZoneTrigger(context, true);
    }

    private static int executeDayChatZoneExit(CommandContext<CommandSourceStack> context) {
        return executeDayChatZoneTrigger(context, false);
    }

    private static int executeDayChatZoneTrigger(CommandContext<CommandSourceStack> context, boolean entrance) {
        ServerPlayer player = requirePlayer(context, "daychat zone");
        if (player == null) return 0;
        claimStoryteller(context);
        String name = StringArgumentType.getString(context, "name");
        send(context, entrance
                ? DayChatZoneManager.addEntrance(name, player)
                : DayChatZoneManager.addExit(name, player));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDayChatZoneClearEntrances(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        String name = StringArgumentType.getString(context, "name");
        send(context, DayChatZoneManager.clearEntrances(name));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDayChatZoneClearExits(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        String name = StringArgumentType.getString(context, "name");
        send(context, DayChatZoneManager.clearExits(name));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDayChatZoneRemove(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        String name = StringArgumentType.getString(context, "name");
        send(context, DayChatZoneManager.removeZone(name));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDayChatClearZones(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        send(context, DayChatZoneManager.clearZones());
        return Command.SINGLE_SUCCESS;
    }

    private static int executeNightChatStatus(CommandContext<CommandSourceStack> context) {
        send(context, NightChatManager.statusLine());
        send(context, "Routing mode: all seated players (living or dead) plus Storytellers share Night Chat; temporary private rooms are invitation-only.");
        send(context, "Private Storyteller chats work at any phase: /bots private request | /bots private invite <seat>");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeNightChatWhoAmI(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "nightchat whoami");
        if (player == null) return 0;
        send(context, NightChatManager.participantStatus(player.getUUID()));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeNightChatStart(CommandContext<CommandSourceStack> context) {
        NightChatManager.Result result = NightChatManager.start();
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeNightChatStop(CommandContext<CommandSourceStack> context) {
        NightChatManager.Result result = NightChatManager.stop();
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeNightChatResync(CommandContext<CommandSourceStack> context) {
        NightChatManager.Result result = NightChatManager.resync();
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executePrivateInvite(CommandContext<CommandSourceStack> context) {
        ServerPlayer storyteller = requirePlayer(context, "private invite");
        if (storyteller == null) return 0;
        int seat = IntegerArgumentType.getInteger(context, "seat");
        return sendPrivateInvite(context, storyteller, seat, false);
    }

    private static int sendPrivateInvite(
            CommandContext<CommandSourceStack> context,
            ServerPlayer storyteller,
            int seat,
            boolean houseBound
    ) {
        if (!StorytellerState.isStoryteller(storyteller.getUUID())) {
            send(context, "Claim Storyteller control first with /bots storyteller claim.");
            return 0;
        }

        UUID targetId = SetupOperations.playerBySeat(seat);
        if (targetId == null) {
            send(context, "Seat " + seat + " is empty.");
            return 0;
        }
        ServerPlayer target = connectedPlayerByUuid(context, targetId);
        if (target == null) {
            send(context, "Seat " + seat + " is not currently connected.");
            return 0;
        }

        NightChatManager.InviteResult result = houseBound
                ? NightChatManager.createStorytellerHouseInvite(storyteller.getUUID(), targetId, seat)
                : NightChatManager.createStorytellerInvite(storyteller.getUUID(), targetId);
        if (!result.ok()) {
            send(context, result.message());
            return 0;
        }

        String acceptCommand = "/bots private accept " + result.token();
        Component message = Component.literal("The Storyteller wants to speak with you privately. ")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(privateActionButton("[JOIN PRIVATE CHAT]", acceptCommand,
                        "Join the Storyteller's private voice room"));
        target.sendSystemMessage(message);
        send(context, "Private chat invitation sent to " + target.getName().getString()
                + " (Seat " + seat + "). It expires in 60 seconds.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executePrivateRequest(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "private request");
        if (player == null) return 0;

        int onlineStorytellers = 0;
        for (UUID storytellerId : StorytellerState.STORYTELLERS) {
            if (connectedPlayerByUuid(context, storytellerId) != null) onlineStorytellers++;
        }
        if (onlineStorytellers == 0) {
            send(context, "No Storyteller is currently online to receive the request.");
            return 0;
        }

        NightChatManager.InviteResult result = NightChatManager.createPlayerRequest(player.getUUID());
        if (!result.ok()) {
            send(context, result.message());
            return 0;
        }

        int seat = ServerState.PLAYER_SEAT_NUMBERS.getOrDefault(player.getUUID(), 0);
        String acceptCommand = "/bots private accept " + result.token();
        Component request = Component.literal(player.getName().getString()
                        + (seat > 0 ? " (Seat " + seat + ")" : "")
                        + " is requesting a private conversation. ")
                .withStyle(ChatFormatting.AQUA)
                .append(privateActionButton("[ACCEPT]", acceptCommand,
                        "Move you and this player into a private voice room"));

        int delivered = 0;
        for (UUID storytellerId : StorytellerState.STORYTELLERS) {
            ServerPlayer storyteller = connectedPlayerByUuid(context, storytellerId);
            if (storyteller == null) continue;
            storyteller.sendSystemMessage(request);
            delivered++;
        }

        send(context, "Private chat request sent to " + delivered
                + " Storyteller(s). The first acceptance wins; request expires in 60 seconds.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executePrivateAccept(CommandContext<CommandSourceStack> context) {
        ServerPlayer accepter = requirePlayer(context, "private accept");
        if (accepter == null) return 0;

        UUID token;
        try {
            token = UUID.fromString(StringArgumentType.getString(context, "token"));
        } catch (IllegalArgumentException ex) {
            send(context, "Invalid private chat invitation token.");
            return 0;
        }

        NightChatManager.Result result = NightChatManager.acceptInvite(token, accepter.getUUID());
        send(context, result.message());
        if (!result.ok()) return 0;

        UUID partnerId = NightChatManager.privatePartner(accepter.getUUID());
        ServerPlayer partner = connectedPlayerByUuid(context, partnerId);
        if (partner != null) {
            partner.sendSystemMessage(Component.literal(
                    "Private voice chat connected with " + accepter.getName().getString()
                            + ". Use /bots private leave when finished.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executePrivateLeave(CommandContext<CommandSourceStack> context) {
        ServerPlayer participant = requirePlayer(context, "private leave");
        if (participant == null) return 0;

        UUID partnerId = NightChatManager.privatePartner(participant.getUUID());
        boolean storytellerLeaving = NightChatManager.isStorytellerAttachedToPrivate(participant.getUUID());
        NightChatManager.Result result = NightChatManager.leavePrivate(participant.getUUID());
        send(context, result.message());
        if (!result.ok()) return 0;

        ServerPlayer partner = connectedPlayerByUuid(context, partnerId);
        if (partner != null) {
            String message = storytellerLeaving
                    ? "The Storyteller left your private voice chat. You remain private until you choose to leave."
                    : participant.getName().getString() + " ended the private voice chat.";
            partner.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeStorytellerClaim(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "storyteller claim");
        if (player == null) return 0;
        StorytellerState.claimStoryteller(player.getUUID());
        AutoSeatManager.removeStorytellerSeat(context.getSource().getServer(), player.getUUID());
        NightChatManager.onStorytellerStatusChanged(player.getUUID());
        StateBroadcaster.sendCurrentStateTo(player);
        StateBroadcaster.broadcastPlayerDirectory(context.getSource().getServer());
        send(context, "Storyteller control claimed. Pending setup state is now visible in your Grimoire.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeStorytellerRelease(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "storyteller release");
        if (player == null) return 0;
        StorytellerState.releaseStoryteller(player.getUUID());
        AutoSeatManager.seatReleasedStoryteller(context.getSource().getServer(), player);
        NightChatManager.onStorytellerStatusChanged(player.getUUID());
        StateBroadcaster.sendCurrentStateTo(player);
        StateBroadcaster.broadcastPlayerDirectory(context.getSource().getServer());
        send(context, "Storyteller control released.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeShareGrimoire(CommandContext<CommandSourceStack> context) {
        ServerPlayer storyteller = requirePlayer(context, "sharegrimoire");
        if (storyteller == null) return 0;

        UUID targetId;
        try {
            targetId = UUID.fromString(StringArgumentType.getString(context, "player"));
        } catch (IllegalArgumentException ex) {
            send(context, "Invalid Spy/Widow player id.");
            return 0;
        }

        SetupOperations.Result result = StorytellerActionHandler.shareAbilityGrimoire(
                context.getSource().getServer(),
                storyteller,
                targetId
        );
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeSetupStatus(CommandContext<CommandSourceStack> context) {
        send(context, "Setup backend: " + SetupOperations.summary());
        send(context, "Working seats: " + SetupOperations.workingSeats().size()
                + ", working roles: " + SetupOperations.workingRoles().size());
        send(context, "Seat positions: homes=" + SeatPositionManager.configuredSeatHomes()
                + ", townSquare=" + SeatPositionManager.configuredTownSquareSeats());
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSeatPlayer(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        String name = StringArgumentType.getString(context, "player");
        ServerPlayer target = connectedPlayerByName(context, name);
        if (target == null) {
            send(context, "No connected player named '" + name + "'.");
            return 0;
        }
        SetupOperations.Result result = SetupOperations.assignSeat(
                target.getUUID(), IntegerArgumentType.getInteger(context, "seat"));
        return finishSetupOperation(context, result, true);
    }

    private static int executeUnseatPlayer(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        String name = StringArgumentType.getString(context, "player");
        ServerPlayer target = connectedPlayerByName(context, name);
        if (target == null) {
            send(context, "No connected player named '" + name + "'.");
            return 0;
        }
        return finishSetupOperation(context, SetupOperations.unseat(target.getUUID()), true);
    }

    private static int executeSeatAll(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        List<UUID> players = context.getSource().getServer().getPlayerList().getPlayers().stream()
                .filter(player -> !StorytellerState.isStoryteller(player.getUUID()))
                .map(ServerPlayer::getUUID)
                .toList();
        return finishSetupOperation(context, SetupOperations.seatAll(players), true);
    }

    private static int executeAssignRole(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        int seat = IntegerArgumentType.getInteger(context, "seat");
        String role = StringArgumentType.getString(context, "role");
        return finishSetupOperation(context, SetupOperations.assignRole(seat, role), true);
    }

    private static int executeClearRole(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        int seat = IntegerArgumentType.getInteger(context, "seat");
        return finishSetupOperation(context, SetupOperations.clearRole(seat), true);
    }

    private static int executeAlignment(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        int seat = IntegerArgumentType.getInteger(context, "seat");
        String value = StringArgumentType.getString(context, "alignment").toLowerCase();
        AlignmentOverride override = switch (value) {
            case "default", "normal" -> AlignmentOverride.DEFAULT;
            case "good", "force_good" -> AlignmentOverride.FORCE_GOOD;
            case "evil", "bad", "force_bad" -> AlignmentOverride.FORCE_BAD;
            default -> null;
        };
        if (override == null) {
            send(context, "Alignment must be default, good, or evil.");
            return 0;
        }
        return finishSetupOperation(context, SetupOperations.setAlignment(seat, override), true);
    }

    private static int executeSwapRoles(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        return finishSetupOperation(context, SetupOperations.swapRoles(
                IntegerArgumentType.getInteger(context, "seatA"),
                IntegerArgumentType.getInteger(context, "seatB")
        ), true);
    }

    private static int executeSwapSeats(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        return finishSetupOperation(context, SetupOperations.swapSeats(
                IntegerArgumentType.getInteger(context, "seatA"),
                IntegerArgumentType.getInteger(context, "seatB")
        ), true);
    }

    private static int executeShuffleRoles(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        return finishSetupOperation(context, SetupOperations.shuffleRoles(), true);
    }

    private static int executeShuffleSeats(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        return finishSetupOperation(context, SetupOperations.shuffleSeats(), true);
    }

    private static int executeRandomizeRoles(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        return finishSetupOperation(context, SetupOperations.randomizeRoles(), true);
    }

    private static int executeRandomizeBluffs(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        return finishSetupOperation(context, SetupOperations.randomizeBluffs(), true);
    }

    private static int executeClearBluffs(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        return finishSetupOperation(context, SetupOperations.clearBluffs(), true);
    }

    private static int executeAddBluff(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        return finishSetupOperation(context, SetupOperations.addBluff(
                StringArgumentType.getString(context, "role")
        ), true);
    }

    private static int executeAddReminder(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        return finishSetupOperation(context, SetupOperations.addReminder(
                IntegerArgumentType.getInteger(context, "seat"),
                StringArgumentType.getString(context, "text")
        ), true);
    }

    private static int executeClearReminders(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        return finishSetupOperation(context, SetupOperations.clearReminders(
                IntegerArgumentType.getInteger(context, "seat")
        ), true);
    }

    private static int executeClearAllReminders(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        return finishSetupOperation(context, SetupOperations.clearAllReminders(), true);
    }

    private static int executeSendRoles(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        SetupOperations.Result result = SetupOperations.commitSetup(context.getSource().getServer());
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeDiscardSetup(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        SetupOperations.Result result = SetupOperations.discardPending(context.getSource().getServer());
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeResetGame(CommandContext<CommandSourceStack> context) {
        SetupOperations.Result result = SetupOperations.resetGame(context.getSource().getServer(), false);
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeResetGameHard(CommandContext<CommandSourceStack> context) {
        SetupOperations.Result result = SetupOperations.resetGame(context.getSource().getServer(), true);
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeGameComplete(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        SetupOperations.Result result = SetupOperations.completeGame(context.getSource().getServer());
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeEndGameGood(CommandContext<CommandSourceStack> context) {
        return executeEndGame(context, "GOOD");
    }

    private static int executeEndGameEvil(CommandContext<CommandSourceStack> context) {
        return executeEndGame(context, "EVIL");
    }

    private static int executeEndGame(CommandContext<CommandSourceStack> context, String winner) {
        claimStoryteller(context);
        SetupOperations.Result result = SetupOperations.beginEndGame(context.getSource().getServer(), winner);
        send(context, result.message());
        if (result.ok()) {
            ChatFormatting colour = "GOOD".equals(winner) ? ChatFormatting.AQUA : ChatFormatting.RED;
            Component message = Component.literal("GAME OVER — " + winner + " WINS")
                    .withStyle(colour, ChatFormatting.BOLD);
            Component reveal = Component.literal("The Final Grimoire is now revealed. Press your Grimoire key to view it.")
                    .withStyle(ChatFormatting.GOLD);
            for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                player.sendSystemMessage(message);
                player.sendSystemMessage(reveal);
            }
        }
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeEndGameCancel(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        SetupOperations.Result result = SetupOperations.cancelEndGame(context.getSource().getServer());
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeResetForNextGame(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        SetupOperations.Result result = SetupOperations.completeGame(context.getSource().getServer());
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeSnapshotStatus(CommandContext<CommandSourceStack> context) {
        send(context, "Match snapshot: " + MatchSnapshotManager.status());
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSnapshotCorner1(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "snapshot corner1");
        if (player == null) return 0;
        claimStoryteller(context);
        MatchSnapshotManager.Result result = MatchSnapshotManager.setCorner1(context.getSource().getServer(), player);
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeSnapshotCorner2(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "snapshot corner2");
        if (player == null) return 0;
        claimStoryteller(context);
        MatchSnapshotManager.Result result = MatchSnapshotManager.setCorner2(context.getSource().getServer(), player);
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeSnapshotClearRegion(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        MatchSnapshotManager.Result result = MatchSnapshotManager.clearRegion();
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeSnapshotCapture(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        MatchSnapshotManager.Result result = MatchSnapshotManager.captureAtGameStart(context.getSource().getServer());
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeSnapshotRestore(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        SetupOperations.Result result = SetupOperations.completeGame(context.getSource().getServer());
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeSnapshotRestorePrevious(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        SetupOperations.Result result = SetupOperations.restorePreviousGameSnapshot(context.getSource().getServer());
        send(context, result.message());
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int executeSetSeatHome(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "setSeatHome");
        if (player == null) return 0;
        claimStoryteller(context);
        int seat = IntegerArgumentType.getInteger(context, "seat");
        SeatPositionManager.setSeatHome(seat, player);
        send(context, "Stored current position as seat " + seat + " home.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSetTownSquareSeat(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "setTownSquareSeat");
        if (player == null) return 0;
        claimStoryteller(context);
        int seat = IntegerArgumentType.getInteger(context, "seat");
        SeatPositionManager.setTownSquareSeat(seat, player);
        send(context, "Stored current position as town-square seat " + seat + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSetClockCenter(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "setClockCenter");
        if (player == null) return 0;
        claimStoryteller(context);
        SeatPositionManager.setClockCenter(player);
        StateBroadcaster.broadcastVoteState(context.getSource().getServer());
        send(context, "Stored current position as the world clock-hand center.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSetClockHandScale(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        double scale = DoubleArgumentType.getDouble(context, "scale");
        SeatPositionManager.setClockHandScale(scale);
        StateBroadcaster.broadcastVoteState(context.getSource().getServer());
        send(context, String.format(java.util.Locale.ROOT, "Clock-hand scale set to %.2f.", SeatPositionManager.clockHandScale()));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSetVoteSpeed(CommandContext<CommandSourceStack> context) {
        claimStoryteller(context);
        if (DaytimeState.isVoteInProgress()) {
            send(context, "Vote speed cannot be changed while a vote is running.");
            return 0;
        }
        double seconds = DoubleArgumentType.getDouble(context, "seconds");
        int ticks = (int) Math.round(seconds * 20.0D);
        VotePresentationSettings.setStepTicks(ticks);
        StateBroadcaster.broadcastVoteState(context.getSource().getServer());
        send(context, String.format(java.util.Locale.ROOT,
                "Vote speed set to %.2f second(s) per seat.",
                VotePresentationSettings.stepTicks() / 20.0D));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSendToSeats(CommandContext<CommandSourceStack> context) {
        int moved = SeatPositionManager.sendAllToTownSquare(
                context.getSource().getServer(), ServerState.PLAYER_SEAT_NUMBERS);
        send(context, "Sent " + moved + " connected seated player(s) to configured town-square seats.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSendHome(CommandContext<CommandSourceStack> context) {
        int moved = SeatPositionManager.sendAllHome(
                context.getSource().getServer(), ServerState.PLAYER_SEAT_NUMBERS);
        send(context, "Sent " + moved + " connected seated player(s) home.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTeleportToSeat(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "teleportToSeat");
        if (player == null) return 0;
        int seat = IntegerArgumentType.getInteger(context, "seat");
        if (!SeatPositionManager.teleportPlayerToHome(player, seat)) {
            send(context, "Seat " + seat + " has no configured home. Use /bots setSeatHome " + seat + " first.");
            return 0;
        }
        send(context, "Teleported to seat " + seat + " home.");

        // During Night, moving between configured houses is also the Storyteller's
        // private-chat lifecycle. Leaving one house detaches only the Storyteller
        // back to public Night Chat; the previous player remains private until
        // they choose to leave. Arriving at the new house sends the next invite.
        if (NightChatManager.isActive() && StorytellerState.isStoryteller(player.getUUID())) {
            UUID targetId = SetupOperations.playerBySeat(seat);
            UUID currentPartner = NightChatManager.privatePartner(player.getUUID());

            if (currentPartner != null && !currentPartner.equals(targetId)) {
                NightChatManager.Result left = NightChatManager.leavePrivate(player.getUUID());
                if (left.ok()) {
                    ServerPlayer previous = connectedPlayerByUuid(context, currentPartner);
                    if (previous != null) {
                        previous.sendSystemMessage(Component.literal(
                                "The Storyteller left your private voice chat. You remain private until you choose to leave.")
                                .withStyle(ChatFormatting.GRAY));
                    }
                } else {
                    send(context, "Could not close the previous private chat: " + left.message());
                }
            }

            if (NightChatManager.privatePartner(player.getUUID()) == null
                    && targetId != null && !targetId.equals(player.getUUID())
                    && connectedPlayerByUuid(context, targetId) != null) {
                sendPrivateInvite(context, player, seat, true);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int finishSetupOperation(
            CommandContext<CommandSourceStack> context,
            SetupOperations.Result result,
            boolean syncGrimoire
    ) {
        send(context, result.message());
        if (!result.ok()) return 0;
        if (syncGrimoire) StateBroadcaster.broadcastGrimoire(context.getSource().getServer());
        return Command.SINGLE_SUCCESS;
    }

    private static void claimStoryteller(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            StorytellerState.claimStoryteller(player.getUUID());
            AutoSeatManager.removeStorytellerSeat(context.getSource().getServer(), player.getUUID());
            NightChatManager.onStorytellerStatusChanged(player.getUUID());
        }
    }

    private static int executeOpenNominations(CommandContext<CommandSourceStack> context) {
        Set<UUID> players = new HashSet<>(ServerState.PLAYER_SEAT_NUMBERS.keySet());
        if (players.isEmpty()) {
            for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                players.add(player.getUUID());
            }
        }
        DaytimeState.openNominations(players, ServerState.deadPlayers(), java.util.List.of(), java.util.List.of());
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Nominations opened for " + players.size() + " player(s).");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeCloseNominations(CommandContext<CommandSourceStack> context) {
        DaytimeState.closeNominations();
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Nominations closed.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeNominate(CommandContext<CommandSourceStack> context) {
        ServerPlayer nominator = requirePlayer(context, "nominate");
        if (nominator == null) return 0;
        int seat = IntegerArgumentType.getInteger(context, "seat");
        UUID nominee = playerBySeat(seat);
        if (nominee == null) {
            send(context, "No player is assigned to seat " + seat + ".");
            return 0;
        }
        boolean ok = NominationManager.executeNomination(
                context.getSource().getServer(), nominator.getUUID(), nominee, VotingManager.alivePlayerCount()
        );
        if (!ok) {
            send(context, "Nomination rejected by current daytime state.");
            return 0;
        }
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Nomination accepted: " + seatLabel(nominator.getUUID()) + " -> seat " + seat
                + ". Votes required: " + currentVotesRequired());
        return Command.SINGLE_SUCCESS;
    }

    private static int executeToggleHand(CommandContext<CommandSourceStack> context) {
        return setHandFromCommand(context, null);
    }

    private static int executeRaiseHand(CommandContext<CommandSourceStack> context) {
        return setHandFromCommand(context, true);
    }

    private static int executeLowerHand(CommandContext<CommandSourceStack> context) {
        return setHandFromCommand(context, false);
    }

    private static int executeHandStatus(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "hand status");
        if (player == null) return 0;
        boolean raised = DaytimeState.isHandRaised(player.getUUID());
        boolean exileVoting = DaytimeState.hasActiveExile() || DaytimeState.isExileSupportInProgress();
        int required = exileVoting ? ExileSupportManager.currentThreshold() : VotingManager.currentHandsRequired();
        send(context, "Your " + (exileVoting ? "exile support" : "voting") + " hand is " + (raised ? "RAISED" : "LOWERED")
                + ". Hands raised: " + VotingManager.raisedHandCount()
                + ", hands required: " + required + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int setHandFromCommand(CommandContext<CommandSourceStack> context, Boolean forced) {
        ServerPlayer player = requirePlayer(context, "hand");
        if (player == null) return 0;
        UUID id = player.getUUID();
        if (!ServerState.PLAYER_SEAT_NUMBERS.containsKey(id)) {
            send(context, "You must be seated to raise a voting hand.");
            return 0;
        }
        if (StorytellerState.isStoryteller(id)) {
            send(context, "Storytellers do not raise player voting hands.");
            return 0;
        }
        boolean exileVoting = DaytimeState.hasActiveExile() || DaytimeState.isExileSupportInProgress();
        if (DaytimeState.isExiledTraveler(id)) {
            send(context, "You have been exiled and are no longer participating in votes.");
            return 0;
        }
        if (!DaytimeState.areNominationsOpen() && !DaytimeState.isVoteInProgress() && !exileVoting) {
            send(context, "Voting hands are available during nominations or an exile call.");
            return 0;
        }
        if (!exileVoting && Boolean.TRUE.equals(ServerState.PLAYER_DEATH_STATUS.get(id)) && DaytimeState.hasUsedGhostVote(id)) {
            DaytimeState.setRaisedHand(id, false);
            StateBroadcaster.broadcastVoteState(context.getSource().getServer());
            send(context, "Your ghost vote has already been used.");
            return 0;
        }
        boolean alreadyLocked = exileVoting
                ? DaytimeState.getLockedExileSupportVotes().containsKey(id)
                : DaytimeState.getLockedVotes().containsKey(id);
        if ((DaytimeState.isVoteInProgress() || DaytimeState.isExileSupportInProgress()) && alreadyLocked) {
            send(context, "Your vote has already been counted.");
            return 0;
        }
        boolean next = forced != null ? forced : !DaytimeState.isHandRaised(id);
        DaytimeState.setRaisedHand(id, next);
        StateBroadcaster.broadcastVoteState(context.getSource().getServer());
        int required = exileVoting ? ExileSupportManager.currentThreshold() : VotingManager.currentHandsRequired();
        send(context, (exileVoting ? "Exile support hand " : "Voting hand ") + (next ? "RAISED" : "LOWERED") + ". "
                + VotingManager.raisedHandCount() + " hand(s) raised; " + required + " required.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeRunVote(CommandContext<CommandSourceStack> context) {
        if (!DaytimeState.hasActiveNomination()) {
            send(context, "No active nomination to vote on.");
            return 0;
        }
        VotingManager.startVote(context.getSource().getServer(), ServerState.deadPlayers(), false, false, null, Set.of());
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, String.format(java.util.Locale.ROOT,
                "Clockwise vote started. Each seat locks after %.2f second(s). Required to beat the block: %d.",
                VotePresentationSettings.stepTicks() / 20.0D, currentVotesRequired()));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeVote(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "vote");
        if (player == null) return 0;
        if (!DaytimeState.isVoteInProgress()) {
            send(context, "No vote is currently running.");
            return 0;
        }
        boolean yes = BoolArgumentType.getBool(context, "yes");
        VotingManager.setVote(context.getSource().getServer(), player.getUUID(), yes);
        VotingManager.lockVote(context.getSource().getServer(), player.getUUID());
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Your vote is locked: " + (yes ? "YES" : "NO") + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeResolveVote(CommandContext<CommandSourceStack> context) {
        if (!DaytimeState.isVoteInProgress()) {
            send(context, "No vote is currently running.");
            return 0;
        }
        if (!VotingManager.canResolveVote()) {
            send(context, "The clockwise vote clock has not reached every seat yet.");
            return 0;
        }
        VotingManager.Result result = VotingManager.resolveVote(context.getSource().getServer());
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Vote result: " + result.result() + " — " + result.votes() + " vote(s), threshold " + result.threshold() + ".");
        if (DaytimeState.getMarkedForExecution() != null) {
            send(context, "On the block: " + seatLabel(DaytimeState.getMarkedForExecution())
                    + " with " + DaytimeState.getVotesForMarkedPlayer() + " vote(s).");
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeCancelVote(CommandContext<CommandSourceStack> context) {
        if (!DaytimeState.isVoteInProgress()) {
            send(context, "No vote is currently running.");
            return 0;
        }
        VotingManager.resetVote(context.getSource().getServer());
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Vote cancelled. The current nomination remains active.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeResetVote(CommandContext<CommandSourceStack> context) {
        VotingManager.resetVote(context.getSource().getServer());
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Vote state reset.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeMarkedPlayer(CommandContext<CommandSourceStack> context) {
        UUID marked = DaytimeState.getMarkedForExecution();
        if (marked == null) {
            send(context, "Nobody is currently marked for execution.");
            return 0;
        }
        ExecutionManager.executePlayer(context.getSource().getServer(), marked, false, null);
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Executed " + seatLabel(marked) + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeMarkedPlayerFail(CommandContext<CommandSourceStack> context) {
        UUID marked = DaytimeState.getMarkedForExecution();
        if (marked == null) {
            send(context, "Nobody is currently marked for execution.");
            return 0;
        }
        ExecutionManager.executePlayerFail(context.getSource().getServer(), marked, false, null);
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Execution resolved as SURVIVED/FAILED for " + seatLabel(marked) + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeCallForExile(CommandContext<CommandSourceStack> context) {
        ServerPlayer caller = requirePlayer(context, "callForExile");
        if (caller == null) return 0;
        int seat = IntegerArgumentType.getInteger(context, "seat");
        UUID target = playerBySeat(seat);
        if (target == null) {
            send(context, "No player is assigned to seat " + seat + ".");
            return 0;
        }
        if (!ExileManager.callForExile(context.getSource().getServer(), caller.getUUID(), target, false)) {
            send(context, "Exile call rejected. The target must be an eligible Traveler.");
            return 0;
        }
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Exile called against seat " + seat + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeRunExileSupport(CommandContext<CommandSourceStack> context) {
        if (!DaytimeState.hasActiveExile()) {
            send(context, "No active exile call.");
            return 0;
        }
        if (!ExileSupportManager.startExileSupport(context.getSource().getServer())) {
            send(context, "The exile support vote is already running or cannot start.");
            return 0;
        }
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, String.format(java.util.Locale.ROOT,
                "Exile clock started. Each active player is counted every %.2f second(s); %d support vote(s) required.",
                VotePresentationSettings.stepTicks() / 20.0D, ExileSupportManager.currentThreshold()));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeExileVote(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "exileVote");
        if (player == null) return 0;
        if (!DaytimeState.isExileSupportInProgress()) {
            send(context, "No exile support vote is running.");
            return 0;
        }
        boolean yes = BoolArgumentType.getBool(context, "yes");
        ExileSupportManager.setSupport(context.getSource().getServer(), player.getUUID(), yes);
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Exile support locked: " + (yes ? "YES" : "NO") + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeResolveExile(CommandContext<CommandSourceStack> context) {
        if (!DaytimeState.isExileSupportInProgress()) {
            send(context, "No exile support vote is running.");
            return 0;
        }
        if (!ExileSupportManager.canResolve()) {
            send(context, "The exile clock has not reached every active player yet.");
            return 0;
        }
        ExileSupportManager.Result result = ExileSupportManager.resolve(context.getSource().getServer());
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Exile result: " + (result.exiled() ? "EXILED" : "NOT EXILED")
                + " — support " + result.support() + "/" + result.threshold() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeCancelExileVote(CommandContext<CommandSourceStack> context) {
        if (!ExileSupportManager.cancelSupportVote(context.getSource().getServer())) {
            send(context, "No exile support vote is currently running.");
            return 0;
        }
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Exile vote cancelled. The exile call remains active and may be restarted.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSetExileCount(CommandContext<CommandSourceStack> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        if (!ExileSupportManager.setSupportOverride(context.getSource().getServer(), count)) {
            send(context, "Exile count overrides are available after the exile clock completes.");
            return 0;
        }
        send(context, "Storyteller exile-count override set to " + count + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeClearExileCount(CommandContext<CommandSourceStack> context) {
        if (!DaytimeState.isExileSupportInProgress()) {
            send(context, "No exile support vote is currently running.");
            return 0;
        }
        ExileSupportManager.clearSupportOverride(context.getSource().getServer());
        send(context, "Exile-count override cleared. Locked support: " + ExileSupportManager.lockedSupportCount() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeExileStatus(CommandContext<CommandSourceStack> context) {
        send(context, "Exile: target=" + (DaytimeState.getCurrentExileTarget() == null ? "none" : seatLabel(DaytimeState.getCurrentExileTarget()))
                + ", caller=" + (DaytimeState.getCurrentExileCaller() == null ? "none" : seatLabel(DaytimeState.getCurrentExileCaller()))
                + ", running=" + DaytimeState.isExileSupportInProgress()
                + ", support=" + ExileSupportManager.effectiveSupportCount() + "/" + ExileSupportManager.currentThreshold()
                + ", exiledTravellers=" + DaytimeState.getExiledTravelers().size() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeResetExile(CommandContext<CommandSourceStack> context) {
        ExileManager.resetExile(context.getSource().getServer());
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Exile state reset.");
        return Command.SINGLE_SUCCESS;
    }

    /** One-player smoke test that walks the actual nomination/vote/execution backend. */
    private static int executeGameFlowDemo(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = requirePlayer(context, "gameflowdemo");
        if (player == null) return 0;
        UUID id = player.getUUID();
        ServerState.PLAYER_SEAT_NUMBERS.putIfAbsent(id, 1);
        ServerState.PLAYER_DEATH_STATUS.put(id, false);
        int day = Math.max(1, Math.max(ServerState.currentNight, ServerState.currentDay));
        ServerState.currentNight = day;
        ServerState.currentDay = day;
        ServerState.executionToday = false;
        DaytimeState.hardReset(Set.of(id), Set.of());
        DaytimeState.openNominations(Set.of(id), Set.of(), java.util.List.of(), java.util.List.of());
        NominationManager.executeNomination(context.getSource().getServer(), id, id, 1);
        VotingManager.startVote(context.getSource().getServer(), Set.of(), false, false, null, Set.of());
        VotingManager.setVote(context.getSource().getServer(), id, true);
        VotingManager.lockVote(context.getSource().getServer(), id);
        VotingManager.Result result = VotingManager.resolveVote(context.getSource().getServer());
        StateBroadcaster.broadcastCurrentState(context.getSource().getServer());
        send(context, "Game-flow demo complete: nomination -> YES vote -> " + result.result()
                + ". Run /bots status, then /bots execute to test execution.");
        return Command.SINGLE_SUCCESS;
    }

    private static UUID playerBySeat(int seat) {
        for (var entry : ServerState.PLAYER_SEAT_NUMBERS.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == seat) return entry.getKey();
        }
        return null;
    }

    private static String seatLabel(UUID player) {
        if (player == null) return "none";
        Integer seat = ServerState.PLAYER_SEAT_NUMBERS.get(player);
        return seat == null ? player.toString() : "seat " + seat;
    }

    private static int currentVotesRequired() {
        int base = VotingManager.calculateThreshold(VotingManager.alivePlayerCount());
        return DaytimeState.getMarkedForExecution() == null
                ? base
                : Math.max(base, DaytimeState.getVotesForMarkedPlayer() + 1);
    }

    private static int executeTimerStart(CommandContext<CommandSourceStack> context) {
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        TimerManager.startTimer(context.getSource().getServer(), seconds, false);
        send(context, "Timer started for " + seconds + " seconds.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTimerPause(CommandContext<CommandSourceStack> context) {
        TimerManager.pauseTimer(context.getSource().getServer());
        send(context, "Timer paused.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTimerResume(CommandContext<CommandSourceStack> context) {
        TimerManager.resumeTimer(context.getSource().getServer());
        send(context, "Timer resumed.");
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTimerStop(CommandContext<CommandSourceStack> context) {
        TimerManager.stopTimer(context.getSource().getServer());
        send(context, "Timer stopped.");
        return Command.SINGLE_SUCCESS;
    }

    private static ServerPlayer connectedPlayerByName(CommandContext<CommandSourceStack> context, String name) {
        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase(name)) return player;
        }
        return null;
    }

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> context, String subcommand) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            send(context, "/bots " + subcommand + " must be run by a player.");
        }
        return player;
    }

    private static ServerPlayer connectedPlayerByUuid(CommandContext<CommandSourceStack> context, UUID playerId) {
        if (playerId == null) return null;
        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            if (player.getUUID().equals(playerId)) return player;
        }
        return null;
    }

    private static Component privateActionButton(String label, String command, String hoverText) {
        return Component.literal(label)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText))));
    }

    private static GamePhase currentPhase() {
        return GamePhase.determine(
                ServerState.currentNight,
                ServerState.currentDay,
                DaytimeState.areNominationsOpen(),
                DaytimeState.getCurrentNominee(),
                DaytimeState.getMarkedForExecution(),
                DaytimeState.getCurrentExileTarget(),
                DaytimeState.isExileSupportInProgress()
        );
    }

    private static void send(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    private static String roleId(PendingRoleAssignment assignment) {
        if (assignment == null) return "none";
        if (!assignment.isCustomRole() && assignment.role() == Role.NO_ROLE) return "none";
        return assignment.getRoleId();
    }

    private static String roleName(PendingRoleAssignment assignment) {
        if (assignment == null) return "none";
        if (!assignment.isCustomRole() && assignment.role() == Role.NO_ROLE) return "none";
        return assignment.resolveCustomRole(ServerState.currentScript).getDisplayName();
    }

    private static String roleName(String roleId, Script script) {
        if (roleId == null || roleId.isBlank() || roleId.equalsIgnoreCase("none")) return "none";
        Role role = Role.findById(roleId);
        if (role != null && role != Role.NO_ROLE) return role.getDisplayName();
        if (script != null) {
            return script.getCustomRole(roleId).map(custom -> custom.getDisplayName()).orElse(roleId);
        }
        return roleId;
    }

    private static String alignmentName(boolean good) {
        return good ? "good" : "evil";
    }
}
