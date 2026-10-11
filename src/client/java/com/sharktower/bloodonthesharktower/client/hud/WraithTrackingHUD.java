package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.networking.WraithTrackingS2CPayload;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Client-only directional compass and 20-block Storyteller highlight for Wraith. */
public final class WraithTrackingHUD {
    private static String mode = "OFF";
    private static double targetX, targetZ;
    private static UUID storytellerId;
    private static UUID highlightedId;

    private WraithTrackingHUD() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WraithTrackingS2CPayload.TYPE, (payload, context) ->
                context.client().execute(() -> receive(payload.state())));
        ClientTickEvents.END_CLIENT_TICK.register(WraithTrackingHUD::tick);
    }

    private static void receive(String value) {
        if (value == null || value.equals("OFF")) {
            mode = "OFF";
            storytellerId = null;
            return;
        }
        try {
            String[] parts = value.split("\\|");
            if (parts.length < 3 || (!parts[0].equals("W") && !parts[0].equals("T"))) return;
            mode = parts[0];
            targetX = Double.parseDouble(parts[1]);
            targetZ = Double.parseDouble(parts[2]);
            storytellerId = parts.length >= 4
                    ? UUID.fromString(parts[3]) : null;
        } catch (RuntimeException ignored) {
            mode = "OFF";
            storytellerId = null;
        }
    }

    public static void reset() {
        mode = "OFF";
        storytellerId = null;
        highlightedId = null;
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }
        boolean active = (mode.equals("W") && minecraft.player.isSpectator()
                || mode.equals("T") && ClientState.storytellerPlayers.contains(minecraft.player.getUUID()))
                && ClientState.phase() == com.sharktower.bloodonthesharktower.core.GamePhase.NIGHT;
        UUID next = null;
        if (active && storytellerId != null) {
            Player player = minecraft.level.getPlayerByUUID(storytellerId);
            if (player != null && (mode.equals("T") || player.distanceToSqr(minecraft.player) <= 400.0D)) {
                next = storytellerId;
            }
        }
        if (highlightedId != null && !highlightedId.equals(next)) {
            Player old = minecraft.level.getPlayerByUUID(highlightedId);
            if (old != null) old.setGlowingTag(false);
        }
        if (next != null) {
            Player player = minecraft.level.getPlayerByUUID(next);
            if (player != null) player.setGlowingTag(true);
        }
        highlightedId = next;
    }

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (minecraft.player == null || minecraft.gui.screen() != null) return;
        if (!mode.equals("W") && !mode.equals("T")) return;
        if (ClientState.phase() != com.sharktower.bloodonthesharktower.core.GamePhase.NIGHT) return;
        if (mode.equals("W") && !minecraft.player.isSpectator()) return;

        double dx = targetX - minecraft.player.getX();
        double dz = targetZ - minecraft.player.getZ();
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double delta = Math.floorMod((int)Math.round(targetYaw - minecraft.player.getYRot()) + 180, 360) - 180;
        String arrow = Math.abs(delta) < 24 ? "↑" : delta > 0
                ? (delta < 145 ? "←" : "↓") : (delta > -145 ? "→" : "↓");
        int meters = (int)Math.round(Math.hypot(dx, dz));
        String label = mode.equals("W") ? "WRAITH  " + arrow + "  Storyteller"
                : "NIGHT VISIT  " + arrow + "  House";
        int width = minecraft.getWindow().getGuiScaledWidth();
        graphics.text(minecraft.font, label, (width - minecraft.font.width(label)) / 2, 44, 0xFF82D8E5, true);
        if (mode.equals("T")) {
            String distance = meters + " blocks away";
            graphics.text(minecraft.font, distance, (width - minecraft.font.width(distance)) / 2, 55,
                    0xFFCCCCCC, true);
        }
    }
}
