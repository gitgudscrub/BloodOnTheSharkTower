package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Whether this player is locked in their house for the night (their client draws dark fog). */
public record NightLockS2CPayload(boolean locked) implements CustomPacketPayload {
    public static final Type<NightLockS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "night_lock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NightLockS2CPayload> CODEC =
            ByteBufCodecs.BOOL.map(NightLockS2CPayload::new, NightLockS2CPayload::locked).cast();

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
