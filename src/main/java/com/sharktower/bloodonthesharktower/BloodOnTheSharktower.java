package com.sharktower.bloodonthesharktower;

import com.sharktower.bloodonthesharktower.command.BotsCommands;
import com.sharktower.bloodonthesharktower.core.LegacyPortInfo;
import com.sharktower.bloodonthesharktower.core.PhasePresentation;
import com.sharktower.bloodonthesharktower.core.RoleModelDiagnostics;
import com.sharktower.bloodonthesharktower.daytime.ElectionManager;
import com.sharktower.bloodonthesharktower.daytime.DayPublicInfoBookManager;
import com.sharktower.bloodonthesharktower.networking.ModPackets;
import com.sharktower.bloodonthesharktower.networking.ModPayloads;
import com.sharktower.bloodonthesharktower.setup.DuskHomeCompassManager;
import com.sharktower.bloodonthesharktower.setup.MapConfigurationStore;
import com.sharktower.bloodonthesharktower.sound.ModSounds;
import com.sharktower.bloodonthesharktower.voicechat.NightChatManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BloodOnTheSharktower implements ModInitializer {
    public static final String MOD_ID = "blood_on_the_sharktower";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Blood on the Sharktower initialized successfully.");
        LOGGER.info(
                "Private BOTB port foundation loaded: source {}, {} legacy asset files staged.",
                LegacyPortInfo.SOURCE_VERSION,
                LegacyPortInfo.IMPORTED_ASSET_FILE_COUNT
        );
        RoleModelDiagnostics.Result roleModel = RoleModelDiagnostics.run();
        LOGGER.info(
                "Role/script model smoke test: {} ({} enum roles, {} selectable, {} parsed test roles).",
                roleModel.ok() ? "PASS" : "FAIL",
                roleModel.enumEntries(),
                roleModel.selectableRoles(),
                roleModel.parsedSmokeRoles()
        );
        MapConfigurationStore.load();
        ModSounds.initialize();
        ModPayloads.registerPayloads();
        ModPackets.registerC2SReceivers();
        LOGGER.info("Bulk 0.5.0 game-flow networking registered: core state, daytime elections, votes, executions and exile state.");
        BotsCommands.register();
        ServerTickEvents.END_SERVER_TICK.register(NightChatManager::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(ElectionManager::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(PhasePresentation::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(DuskHomeCompassManager::serverTick);
        ServerTickEvents.END_SERVER_TICK.register(DayPublicInfoBookManager::serverTick);
        LOGGER.info("Night-chat automation, world clock-hand voting, enforced vote seating, dusk Home Compass routing and temporary public-info books registered.");
    }
}
