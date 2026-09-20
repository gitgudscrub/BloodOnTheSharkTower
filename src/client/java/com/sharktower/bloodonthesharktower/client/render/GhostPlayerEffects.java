package com.sharktower.bloodonthesharktower.client.render;

import com.sharktower.bloodonthesharktower.states.ClientState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;

/**
 * Lightweight client-only ghost ambience for dead Clocktower players.
 *
 * Dead players with an unused ghost vote have a stronger soul-wisp effect.
 * After the ghost vote is spent the player remains visibly dead, but the
 * particle cadence becomes deliberately subtler.
 */
public final class GhostPlayerEffects {
    private static int ticks;

    private GhostPlayerEffects() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(GhostPlayerEffects::tick);
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.level == null) return;
        ticks++;

        for (Player player : minecraft.level.players()) {
            // Do not render the local player's own soul ambience. In first-person
            // the particles can drift directly across the camera and become noisy.
            if (minecraft.player != null && player.getUUID().equals(minecraft.player.getUUID())) continue;
            if (!ClientState.playerDeathStatus.getOrDefault(player.getUUID(), false)) continue;

            boolean ghostVoteAvailable = !ClientState.hasUsedGhostVote.getOrDefault(player.getUUID(), false);
            int interval = ghostVoteAvailable ? 6 : 16;
            int phase = Math.floorMod(player.getId(), interval);
            if (Math.floorMod(ticks, interval) != phase) continue;

            double time = (ticks + player.getId() * 17L) * 0.17D;
            double radius = 0.32D;
            double x = player.getX() + Math.cos(time) * radius;
            double z = player.getZ() + Math.sin(time) * radius;
            double y = player.getY() + 0.25D + ((Math.sin(time * 0.61D) + 1.0D) * 0.65D);

            minecraft.level.addParticle(
                    ParticleTypes.SOUL,
                    x, y, z,
                    Math.cos(time) * 0.008D,
                    0.018D,
                    Math.sin(time) * 0.008D
            );
        }
    }
}
