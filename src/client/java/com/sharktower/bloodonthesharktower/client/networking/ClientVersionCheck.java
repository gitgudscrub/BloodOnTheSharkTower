package com.sharktower.bloodonthesharktower.client.networking;

import com.sharktower.bloodonthesharktower.networking.VersionCheck;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;

/** Answers the server's login-time version request with this client's version. */
public final class ClientVersionCheck {
    private ClientVersionCheck() {}

    public static void register() {
        String version = VersionCheck.modVersion();
        ClientConfigurationNetworking.registerGlobalReceiver(VersionCheck.RequestS2C.TYPE, (payload, context) ->
                context.responseSender().sendPacket(new VersionCheck.ReplyC2S(version)));
    }
}
