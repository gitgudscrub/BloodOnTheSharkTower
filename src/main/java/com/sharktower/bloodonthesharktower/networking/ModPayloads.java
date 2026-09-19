package com.sharktower.bloodonthesharktower.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Bulk 26.2 port of the original BOTB ModPayloads core gameplay registry. */
public final class ModPayloads {
    private ModPayloads() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(SyncDayNightS2CPayload.TYPE, SyncDayNightS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SendScriptS2CPayload.TYPE, SendScriptS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SendRoleS2CPayload.TYPE, SendRoleS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SendSeatsS2CPayload.TYPE, SendSeatsS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SendDeathStatusS2CPayload.TYPE, SendDeathStatusS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SendGrimoireS2CPayload.TYPE, SendGrimoireS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NetworkSyncProbeS2CPayload.TYPE, NetworkSyncProbeS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncDaytimeStateS2CPayload.TYPE, SyncDaytimeStateS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VoteStateUpdateS2CPayload.TYPE, VoteStateUpdateS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TimerStateS2CPayload.TYPE, TimerStateS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayerDirectoryS2CPayload.TYPE, PlayerDirectoryS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VoiceRouteS2CPayload.TYPE, VoiceRouteS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EndGameStateS2CPayload.TYPE, EndGameStateS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TriggeredNightOrderS2CPayload.TYPE, TriggeredNightOrderS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StorytellerNightInfoS2CPayload.TYPE, StorytellerNightInfoS2CPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AbilityGrimoireS2CPayload.TYPE, AbilityGrimoireS2CPayload.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(NetworkSyncAckC2SPayload.TYPE, NetworkSyncAckC2SPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RoleSyncAckC2SPayload.TYPE, RoleSyncAckC2SPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StorytellerActionC2SPayload.TYPE, StorytellerActionC2SPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PlayerActionC2SPayload.TYPE, PlayerActionC2SPayload.CODEC);
    }
}
