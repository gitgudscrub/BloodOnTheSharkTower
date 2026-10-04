package com.sharktower.bloodonthesharktower.networking;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
/** Sent only to the requesting ST; the opaque token authorizes the exact preview. */
public record TeamInfoPreviewPayload(String token, String text) implements CustomPacketPayload {
    public static final Type<TeamInfoPreviewPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID,"team_info_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamInfoPreviewPayload> CODEC = new StreamCodec<>() {
        public TeamInfoPreviewPayload decode(RegistryFriendlyByteBuf b) { return new TeamInfoPreviewPayload(b.readUtf(64), b.readUtf(32767)); }
        public void encode(RegistryFriendlyByteBuf b, TeamInfoPreviewPayload p) { b.writeUtf(p.token(),64); b.writeUtf(p.text(),32767); }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
