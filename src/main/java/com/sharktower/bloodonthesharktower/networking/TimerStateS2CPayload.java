package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 26.3 timer state + one-shot natural-completion cue. */
public record TimerStateS2CPayload(
        boolean isActive,
        boolean isPaused,
        int remainingSeconds,
        int totalSeconds,
        boolean completedNaturally
) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, "timer_state");
    public static final Type<TimerStateS2CPayload> TYPE = new Type<>(ID_VALUE);
    public static final StreamCodec<RegistryFriendlyByteBuf, TimerStateS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TimerStateS2CPayload::isActive,
            ByteBufCodecs.BOOL, TimerStateS2CPayload::isPaused,
            ByteBufCodecs.VAR_INT, TimerStateS2CPayload::remainingSeconds,
            ByteBufCodecs.VAR_INT, TimerStateS2CPayload::totalSeconds,
            ByteBufCodecs.BOOL, TimerStateS2CPayload::completedNaturally,
            TimerStateS2CPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
