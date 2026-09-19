package com.sharktower.bloodonthesharktower.voicechat;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatServerApi;

/**
 * Small, thread-safe diagnostic bridge between the Simple Voice Chat plugin
 * entrypoint and the rest of Blood on the Sharktower.
 *
 * 1.0.1-dev intentionally does not alter or reroute any audio. It only proves
 * that the 26.3 voice chat API loads and gives later Night Chat work a stable
 * integration point.
 */
public final class VoicechatIntegrationState {
    private static volatile VoicechatApi api;
    private static volatile VoicechatServerApi serverApi;
    private static volatile VoicechatClientApi clientApi;
    private static volatile boolean initialized;
    private static volatile boolean serverStarted;
    private static volatile boolean clientConnected;

    private VoicechatIntegrationState() {}

    static void onInitialized(VoicechatApi voicechatApi) {
        api = voicechatApi;
        initialized = true;
    }

    static void onServerStarted(VoicechatServerApi voicechatServerApi) {
        serverApi = voicechatServerApi;
        serverStarted = true;
    }

    static void onServerStopped() {
        serverStarted = false;
        serverApi = null;
    }

    static void onClientConnectionChanged(boolean connected, VoicechatClientApi voicechatClientApi) {
        clientConnected = connected;
        clientApi = connected ? voicechatClientApi : null;
    }

    /**
     * Local-client speaking state supplied by Simple Voice Chat. This is kept
     * here rather than reflected from SVC internals, so HUD code only depends on
     * the public voicechat API and fails closed if voice chat is unavailable.
     */
    public static boolean isPlayerTalking(java.util.UUID playerId) {
        VoicechatClientApi api = clientApi;
        if (!clientConnected || api == null || playerId == null) return false;
        try {
            return api.isTalking(playerId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isInitialized() {
        return initialized && api != null;
    }

    public static boolean isServerStarted() {
        return serverStarted && serverApi != null;
    }

    /**
     * On an integrated server this reflects the local development client.
     * On a dedicated server it normally remains false because there is no
     * local client in the same JVM.
     */
    public static boolean isClientConnected() {
        return clientConnected;
    }

    public static VoicechatServerApi serverApi() {
        return serverApi;
    }
}
