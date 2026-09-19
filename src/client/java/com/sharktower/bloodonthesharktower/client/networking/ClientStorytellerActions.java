package com.sharktower.bloodonthesharktower.client.networking;

import com.sharktower.bloodonthesharktower.networking.StorytellerActionC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Small client helper used by the original-style Grimoire controls. */
public final class ClientStorytellerActions {
    private ClientStorytellerActions() {}

    public static void send(String action) {
        send(action, "");
    }

    public static void send(String action, String argument) {
        ClientPlayNetworking.send(new StorytellerActionC2SPayload(action, argument));
    }
}
