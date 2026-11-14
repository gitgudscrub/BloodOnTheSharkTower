package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.client.config.ClientSettings;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;

/** Registers the first large presentation batch as a single ordered HUD layer. */
public final class SharktowerHudRenderer {
    private SharktowerHudRenderer() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(BloodOnTheSharktower.MOD_ID, "main_hud"),
                SharktowerHudRenderer::render
        );
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        float scale = HudScale.scale();
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        try {
            renderScaled(graphics, minecraft);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void renderScaled(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        EndGameHUD.render(graphics, minecraft);
        // Every Sharktower screen (Grimoire, Role Bag, tools...) is a full-screen
        // panel. The role card, setup banner, sidebar and election panel drawn
        // underneath only bleed through the blur and overlap the screen's own
        // widgets, so skip them while any screen other than chat is open.
        if (minecraft.gui.screen() != null && !(minecraft.gui.screen() instanceof ChatScreen)) return;
        if (ClientSettings.voiceHud) VoiceRouteHUD.render(graphics, minecraft);
        NightVisitInfoHUD.render(graphics, minecraft);
        if (ClientSettings.roleHud) RoleHUD.render(graphics, minecraft);
        if (ClientSettings.setupHud) SetupHUD.render(graphics, minecraft);
        if (ClientSettings.roleCountsHud) RoleDistributionHUD.render(graphics, minecraft);
        NightOrderHUD.render(graphics, minecraft);
        if (ClientSettings.playerListHud) PlayerSidebarHUD.render(graphics, minecraft);
        if (ClientSettings.electionHud) ElectionHUD.render(graphics, minecraft);
        VoteCountdownHUD.render(graphics, minecraft);
        if (ClientSettings.handHud) HandVoteHUD.render(graphics, minecraft);
        if (ClientSettings.timerHud) TimerHUD.render(graphics, minecraft);
    }
}
