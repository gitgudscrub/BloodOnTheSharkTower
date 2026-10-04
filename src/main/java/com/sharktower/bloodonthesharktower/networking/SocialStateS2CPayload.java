package com.sharktower.bloodonthesharktower.networking;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;
/** Public attention order and speaking activity; never carries audio or room membership. */
public record SocialStateS2CPayload(Map<UUID,Integer> attention, Map<UUID,Boolean> talking) implements CustomPacketPayload {
    public static final Type<SocialStateS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, "social_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SocialStateS2CPayload> CODEC = new StreamCodec<>() {
        public SocialStateS2CPayload decode(RegistryFriendlyByteBuf b) { return new SocialStateS2CPayload(StateWire.seatsFromJson(b.readUtf()), StateWire.deathFromJson(b.readUtf())); }
        public void encode(RegistryFriendlyByteBuf b, SocialStateS2CPayload p) { b.writeUtf(StateWire.seatsToJson(p.attention())); b.writeUtf(StateWire.deathToJson(p.talking())); }
    };
    public SocialStateS2CPayload { attention=Map.copyOf(attention); talking=Map.copyOf(talking); }
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
