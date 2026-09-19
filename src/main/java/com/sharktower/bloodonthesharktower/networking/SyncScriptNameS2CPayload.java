package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Temporary narrow script-state payload used while the full original
 * SendScriptS2CPayload/Script codec stack is ported in the next networking slice.
 */
public record SyncScriptNameS2CPayload(String scriptName) implements CustomPacketPayload {
    public static final Identifier ID_VALUE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID,
            "sync_script_name"
    );

    public static final Type<SyncScriptNameS2CPayload> TYPE = new Type<>(ID_VALUE);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncScriptNameS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            SyncScriptNameS2CPayload::scriptName,
            SyncScriptNameS2CPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
