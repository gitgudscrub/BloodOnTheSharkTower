package com.sharktower.bloodonthesharktower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.client.hud.GameEndAnimationHUD;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Public in-world YES/NO vote markers.
 *
 * Living YES = green tick.
 * Dead/ghost YES = blue tick.
 * NO = red cross.
 *
 * Once the clock passes a seat, the marker follows the locked vote rather than
 * the live hand state. Organ Grinder hides these public markers from ordinary
 * players while leaving them visible to the Storyteller.
 */
public final class VoteIndicatorRenderer {
    private static final double ICON_SIZE = 0.34D;
    private static final double CURRENT_VOTER_BONUS = 0.06D;
    private static final double HEAD_OFFSET = 0.24D;

    private static final Identifier YES_TEXTURE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/hud/vote_yes.png");
    private static final Identifier NO_TEXTURE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/hud/vote_no.png");
    private static final Identifier GHOST_TEXTURE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/hud/vote_ghost.png");

    private VoteIndicatorRenderer() {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context ->
                render(context.submitNodeCollector(), context.poseStack()));
    }

    private static void render(SubmitNodeCollector collector, PoseStack poseStack) {
        if (GameEndAnimationHUD.isAnimating()) return;
        if (!ClientState.voteInProgress && !ClientState.exileSupportVote) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        UUID localId = minecraft.player.getUUID();
        boolean localStoryteller = ClientState.storytellerPlayers.contains(localId);

        // Organ Grinder intentionally conceals individual votes from players.
        if (ClientState.organGrinderMode && !localStoryteller) return;

        Vec3 camera = minecraft.gameRenderer.mainCamera().position();

        for (Player player : minecraft.level.players()) {
            UUID id = player.getUUID();
            if (id.equals(localId)) continue;
            if (!ClientState.playerSeatNumbers.containsKey(id)) continue;
            if (ClientState.storytellerPlayers.contains(id)) continue;

            boolean yes = ClientState.lockedVotes.containsKey(id)
                    ? ClientState.lockedVotes.getOrDefault(id, false)
                    : ClientState.currentVotes.getOrDefault(id, false);
            boolean dead = ClientState.playerDeathStatus.getOrDefault(id, false);

            Identifier texture = yes
                    ? (dead ? GHOST_TEXTURE : YES_TEXTURE)
                    : NO_TEXTURE;

            double size = ICON_SIZE;
            if (id.equals(ClientState.currentVoteClockPlayer)) {
                // A small pulse/size increase makes the seat currently being
                // counted readable without adding another HUD element.
                double pulse = (Math.sin(System.nanoTime() / 120_000_000.0D) + 1.0D) * 0.5D;
                size += CURRENT_VOTER_BONUS * pulse;
            }

            double cx = player.getX();
            double cy = player.getY() + player.getBbHeight() + HEAD_OFFSET;
            double cz = player.getZ();
            submitBillboard(collector, poseStack, camera, texture, cx, cy, cz, size);
        }
    }

    private static void submitBillboard(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            Vec3 camera,
            Identifier texture,
            double cx,
            double cy,
            double cz,
            double iconSize
    ) {
        double fx = camera.x - cx;
        double fz = camera.z - cz;
        double horizontal = Math.sqrt(fx * fx + fz * fz);
        if (horizontal < 0.0001D) {
            fx = 0.0D;
            fz = 1.0D;
            horizontal = 1.0D;
        }
        fx /= horizontal;
        fz /= horizontal;

        double rx = fz;
        double rz = -fx;
        double half = iconSize * 0.5D;

        float leftX = (float) (cx - rx * half - camera.x);
        float leftZ = (float) (cz - rz * half - camera.z);
        float rightX = (float) (cx + rx * half - camera.x);
        float rightZ = (float) (cz + rz * half - camera.z);
        float bottomY = (float) (cy - half - camera.y);
        float topY = (float) (cy + half - camera.y);

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture), (pose, consumer) -> {
            vertex(consumer, pose, leftX, bottomY, leftZ, 0.0F, 1.0F);
            vertex(consumer, pose, rightX, bottomY, rightZ, 1.0F, 1.0F);
            vertex(consumer, pose, rightX, topY, rightZ, 1.0F, 0.0F);
            vertex(consumer, pose, leftX, topY, leftZ, 0.0F, 0.0F);
        });
    }

    private static void vertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v
    ) {
        consumer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
