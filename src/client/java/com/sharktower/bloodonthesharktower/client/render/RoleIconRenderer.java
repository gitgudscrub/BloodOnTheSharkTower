package com.sharktower.bloodonthesharktower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sharktower.bloodonthesharktower.client.ClientGrimoireEdits;
import com.sharktower.bloodonthesharktower.client.config.ClientSettings;
import com.sharktower.bloodonthesharktower.client.gui.UiDrawing;
import com.sharktower.bloodonthesharktower.client.hud.GameEndAnimationHUD;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.ScriptRole;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Floating BOTC role tokens based only on what this particular client knows or has noted. */
public final class RoleIconRenderer {
    private static final double TRUE_ICON_SIZE = 0.56D;
    private static final double PERCEIVED_ICON_SIZE = 0.36D;
    private static final double PAIR_GAP = 0.08D;
    private static final double HEAD_OFFSET = 0.72D;

    private RoleIconRenderer() {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(context ->
                render(context.submitNodeCollector(), context.poseStack()));
    }

    private static void render(SubmitNodeCollector collector, PoseStack poseStack) {
        if (!ClientSettings.worldRoleIcons || GameEndAnimationHUD.isAnimating()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (!ClientGrimoireEdits.hasVisibleRoleNotes()) return;

        UUID localId = minecraft.player.getUUID();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();

        for (Player player : minecraft.level.players()) {
            UUID id = player.getUUID();
            if (id.equals(localId)) continue;

            PendingRoleAssignment assignment = ClientGrimoireEdits.roleFor(id);
            PendingRoleAssignment perceived = ClientGrimoireEdits.perceivedRoleFor(id);

            // An empty Grim slot is setup state, not a role. Do not turn the empty
            // placeholder square into a floating world icon above the player.
            boolean hasActualRole = hasAssignedRole(assignment);
            boolean hasPerceivedRole = hasAssignedRole(perceived);
            if (!hasActualRole && !hasPerceivedRole) continue;

            Identifier actualTexture = hasActualRole ? iconOrPlaceholder(assignment) : null;
            Identifier perceivedTexture = shouldShowPerceivedSlot(assignment, perceived)
                    ? iconOrPlaceholder(perceived)
                    : null;

            double cx = player.getX();
            double cy = player.getY() + player.getBbHeight() + HEAD_OFFSET;
            double cz = player.getZ();

            if (actualTexture != null && perceivedTexture != null) {
                // Centre the pair as a unit, but keep the true role visibly larger.
                double totalWidth = TRUE_ICON_SIZE + PAIR_GAP + PERCEIVED_ICON_SIZE;
                double trueOffset = -(totalWidth / 2.0) + TRUE_ICON_SIZE / 2.0;
                double perceivedOffset = (totalWidth / 2.0) - PERCEIVED_ICON_SIZE / 2.0;
                submitBillboard(collector, poseStack, camera, actualTexture, cx, cy, cz,
                        trueOffset, TRUE_ICON_SIZE);
                submitBillboard(collector, poseStack, camera, perceivedTexture, cx, cy, cz,
                        perceivedOffset, PERCEIVED_ICON_SIZE);
            } else if (actualTexture != null) {
                submitBillboard(collector, poseStack, camera, actualTexture, cx, cy, cz,
                        0.0D, TRUE_ICON_SIZE);
            } else if (perceivedTexture != null) {
                submitBillboard(collector, poseStack, camera, perceivedTexture, cx, cy, cz,
                        0.0D, PERCEIVED_ICON_SIZE);
            }
        }
    }


    private static boolean hasAssignedRole(PendingRoleAssignment assignment) {
        return assignment != null
                && assignment.getRoleId() != null
                && !assignment.getRoleId().isBlank()
                && !"none".equalsIgnoreCase(assignment.getRoleId());
    }

    private static Identifier iconOrPlaceholder(PendingRoleAssignment assignment) {
        if (assignment == null || "none".equalsIgnoreCase(assignment.getRoleId())) {
            return UiDrawing.emptyRoleSlotTexture();
        }
        ScriptRole role = assignment.getScriptRole();
        return role == null || role.getIcon() == null
                ? UiDrawing.emptyRoleSlotTexture()
                : role.getIcon();
    }

    private static boolean shouldShowPerceivedSlot(PendingRoleAssignment actual, PendingRoleAssignment perceived) {
        if (perceived != null && !"none".equalsIgnoreCase(perceived.getRoleId())) return true;
        if (actual == null || actual.getRoleId() == null) return false;
        return "drunk".equalsIgnoreCase(actual.getRoleId())
                || "marionette".equalsIgnoreCase(actual.getRoleId());
    }

    private static void submitBillboard(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            Vec3 camera,
            Identifier texture,
            double cx,
            double cy,
            double cz,
            double horizontalOffset,
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
        cx += rx * horizontalOffset;
        cz += rz * horizontalOffset;

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
