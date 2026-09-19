package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.UUID;

/** Vote/hand/clock state broadcast for Sharktower's clockwise nomination flow. */
public record VoteStateUpdateS2CPayload(
        boolean voteInProgress,
        int effectiveVoteCount,
        int threshold,
        int handsRaised,
        Map<UUID, Boolean> raisedHands,
        Map<UUID, Boolean> currentVotes,
        Map<UUID, Boolean> lockedVotes,
        Map<UUID, Boolean> leverStates,
        boolean organGrinderMode,
        boolean exileSupport,
        UUID currentVoter,
        int voteClockIndex,
        int voteClockTotal,
        boolean voteClockComplete,
        boolean countOverrideActive,
        int countOverride,
        String lastVoteResult,
        UUID lastVoteNominee,
        int lastVoteCount,
        int lastVoteThreshold,
        boolean clockCenterAvailable,
        double clockCenterX,
        double clockCenterY,
        double clockCenterZ,
        double clockHandScale,
        int voteStepTicks,
        boolean clockTargetAvailable,
        double clockTargetX,
        double clockTargetY,
        double clockTargetZ,
        boolean clockReferenceAvailable,
        double clockReferenceX,
        double clockReferenceY,
        double clockReferenceZ
) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, "vote_state_update");
    public static final Type<VoteStateUpdateS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, VoteStateUpdateS2CPayload> CODEC = new StreamCodec<>() {
        @Override
        public VoteStateUpdateS2CPayload decode(RegistryFriendlyByteBuf buf) {
            return new VoteStateUpdateS2CPayload(
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    StateWire.deathFromJson(buf.readUtf()),
                    StateWire.deathFromJson(buf.readUtf()),
                    StateWire.deathFromJson(buf.readUtf()),
                    StateWire.deathFromJson(buf.readUtf()),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    readUuid(buf),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    readUuid(buf),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readBoolean(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, VoteStateUpdateS2CPayload value) {
            buf.writeBoolean(value.voteInProgress);
            buf.writeVarInt(value.effectiveVoteCount);
            buf.writeVarInt(value.threshold);
            buf.writeVarInt(value.handsRaised);
            buf.writeUtf(StateWire.deathToJson(value.raisedHands));
            buf.writeUtf(StateWire.deathToJson(value.currentVotes));
            buf.writeUtf(StateWire.deathToJson(value.lockedVotes));
            buf.writeUtf(StateWire.deathToJson(value.leverStates));
            buf.writeBoolean(value.organGrinderMode);
            buf.writeBoolean(value.exileSupport);
            writeUuid(buf, value.currentVoter);
            buf.writeVarInt(value.voteClockIndex);
            buf.writeVarInt(value.voteClockTotal);
            buf.writeBoolean(value.voteClockComplete);
            buf.writeBoolean(value.countOverrideActive);
            buf.writeVarInt(value.countOverride);
            buf.writeUtf(value.lastVoteResult == null ? "NONE" : value.lastVoteResult);
            writeUuid(buf, value.lastVoteNominee);
            buf.writeVarInt(value.lastVoteCount);
            buf.writeVarInt(value.lastVoteThreshold);
            buf.writeBoolean(value.clockCenterAvailable);
            buf.writeDouble(value.clockCenterX);
            buf.writeDouble(value.clockCenterY);
            buf.writeDouble(value.clockCenterZ);
            buf.writeDouble(value.clockHandScale);
            buf.writeVarInt(value.voteStepTicks);
            buf.writeBoolean(value.clockTargetAvailable);
            buf.writeDouble(value.clockTargetX);
            buf.writeDouble(value.clockTargetY);
            buf.writeDouble(value.clockTargetZ);
            buf.writeBoolean(value.clockReferenceAvailable);
            buf.writeDouble(value.clockReferenceX);
            buf.writeDouble(value.clockReferenceY);
            buf.writeDouble(value.clockReferenceZ);
        }
    };

    public VoteStateUpdateS2CPayload {
        raisedHands = Map.copyOf(raisedHands);
        currentVotes = Map.copyOf(currentVotes);
        lockedVotes = Map.copyOf(lockedVotes);
        leverStates = Map.copyOf(leverStates);
        lastVoteResult = lastVoteResult == null || lastVoteResult.isBlank() ? "NONE" : lastVoteResult;
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
