package com.sharktower.bloodonthesharktower.networking;

import com.sharktower.bloodonthesharktower.setup.LunaticBluffs;
import com.sharktower.bloodonthesharktower.setup.SetupOperations;
import com.sharktower.bloodonthesharktower.states.StorytellerState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;

/** Dedicated private control channel for fake Lunatic Demon bluffs. */
public final class LunaticBluffNetworking {
    private LunaticBluffNetworking() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(LunaticBluffActionC2SPayload.TYPE, (payload, context) -> {
            ServerPlayer actor = context.player();
            MinecraftServer server = actor.level().getServer();
            if (server == null) return;

            if (!StorytellerState.isStoryteller(actor.getUUID())) {
                actor.sendSystemMessage(Component.literal("Lunatic bluff controls are Storyteller-only.")
                        .withStyle(ChatFormatting.GRAY));
                return;
            }

            String action = payload.action().trim().toLowerCase(java.util.Locale.ROOT);
            SetupOperations.Result result;
            switch (action) {
                case "get" -> {
                    sendSnapshot(actor);
                    return;
                }
                case "set" -> {
                    List<String> ids = payload.argument().isBlank()
                            ? List.of()
                            : Arrays.asList(payload.argument().split("\\|"));
                    result = LunaticBluffs.set(ids);
                    sendSnapshot(actor);
                }
                case "send" -> result = LunaticBluffs.send(server);
                default -> result = SetupOperations.Result.fail("Unknown Lunatic bluff action: " + payload.action());
            }

            actor.sendSystemMessage(Component.literal(result.message())
                    .withStyle(result.ok() ? ChatFormatting.GRAY : ChatFormatting.RED));
        });
    }

    public static void sendSnapshot(ServerPlayer storyteller) {
        if (storyteller == null || !StorytellerState.isStoryteller(storyteller.getUUID())) return;
        ServerPlayNetworking.send(storyteller, new LunaticBluffsS2CPayload(LunaticBluffs.currentIds()));
    }
}
