package com.sharktower.bloodonthesharktower.client.event;

import com.mojang.blaze3d.platform.InputConstants;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.client.gui.AssignRolesScreen;
import com.sharktower.bloodonthesharktower.client.gui.CharacterDetailsScreen;
import com.sharktower.bloodonthesharktower.client.gui.FinalGrimoireScreen;
import com.sharktower.bloodonthesharktower.client.gui.RoleCatalogScreen;
import com.sharktower.bloodonthesharktower.client.gui.ScriptReferenceScreen;
import com.sharktower.bloodonthesharktower.client.gui.StorytellerToolsScreen;
import com.sharktower.bloodonthesharktower.client.gui.SharktowerSettingsScreen;
import com.sharktower.bloodonthesharktower.client.gui.TimerScreen;
import com.sharktower.bloodonthesharktower.client.hud.NightOrderHUD;
import com.sharktower.bloodonthesharktower.client.networking.ClientPlayerActions;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 26.3 port of the core BOTB key map family, keeping the familiar defaults:
 * Z role HUD, R grimoire, K catalog, C script, X role details, Y timer, I tools.
 */
public final class KeyInputHandler {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, "botc")
    );

    public static KeyMapping toggleShowRole;
    public static KeyMapping openAssignGui;
    public static KeyMapping openCatalogKey;
    public static KeyMapping openScriptKey;
    public static KeyMapping openMyRoleDetailsKey;
    public static KeyMapping disableHudKey;
    public static KeyMapping openTimerKey;
    public static KeyMapping openStorytellerToolsKey;
    public static KeyMapping toggleVoteHandKey;
    public static KeyMapping leavePrivateChatKey;
    public static KeyMapping openSettingsKey;
    public static KeyMapping toggleNightHudKey;
    public static KeyMapping nightHudNextKey;
    public static KeyMapping nightHudPrevKey;
    public static KeyMapping nightHudActivateKey;

    // Arrow-key phase controls use rising-edge polling instead of consumeClick().
    // On 26.3 the registered arrow mappings can remain held/down without
    // reliably incrementing the click counter used by consumeClick().
    private static boolean nightHudNextWasDown;
    private static boolean nightHudPrevWasDown;
    private static boolean nightHudActivateWasDown;

    private KeyInputHandler() {}

    public static void register() {
        toggleShowRole = bind("key.blood_on_the_sharktower.toggle_show_role", InputConstants.KEY_Z);
        openAssignGui = bind("key.blood_on_the_sharktower.open_assign_gui", InputConstants.KEY_R);
        openCatalogKey = bind("key.blood_on_the_sharktower.open_catalog", InputConstants.KEY_K);
        openScriptKey = bind("key.blood_on_the_sharktower.open_script", InputConstants.KEY_C);
        openMyRoleDetailsKey = bind("key.blood_on_the_sharktower.open_my_role_details", InputConstants.KEY_X);
        disableHudKey = bind("key.blood_on_the_sharktower.disable_hud", InputConstants.KEY_B);
        openTimerKey = bind("key.blood_on_the_sharktower.open_timer", InputConstants.KEY_Y);
        openStorytellerToolsKey = bind("key.blood_on_the_sharktower.open_storyteller_tools", InputConstants.KEY_I);
        toggleVoteHandKey = bind("key.blood_on_the_sharktower.toggle_vote_hand", InputConstants.KEY_U);
        leavePrivateChatKey = bind("key.blood_on_the_sharktower.leave_private_chat", InputConstants.KEY_J);
        openSettingsKey = bind("key.blood_on_the_sharktower.open_settings", InputConstants.KEY_O);
        toggleNightHudKey = bind("key.blood_on_the_sharktower.toggle_night_hud", InputConstants.KEY_N);
        nightHudNextKey = bind("key.blood_on_the_sharktower.night_hud_next", 262); // Right Arrow
        nightHudPrevKey = bind("key.blood_on_the_sharktower.night_hud_prev", 263); // Left Arrow
        nightHudActivateKey = bind("key.blood_on_the_sharktower.night_hud_teleport", 265); // Up Arrow

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (toggleShowRole.consumeClick()) {
                ClientState.isRoleHudVisible = !ClientState.isRoleHudVisible;
                client.player.sendSystemMessage(Component.literal("Role HUD " + (ClientState.isRoleHudVisible ? "shown" : "hidden")));
            }
            while (disableHudKey.consumeClick()) {
                ClientState.isHudEnabled = !ClientState.isHudEnabled;
                client.player.sendSystemMessage(Component.literal("Sharktower HUD " + (ClientState.isHudEnabled ? "enabled" : "disabled")));
            }
            while (openAssignGui.consumeClick()) {
                if (ClientState.gameEnding && ClientState.rolesRevealed) {
                    client.gui.setScreen(new FinalGrimoireScreen(null));
                } else {
                    client.gui.setScreen(new AssignRolesScreen());
                }
            }
            while (openCatalogKey.consumeClick()) client.gui.setScreen(new RoleCatalogScreen());
            while (openScriptKey.consumeClick()) {
                if (ClientState.currentScript == null) {
                    client.player.sendSystemMessage(Component.literal("No script is currently assigned."));
                } else {
                    client.gui.setScreen(new ScriptReferenceScreen());
                }
            }
            while (openMyRoleDetailsKey.consumeClick()) client.gui.setScreen(new CharacterDetailsScreen(ClientState.myAssignment.getScriptRole()));
            while (openTimerKey.consumeClick()) client.gui.setScreen(new TimerScreen());
            while (openStorytellerToolsKey.consumeClick()) client.gui.setScreen(new StorytellerToolsScreen());
            while (openSettingsKey.consumeClick()) client.gui.setScreen(new SharktowerSettingsScreen(null));
            while (toggleVoteHandKey.consumeClick()) ClientPlayerActions.send("toggle_hand");
            while (leavePrivateChatKey.consumeClick()) {
                if (ClientState.voiceRoute != null && ClientState.voiceRoute.startsWith("PRIVATE")) {
                    ClientPlayerActions.send("leave_private");
                }
            }

            boolean storyteller = ClientState.storytellerPlayers.contains(client.player.getUUID());
            while (toggleNightHudKey.consumeClick()) {
                if (!storyteller) continue;
                ClientState.isNightHudVisible = !ClientState.isNightHudVisible;
                client.player.sendSystemMessage(Component.literal(
                        "Storyteller phase bar " + (ClientState.isNightHudVisible ? "shown" : "hidden")));
            }

            // 26.3: use the mapping's held state and detect the rising edge ourselves.
            // This keeps the controls rebindable through Minecraft's Controls menu,
            // but avoids relying on consumeClick() for the arrow keys.
            boolean nextDown = nightHudNextKey.isDown();
            boolean prevDown = nightHudPrevKey.isDown();
            boolean activateDown = nightHudActivateKey.isDown();

            if (storyteller && client.gui.screen() == null && ClientState.isNightHudVisible) {
                if (nextDown && !nightHudNextWasDown) NightOrderHUD.advance(1);
                if (prevDown && !nightHudPrevWasDown) NightOrderHUD.advance(-1);
                if (activateDown && !nightHudActivateWasDown) NightOrderHUD.activate();
            }

            // Always update the remembered state, even while a GUI is open or the
            // player is not the Storyteller. Holding a key while closing a screen
            // therefore cannot accidentally trigger a phase change.
            nightHudNextWasDown = nextDown;
            nightHudPrevWasDown = prevDown;
            nightHudActivateWasDown = activateDown;
        });
    }

    private static KeyMapping bind(String translationKey, int key) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYBOARD,
                key,
                CATEGORY
        ));
    }
}
