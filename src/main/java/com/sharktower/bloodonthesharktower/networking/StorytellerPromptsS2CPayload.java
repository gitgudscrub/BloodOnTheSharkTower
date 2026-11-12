package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.automation.StorytellerPrompt;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Decisions waiting on the Storyteller. Non-Storytellers always receive an empty list. */
public record StorytellerPromptsS2CPayload(List<StorytellerPrompt> prompts) implements CustomPacketPayload {
    public static final Type<StorytellerPromptsS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "storyteller_prompts"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorytellerPromptsS2CPayload> CODEC = new StreamCodec<>() {
        public StorytellerPromptsS2CPayload decode(RegistryFriendlyByteBuf buf) {
            return new StorytellerPromptsS2CPayload(StorytellerPrompt.fromJson(buf.readUtf(65535)));
        }

        public void encode(RegistryFriendlyByteBuf buf, StorytellerPromptsS2CPayload value) {
            buf.writeUtf(StorytellerPrompt.toJson(value.prompts()), 65535);
        }
    };

    public StorytellerPromptsS2CPayload {
        prompts = List.copyOf(prompts);
    }

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
