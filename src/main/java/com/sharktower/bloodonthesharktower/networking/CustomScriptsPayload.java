package com.sharktower.bloodonthesharktower.networking;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
/** Storyteller-only library responses. Never broadcast script filenames to players. */
public record CustomScriptsPayload(String kind, String text, String token) implements CustomPacketPayload {
    public static final Type<CustomScriptsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID,"custom_scripts"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CustomScriptsPayload> CODEC = new StreamCodec<>() {
        public CustomScriptsPayload decode(RegistryFriendlyByteBuf b) { return new CustomScriptsPayload(b.readUtf(32),b.readUtf(262144),b.readUtf(64)); }
        public void encode(RegistryFriendlyByteBuf b,CustomScriptsPayload p) { b.writeUtf(p.kind(),32);b.writeUtf(p.text(),262144);b.writeUtf(p.token(),64); }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
