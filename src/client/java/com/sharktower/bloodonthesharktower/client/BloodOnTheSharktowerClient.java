package com.sharktower.bloodonthesharktower.client;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.client.config.ClientSettings;
import com.sharktower.bloodonthesharktower.client.event.KeyInputHandler;
import com.sharktower.bloodonthesharktower.client.hud.SharktowerHudRenderer;
import com.sharktower.bloodonthesharktower.client.gui.GrimoireReturnState;
import com.sharktower.bloodonthesharktower.client.networking.CoreStateReceivers;
import com.sharktower.bloodonthesharktower.client.render.ClockHandsRenderer;
import com.sharktower.bloodonthesharktower.client.render.GhostPlayerEffects;
import com.sharktower.bloodonthesharktower.client.render.RoleIconRenderer;
import com.sharktower.bloodonthesharktower.client.render.VoteIndicatorRenderer;
import net.fabricmc.api.ClientModInitializer;

public final class BloodOnTheSharktowerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientSettings.load();
        CoreStateReceivers.register();
        GrimoireReturnState.register();
        KeyInputHandler.register();
        SharktowerHudRenderer.register();
        ClockHandsRenderer.register();
        RoleIconRenderer.register();
        VoteIndicatorRenderer.register();
        GhostPlayerEffects.register();
        BloodOnTheSharktower.LOGGER.info("Blood on the Sharktower client initialized successfully.");
        BloodOnTheSharktower.LOGGER.info("Core client networking receivers registered.");
        BloodOnTheSharktower.LOGGER.info("1.1.0-dev A.11 client polish registered: player list, public role counts, and knowledge-scoped world role icons.");
    }
}
