package com.sharktower.bloodonthesharktower.client;

import com.sharktower.bloodonthesharktower.networking.LunaticBluffActionC2SPayload;
import com.sharktower.bloodonthesharktower.networking.LunaticBluffsS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

/** Storyteller-only client cache for the Lunatic's separate fake bluff set. */
public final class ClientLunaticBluffs {
    private static List<String> roleIds = List.of();

    private ClientLunaticBluffs() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LunaticBluffsS2CPayload.TYPE, (payload, context) ->
                context.client().execute(() -> roleIds = List.copyOf(payload.roleIds())));
    }

    public static List<String> current() {
        return roleIds;
    }

    public static void request() {
        ClientPlayNetworking.send(new LunaticBluffActionC2SPayload("get", ""));
    }

    public static void set(List<String> ids) {
        ClientPlayNetworking.send(new LunaticBluffActionC2SPayload("set", String.join("|", ids)));
    }

    public static void sendToLunatic() {
        ClientPlayNetworking.send(new LunaticBluffActionC2SPayload("send", ""));
    }

    public static void clear() {
        roleIds = List.of();
    }
}
