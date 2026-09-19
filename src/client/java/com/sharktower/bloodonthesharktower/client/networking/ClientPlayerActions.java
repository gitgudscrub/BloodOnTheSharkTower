package com.sharktower.bloodonthesharktower.client.networking;

import com.sharktower.bloodonthesharktower.networking.PlayerActionC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client helper for actions any player may initiate from the Grimoire. */
public final class ClientPlayerActions {
    private ClientPlayerActions() {}

    public static void send(String action) {
        send(action, "");
    }

    public static void send(String action, String argument) {
        ClientPlayNetworking.send(new PlayerActionC2SPayload(action, argument));
    }
}
