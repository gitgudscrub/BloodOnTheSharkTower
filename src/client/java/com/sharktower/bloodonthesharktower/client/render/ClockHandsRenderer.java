package com.sharktower.bloodonthesharktower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * World-space BOTC clock hands. Before a vote the red hand follows the nominee;
 * while voting it sweeps from seat to seat using the same authoritative A.4
 * election index that locks player hands.
 */
public final class ClockHandsRenderer {
    private static final Identifier MINUTE_TEXTURE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/objects/minute_hand.png");
    private static final Identifier EXILE_TEXTURE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/objects/minute_hand_exile.png");
    private static final Identifier HOUR_TEXTURE = Identifier.fromNamespaceAndPath(
            BloodOnTheSharktower.MOD_ID, "textures/objects/hour_hand.png");

    // Preserve the source artwork's native aspect ratio in world space.
    // minute_hand.png = 11x48, hour_hand.png = 11x35.
    private static final double MINUTE_ASPECT = 11.0D / 48.0D;
    private static final double HOUR_ASPECT = 11.0D / 35.0D;

    private static double smoothedNomineeAngle = Double.NaN;
    private static double smoothedReferenceAngle = Double.NaN;

    private ClockHandsRenderer() {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> render(context.submitNodeCollector(), context.poseStack()));
    }

    private static void render(SubmitNodeCollector collector, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !ClientState.clockCenterAvailable) return;
        boolean exile = ClientState.currentExileTarget != null || ClientState.exileSupportVote;
        boolean electionClock = ClientState.voteInProgress || ClientState.exileSupportVote;
        UUID electionTarget = exile ? ClientState.currentExileTarget : ClientState.currentNominee;
        UUID electionCaller = exile ? ClientState.currentExileCaller : ClientState.currentNominator;
        if (electionTarget == null && !electionClock) return;

        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        Point center = new Point(ClientState.clockCenterX, ClientState.clockCenterY + 0.045D, ClientState.clockCenterZ);

        Point minuteTarget;
        if (electionClock && ClientState.voteCountdownNumber() > 0) {
            // Keep the long hand on the nominee/exile target for the visible
            // 3/2/1. It jumps to the first voter only when the server-authoritative
            // pre-clock hold has elapsed.
            minuteTarget = liveOrFallback(minecraft, electionTarget,
                    ClientState.clockReferenceAvailable,
                    ClientState.clockReferenceX, ClientState.clockReferenceY, ClientState.clockReferenceZ);
        } else if (electionClock) {
            minuteTarget = voteSweepTarget(minecraft, center, electionTarget);
        } else {
            minuteTarget = liveOrFallback(minecraft, electionTarget,
                    ClientState.clockTargetAvailable,
                    ClientState.clockTargetX, ClientState.clockTargetY, ClientState.clockTargetZ);
            if (minuteTarget != null) {
                double desired = angleTo(center, minuteTarget);
                smoothedNomineeAngle = approachAngle(smoothedNomineeAngle, desired, 0.18D);
                minuteTarget = pointAt(center, smoothedNomineeAngle, horizontalDistance(center, minuteTarget));
            }
        }

        Point referenceTarget = liveOrFallback(minecraft,
                electionClock ? electionTarget : electionCaller,
                ClientState.clockReferenceAvailable,
                ClientState.clockReferenceX, ClientState.clockReferenceY, ClientState.clockReferenceZ);

        if (referenceTarget != null) {
            double desired = angleTo(center, referenceTarget);
            smoothedReferenceAngle = approachAngle(smoothedReferenceAngle, desired, 0.14D);
            referenceTarget = pointAt(center, smoothedReferenceAngle, horizontalDistance(center, referenceTarget));
            submitHand(collector, poseStack, camera, center, referenceTarget, HOUR_TEXTURE,
                    ClientState.clockHandScale, 0.66D, HOUR_ASPECT, -0.006D);
        }

        if (minuteTarget != null) {
            submitHand(collector, poseStack, camera, center, minuteTarget, exile ? EXILE_TEXTURE : MINUTE_TEXTURE,
                    ClientState.clockHandScale, 1.0D, MINUTE_ASPECT, 0.006D);
        }
    }

    /** Smooth one-second sweep from the previous counted seat to the current seat. */
    private static Point voteSweepTarget(Minecraft minecraft, Point center, UUID electionTarget) {
        UUID current = ClientState.currentVoteClockPlayer;
        if (ClientState.voteClockComplete || current == null) {
            return liveOrFallback(minecraft, electionTarget,
                    ClientState.clockReferenceAvailable,
                    ClientState.clockReferenceX, ClientState.clockReferenceY, ClientState.clockReferenceZ);
        }

        Point currentPoint = livePlayerPoint(minecraft, current);
        if (currentPoint == null && ClientState.clockTargetAvailable) {
            currentPoint = new Point(ClientState.clockTargetX, ClientState.clockTargetY, ClientState.clockTargetZ);
        }
        if (currentPoint == null) return null;

        UUID previous = previousClockPlayer(current, electionTarget, ClientState.playerSeatNumbers);
        Point previousPoint = livePlayerPoint(minecraft, previous);
        if (previousPoint == null) {
            previousPoint = livePlayerPoint(minecraft, electionTarget);
        }
        if (previousPoint == null) previousPoint = currentPoint;

        double a0 = angleTo(center, previousPoint);
        double a1 = angleTo(center, currentPoint);
        double stepSeconds = Math.max(0.05D, ClientState.voteStepTicks / 20.0D);
        double elapsed = (System.nanoTime() - ClientState.voteClockStepClientNanos) / 1_000_000_000.0D;
        double t = Math.max(0.0D, Math.min(1.0D, elapsed / stepSeconds));
        // Ease very slightly so the physical pointer feels deliberate rather than robotic.
        t = t * t * (3.0D - 2.0D * t);
        double angle = lerpAngle(a0, a1, t);
        double radius = horizontalDistance(center, currentPoint);
        return pointAt(center, angle, radius);
    }

    private static UUID previousClockPlayer(UUID current, UUID nominee, Map<UUID, Integer> seats) {
        if (current == null || nominee == null || seats == null || seats.isEmpty()) return nominee;
        List<Map.Entry<UUID, Integer>> entries = new ArrayList<>(seats.entrySet());
        entries.removeIf(entry -> ClientState.isExiledTraveler(entry.getKey()));
        entries.sort(Comparator.comparingInt(Map.Entry::getValue));
        List<UUID> ring = entries.stream().map(Map.Entry::getKey).toList();
        int nomineeIndex = ring.indexOf(nominee);
        if (nomineeIndex < 0) return nominee;

        List<UUID> order = new ArrayList<>(ring.size());
        for (int i = 1; i <= ring.size(); i++) order.add(ring.get((nomineeIndex + i) % ring.size()));
        int index = order.indexOf(current);
        if (index <= 0) return nominee;
        return order.get(index - 1);
    }

    private static Point liveOrFallback(Minecraft minecraft, UUID playerId, boolean hasFallback,
                                        double x, double y, double z) {
        Point live = livePlayerPoint(minecraft, playerId);
        if (live != null) return live;
        return hasFallback ? new Point(x, y, z) : null;
    }

    private static Point livePlayerPoint(Minecraft minecraft, UUID playerId) {
        if (minecraft.level == null || playerId == null) return null;
        Player player = minecraft.level.getPlayerByUUID(playerId);
        if (player == null) return null;
        // Horizontal tracking is intentional: jumping/fidgeting should not make the hand bob.
        return new Point(player.getX(), ClientState.clockCenterY, player.getZ());
    }

    private static void submitHand(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera,
                                   Point center, Point target, Identifier texture,
                                   double configuredScale, double lengthFactor, double textureAspect,
                                   double yOffset) {
        double dx = target.x - center.x;
        double dz = target.z - center.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.001D) return;

        double ux = dx / distance;
        double uz = dz / distance;
        double px = -uz;
        double pz = ux;

        double scaleRatio = Math.max(0.45D, Math.min(1.75D, configuredScale / 4.0D));
        double length = Math.max(2.2D, Math.min(13.5D, distance * 0.90D)) * lengthFactor * scaleRatio;
        // Width must scale with rendered length or the 11px-wide source texture gets
        // progressively squeezed on larger town-square radii.
        double halfWidth = (length * textureAspect) * 0.5D;
        double back = 0.08D * configuredScale;

        double sx = center.x - ux * back;
        double sz = center.z - uz * back;
        double ex = center.x + ux * length;
        double ez = center.z + uz * length;
        double y = center.y + yOffset;

        float x0 = (float) (sx - px * halfWidth - camera.x);
        float z0 = (float) (sz - pz * halfWidth - camera.z);
        float x1 = (float) (sx + px * halfWidth - camera.x);
        float z1 = (float) (sz + pz * halfWidth - camera.z);
        float x2 = (float) (ex + px * halfWidth - camera.x);
        float z2 = (float) (ez + pz * halfWidth - camera.z);
        float x3 = (float) (ex - px * halfWidth - camera.x);
        float z3 = (float) (ez - pz * halfWidth - camera.z);
        float yy = (float) (y - camera.y);

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture), (pose, consumer) -> {
            vertex(consumer, pose, x0, yy, z0, 0.0F, 1.0F);
            vertex(consumer, pose, x1, yy, z1, 1.0F, 1.0F);
            vertex(consumer, pose, x2, yy, z2, 1.0F, 0.0F);
            vertex(consumer, pose, x3, yy, z3, 0.0F, 0.0F);
        });
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static double horizontalDistance(Point a, Point b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double angleTo(Point from, Point to) {
        return Math.atan2(to.x - from.x, to.z - from.z);
    }

    private static Point pointAt(Point center, double angle, double radius) {
        return new Point(center.x + Math.sin(angle) * radius, center.y, center.z + Math.cos(angle) * radius);
    }

    private static double approachAngle(double current, double target, double amount) {
        if (Double.isNaN(current)) return target;
        return current + wrapRadians(target - current) * amount;
    }

    private static double lerpAngle(double start, double end, double t) {
        return start + wrapRadians(end - start) * t;
    }

    private static double wrapRadians(double value) {
        while (value <= -Math.PI) value += Math.PI * 2.0D;
        while (value > Math.PI) value -= Math.PI * 2.0D;
        return value;
    }

    private record Point(double x, double y, double z) {}
}
