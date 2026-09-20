package com.sharktower.bloodonthesharktower.client.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.hud.GameEndAnimationHUD;
import com.sharktower.bloodonthesharktower.client.hud.ClientTriggeredNightOrder;
import com.sharktower.bloodonthesharktower.client.gui.AssignRolesScreen;
import com.sharktower.bloodonthesharktower.client.gui.GrimoireReturnState;
import com.sharktower.bloodonthesharktower.client.gui.BaseThreeScreen;
import com.sharktower.bloodonthesharktower.client.gui.RoleBagScreen;
import com.sharktower.bloodonthesharktower.networking.NetworkSyncAckC2SPayload;
import com.sharktower.bloodonthesharktower.networking.NetworkSyncProbeS2CPayload;
import com.sharktower.bloodonthesharktower.networking.RoleSyncAckC2SPayload;
import com.sharktower.bloodonthesharktower.networking.SendDeathStatusS2CPayload;
import com.sharktower.bloodonthesharktower.networking.SendGrimoireS2CPayload;
import com.sharktower.bloodonthesharktower.networking.SendRoleS2CPayload;
import com.sharktower.bloodonthesharktower.networking.SendScriptS2CPayload;
import com.sharktower.bloodonthesharktower.networking.SendSeatsS2CPayload;
import com.sharktower.bloodonthesharktower.networking.SyncDayNightS2CPayload;
import com.sharktower.bloodonthesharktower.networking.SyncDaytimeStateS2CPayload;
import com.sharktower.bloodonthesharktower.networking.VoteStateUpdateS2CPayload;
import com.sharktower.bloodonthesharktower.networking.VoiceRouteS2CPayload;
import com.sharktower.bloodonthesharktower.networking.EndGameStateS2CPayload;
import com.sharktower.bloodonthesharktower.networking.TimerStateS2CPayload;
import com.sharktower.bloodonthesharktower.networking.PlayerDirectoryS2CPayload;
import com.sharktower.bloodonthesharktower.networking.TriggeredNightOrderS2CPayload;
import com.sharktower.bloodonthesharktower.networking.StorytellerNightInfoS2CPayload;
import com.sharktower.bloodonthesharktower.networking.AbilityGrimoireS2CPayload;
import com.sharktower.bloodonthesharktower.timer.ClientTimerState;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Client receivers for the bulk-ported core gameplay state family. */
public final class CoreStateReceivers {
    private CoreStateReceivers() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SyncDayNightS2CPayload.TYPE, (payload, context) ->
                ClientState.updateDayNight(payload.night(), payload.day(), payload.executionToday())
        );

        ClientPlayNetworking.registerGlobalReceiver(SendScriptS2CPayload.TYPE, (payload, context) -> {
            ClientState.updateScript(payload.script());
            BloodOnTheSharktower.LOGGER.info(
                    "Client received full script '{}': {} playable roles, {} custom roles, {} travelers",
                    payload.script().name(),
                    payload.script().allRoles().size(),
                    payload.script().customRoles().size(),
                    payload.script().travelers().size()
            );

            if (BaseThreeScreen.consumeOpenGrimoireAfterScriptSync()) {
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> client.gui.setScreen(new AssignRolesScreen()));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(SendRoleS2CPayload.TYPE, (payload, context) -> {
            ClientState.updatePlayerState(
                    payload.assignment(),
                    payload.activePlayerCount(),
                    payload.travelerCount(),
                    payload.silent()
            );

            ClientPlayNetworking.send(new RoleSyncAckC2SPayload(
                    ClientState.displayRoleId(),
                    ClientState.isGood()
            ));

            BloodOnTheSharktower.LOGGER.info(
                    "Client received role assignment '{}': id={}, alignment={}, active={}, travelers={}, silent={}",
                    ClientState.displayRoleName(),
                    ClientState.displayRoleId(),
                    ClientState.isGood() ? "good" : "evil",
                    ClientState.activePlayerCount,
                    ClientState.travelerCount,
                    payload.silent()
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(SendSeatsS2CPayload.TYPE, (payload, context) -> {
            ClientState.updateSeats(payload.seatNumbers());
            BloodOnTheSharktower.LOGGER.info(
                    "Client received seat state for {} players",
                    payload.seatNumbers().size()
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(SendDeathStatusS2CPayload.TYPE, (payload, context) -> {
            ClientState.updateDeathStatus(payload.deadPlayers());
            BloodOnTheSharktower.LOGGER.info(
                    "Client received death state: {} tracked, {} dead",
                    payload.deadPlayers().size(),
                    ClientState.deadPlayerCount()
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(TriggeredNightOrderS2CPayload.TYPE, (payload, context) -> {
            ClientTriggeredNightOrder.update(payload.encoded());
            BloodOnTheSharktower.LOGGER.debug("Client triggered night-order state updated.");
        });

        ClientPlayNetworking.registerGlobalReceiver(StorytellerNightInfoS2CPayload.TYPE, (payload, context) -> {
            ClientState.updateStorytellerNightInfo(
                    payload.lastExecutedRoleName(),
                    payload.demonVotedToday(),
                    payload.minionNominatedToday()
            );
            BloodOnTheSharktower.LOGGER.debug(
                    "Client Storyteller night info updated: executed='{}', demonVoted={}, minionNominated={}",
                    payload.lastExecutedRoleName(), payload.demonVotedToday(), payload.minionNominatedToday()
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(AbilityGrimoireS2CPayload.TYPE, (payload, context) -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                ClientGrimoireEdits.applyAbilityGrimoireSnapshot(
                        payload.roles(),
                        payload.reminders(),
                        payload.demonBluffs()
                );
                // Open the normal personal Grimoire so the Spy/Widow sees the
                // shared information in the same place they keep their own notes.
                client.gui.setScreen(new AssignRolesScreen());
            });
            BloodOnTheSharktower.LOGGER.info(
                    "Client received shared ability Grimoire from role '{}': {} roles, {} seats, {} reminder groups, {} bluffs",
                    payload.sourceRoleId(), payload.roles().size(), payload.seatNumbers().size(),
                    payload.reminders().size(), payload.demonBluffs().size()
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(SendGrimoireS2CPayload.TYPE, (payload, context) -> {
            ClientState.updateGrimoire(
                    payload.roles(),
                    payload.perceivedRoles(),
                    payload.seatNumbers(),
                    payload.reminders(),
                    payload.demonBluffs(),
                    payload.isTargetedSend()
            );
            BloodOnTheSharktower.LOGGER.info(
                    "Client received grimoire snapshot: {} roles, {} perceived roles, {} seats, {} reminder groups, {} bluffs, targeted={}",
                    payload.roles().size(),
                    payload.perceivedRoles().size(),
                    payload.seatNumbers().size(),
                    payload.reminders().size(),
                    payload.demonBluffs().size(),
                    payload.isTargetedSend()
            );

            // Role Bag workflow: after the server has actually shuffled and
            // synchronised the pending assignments, return the Storyteller to
            // the Grimoire so the result is visible immediately.
            boolean roleBagReturn = RoleBagScreen.consumeOpenGrimoireAfterDistributionSync();
            boolean editorReturn = GrimoireReturnState.consumeAfterGrimoireSync();

            if (roleBagReturn) {
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> client.gui.setScreen(new AssignRolesScreen()));
            }
            // editorReturn intentionally performs no immediate setScreen here.
            // GrimoireReturnState will reopen the Grim from END_CLIENT_TICK after
            // the current network/input lifecycle has completely finished.
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncDaytimeStateS2CPayload.TYPE, (payload, context) -> {
            ClientState.updateDaytimeState(
                    payload.canNominate(),
                    payload.canBeNominated(),
                    payload.hasUsedGhostVote(),
                    payload.nominationsRemaining(),
                    payload.currentNominator(),
                    payload.currentNominee(),
                    payload.markedForExecution(),
                    payload.votesForMarkedPlayer(),
                    payload.nominationsOpen(),
                    payload.organGrinderModeActiveToday(),
                    payload.storytellerMFE(),
                    payload.storytellerMFEVotes(),
                    payload.storytellerCanBeNominated(),
                    payload.canBeExiled(),
                    payload.exiledTravelers(),
                    payload.currentExileCaller(),
                    payload.currentExileTarget(),
                    payload.exileSupportInProgress(),
                    payload.exileSupportCount(),
                    payload.voudonModeActive(),
                    payload.voudonPlayerUuid()
            );
            BloodOnTheSharktower.LOGGER.info(
                    "Client received daytime state: open={}, nominee={}, marked={}, exile={}, support={}",
                    payload.nominationsOpen(), payload.currentNominee(), payload.markedForExecution(),
                    payload.currentExileTarget(), payload.exileSupportInProgress()
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(VoteStateUpdateS2CPayload.TYPE, (payload, context) -> {
            ClientState.updateVoteState(
                    payload.voteInProgress(),
                    payload.effectiveVoteCount(),
                    payload.threshold(),
                    payload.handsRaised(),
                    payload.raisedHands(),
                    payload.currentVotes(),
                    payload.lockedVotes(),
                    payload.leverStates(),
                    payload.organGrinderMode(),
                    payload.exileSupport(),
                    payload.currentVoter(),
                    payload.voteClockIndex(),
                    payload.voteClockTotal(),
                    payload.voteClockComplete(),
                    payload.countOverrideActive(),
                    payload.countOverride(),
                    payload.lastVoteResult(),
                    payload.lastVoteNominee(),
                    payload.lastVoteCount(),
                    payload.lastVoteThreshold(),
                    payload.clockCenterAvailable(),
                    payload.clockCenterX(),
                    payload.clockCenterY(),
                    payload.clockCenterZ(),
                    payload.clockHandScale(),
                    payload.voteStepTicks(),
                    payload.clockTargetAvailable(),
                    payload.clockTargetX(),
                    payload.clockTargetY(),
                    payload.clockTargetZ(),
                    payload.clockReferenceAvailable(),
                    payload.clockReferenceX(),
                    payload.clockReferenceY(),
                    payload.clockReferenceZ()
            );
            BloodOnTheSharktower.LOGGER.info(
                    "Client received vote state: active={}, hands={}, votes={}, threshold={}, clock={}/{}, complete={}, exileSupport={}",
                    payload.voteInProgress(), payload.handsRaised(), payload.effectiveVoteCount(), payload.threshold(),
                    payload.voteClockIndex(), payload.voteClockTotal(), payload.voteClockComplete(), payload.exileSupport()
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(PlayerDirectoryS2CPayload.TYPE, (payload, context) -> {
            ClientState.updatePlayerDirectory(payload.names(), payload.connectedPlayers(), payload.storytellers());
            BloodOnTheSharktower.LOGGER.info("Client received player directory: {} connected, {} storytellers",
                    payload.connectedPlayers().size(), payload.storytellers().size());
        });

        ClientPlayNetworking.registerGlobalReceiver(VoiceRouteS2CPayload.TYPE, (payload, context) -> {
            ClientState.updateVoiceRoute(payload.route());
            BloodOnTheSharktower.LOGGER.debug("Client voice route updated: {}", payload.route());
        });

        ClientPlayNetworking.registerGlobalReceiver(EndGameStateS2CPayload.TYPE, (payload, context) -> {
            boolean wasEnding = ClientState.gameEnding;
            ClientState.updateEndGame(payload.gameEnded(), payload.rolesRevealed(), payload.winningTeam());

            if (payload.gameEnded() && payload.rolesRevealed()) {
                if (!wasEnding) GameEndAnimationHUD.start(payload.winningTeam());
                else GameEndAnimationHUD.updateWinner(payload.winningTeam());
            } else {
                GameEndAnimationHUD.reset();
            }

            BloodOnTheSharktower.LOGGER.info(
                    "Client end-game state: ended={}, revealed={}, winner={}",
                    payload.gameEnded(), payload.rolesRevealed(), payload.winningTeam()
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(TimerStateS2CPayload.TYPE, (payload, context) -> {
            ClientTimerState.updateTimerState(
                    payload.isActive(), payload.isPaused(), payload.remainingSeconds(), payload.totalSeconds()
            );

            if (payload.completedNaturally()) {
                Minecraft client = Minecraft.getInstance();
                client.execute(() -> {
                    if (client.player == null) return;
                    client.player.playSound(SoundEvents.BELL_RESONATE, 1.0F, 0.72F);
                    client.player.sendSystemMessage(
                            Component.literal("Please return to Town Square")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    );
                });
            }

            BloodOnTheSharktower.LOGGER.info(
                    "Client timer state: active={}, paused={}, remaining={}/{}, completedNaturally={}",
                    payload.isActive(), payload.isPaused(), payload.remainingSeconds(), payload.totalSeconds(),
                    payload.completedNaturally()
            );
        });

        ClientPlayNetworking.registerGlobalReceiver(NetworkSyncProbeS2CPayload.TYPE, (payload, context) -> {
            String phase = ClientState.phase().name();
            String scriptName = ClientState.displayScriptName();
            int scriptRoleCount = ClientState.scriptRoleCount();

            ClientPlayNetworking.send(new NetworkSyncAckC2SPayload(
                    payload.sequence(),
                    phase,
                    scriptName,
                    scriptRoleCount,
                    ClientState.seatedPlayerCount(),
                    ClientState.deadPlayerCount(),
                    ClientState.grimoirePlayerCount()
            ));

            BloodOnTheSharktower.LOGGER.info(
                    "Client acknowledged bulk core sync {}: phase={}, script={}, roles={}, seats={}, dead={}, grimoire={}",
                    payload.sequence(),
                    phase,
                    scriptName,
                    scriptRoleCount,
                    ClientState.seatedPlayerCount(),
                    ClientState.deadPlayerCount(),
                    ClientState.grimoirePlayerCount()
            );
        });
    }
}
