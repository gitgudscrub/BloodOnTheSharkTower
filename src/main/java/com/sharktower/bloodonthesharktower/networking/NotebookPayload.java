package com.sharktower.bloodonthesharktower.networking;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
/** Owner-only private notes. Empty text is valid; generation rejects saves from an old game. */
public record NotebookPayload(long generation, String text) implements CustomPacketPayload {
    public static final int LIMIT = 8192;
    public static final Type<NotebookPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID,"notebook"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NotebookPayload> CODEC = new StreamCodec<>() {
        public NotebookPayload decode(RegistryFriendlyByteBuf b) { return new NotebookPayload(b.readVarLong(),b.readUtf(LIMIT)); }
        public void encode(RegistryFriendlyByteBuf b,NotebookPayload p) { b.writeVarLong(p.generation()); b.writeUtf(p.text(),LIMIT); }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
