package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 26.2 port of the original BOTB SyncDayNightS2CPayload.
 *
 * The payload shape is intentionally kept the same as the 1.21.1 mod:
 * night, day, executionToday.
 */
public record SyncDayNightS2CPayload(int night, int day, boolean executionToday)
        implements CustomPacketPayload {

    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "sync_day_night"
    );

    public static final Type<SyncDayNightS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDayNightS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SyncDayNightS2CPayload::night,
            ByteBufCodecs.VAR_INT,
            SyncDayNightS2CPayload::day,
            ByteBufCodecs.BOOL,
            SyncDayNightS2CPayload::executionToday,
            SyncDayNightS2CPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
