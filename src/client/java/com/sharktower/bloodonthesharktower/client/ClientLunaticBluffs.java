package com.sharktower.bloodonthesharktower.client;

import com.sharktower.bloodonthesharktower.networking.LunaticBluffActionC2SPayload;
import com.sharktower.bloodonthesharktower.networking.LunaticBluffsS2CPayload;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

/** Private client cache for the Lunatic's separate fake bluff set. */
public final class ClientLunaticBluffs {
    private static List<String> roleIds = List.of();
    private static int revision;

    private ClientLunaticBluffs() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LunaticBluffsS2CPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    roleIds = List.copyOf(payload.roleIds());
                    revision++;
                }));
    }

    public static List<String> current() {
        // A new/reset game returns to Setup before Night 1. Drop any previous
        // Lunatic set here so a player who becomes Lunatic again cannot see the
        // previous game's fake bluffs before the Storyteller chooses new ones.
        if (ClientState.currentNight == 0 && ClientState.currentDay == 0 && !roleIds.isEmpty()) {
            roleIds = List.of();
            revision++;
        }
        return roleIds;
    }

    public static int revision() {
        return revision;
    }

    public static void request() {
        ClientPlayNetworking.send(new LunaticBluffActionC2SPayload("get", ""));
    }

    public static void set(List<String> ids) {
        // Optimistic local update keeps the manual Demon Info screen responsive;
        // the server immediately sends back its authoritative set after validation.
        roleIds = ids == null ? List.of() : List.copyOf(ids);
        revision++;
        ClientPlayNetworking.send(new LunaticBluffActionC2SPayload("set", String.join("|", roleIds)));
    }

    public static void sendToLunatic() {
        ClientPlayNetworking.send(new LunaticBluffActionC2SPayload("send", ""));
    }

    public static void clear() {
        roleIds = List.of();
        revision++;
    }
}
