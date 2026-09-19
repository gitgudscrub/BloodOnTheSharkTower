package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.UUID;

/** 26.2 port of BOTB SyncDaytimeStateS2CPayload's authoritative data shape. */
public record SyncDaytimeStateS2CPayload(
        Map<UUID, Boolean> canNominate,
        Map<UUID, Boolean> canBeNominated,
        Map<UUID, Boolean> hasUsedGhostVote,
        Map<UUID, Integer> nominationsRemaining,
        UUID currentNominator,
        UUID currentNominee,
        UUID markedForExecution,
        int votesForMarkedPlayer,
        boolean nominationsOpen,
        boolean organGrinderModeActiveToday,
        UUID storytellerMFE,
        int storytellerMFEVotes,
        boolean storytellerCanBeNominated,
        Map<UUID, Boolean> canBeExiled,
        Map<UUID, Boolean> exiledTravelers,
        UUID currentExileCaller,
        UUID currentExileTarget,
        boolean exileSupportInProgress,
        int exileSupportCount,
        boolean voudonModeActive,
        UUID voudonPlayerUuid
) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, "sync_daytime_state");
    public static final Type<SyncDaytimeStateS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDaytimeStateS2CPayload> CODEC = new StreamCodec<>() {
        @Override
        public SyncDaytimeStateS2CPayload decode(RegistryFriendlyByteBuf buf) {
            return new SyncDaytimeStateS2CPayload(
                    StateWire.deathFromJson(buf.readUtf()),
                    StateWire.deathFromJson(buf.readUtf()),
                    StateWire.deathFromJson(buf.readUtf()),
                    StateWire.seatsFromJson(buf.readUtf()),
                    readUuid(buf),
                    readUuid(buf),
                    readUuid(buf),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    readUuid(buf),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    StateWire.deathFromJson(buf.readUtf()),
                    StateWire.deathFromJson(buf.readUtf()),
                    readUuid(buf),
                    readUuid(buf),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    readUuid(buf)
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SyncDaytimeStateS2CPayload value) {
            buf.writeUtf(StateWire.deathToJson(value.canNominate));
            buf.writeUtf(StateWire.deathToJson(value.canBeNominated));
            buf.writeUtf(StateWire.deathToJson(value.hasUsedGhostVote));
            buf.writeUtf(StateWire.seatsToJson(value.nominationsRemaining));
            writeUuid(buf, value.currentNominator);
            writeUuid(buf, value.currentNominee);
            writeUuid(buf, value.markedForExecution);
            buf.writeVarInt(value.votesForMarkedPlayer);
            buf.writeBoolean(value.nominationsOpen);
            buf.writeBoolean(value.organGrinderModeActiveToday);
            writeUuid(buf, value.storytellerMFE);
            buf.writeVarInt(value.storytellerMFEVotes);
            buf.writeBoolean(value.storytellerCanBeNominated);
            buf.writeUtf(StateWire.deathToJson(value.canBeExiled));
            buf.writeUtf(StateWire.deathToJson(value.exiledTravelers));
            writeUuid(buf, value.currentExileCaller);
            writeUuid(buf, value.currentExileTarget);
            buf.writeBoolean(value.exileSupportInProgress);
            buf.writeVarInt(value.exileSupportCount);
            buf.writeBoolean(value.voudonModeActive);
            writeUuid(buf, value.voudonPlayerUuid);
        }
    };

    public SyncDaytimeStateS2CPayload {
        canNominate = Map.copyOf(canNominate);
        canBeNominated = Map.copyOf(canBeNominated);
        hasUsedGhostVote = Map.copyOf(hasUsedGhostVote);
        nominationsRemaining = Map.copyOf(nominationsRemaining);
        canBeExiled = Map.copyOf(canBeExiled);
        exiledTravelers = Map.copyOf(exiledTravelers);
    }

    private static void writeUuid(RegistryFriendlyByteBuf buf, UUID id) {
        buf.writeUtf(id == null ? "" : id.toString());
    }

    private static UUID readUuid(RegistryFriendlyByteBuf buf) {
        String raw = buf.readUtf();
        if (raw.isBlank()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
