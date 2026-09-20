package com.sharktower.bloodonthesharktower.client.gui.grimoire;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Original BOTB-inspired staggered Grim reveal.
 *
 * Each seat starts 50ms after the previous one and eases from 82% to full size
 * over 200ms. This keeps the original timing while avoiding a hard pop-in on
 * the modern render-state API.
 */
public final class GrimoireRevealAnimation {
    public static final long ELEMENT_DELAY_MS = 50L;
    public static final long DURATION_MS = 200L;

    private static long startedAtMs = System.currentTimeMillis();

    private GrimoireRevealAnimation() {}

    public static void beginScreen() {
        startedAtMs = System.currentTimeMillis();
    }

    public static void showImmediately() {
        startedAtMs = System.currentTimeMillis() - 60_000L;
    }

    public static float progressForSeat(int seat) {
        return progressForIndex(Math.max(0, seat - 1));
    }

    public static float progressForIndex(int index) {
        long delay = Math.max(0, index) * ELEMENT_DELAY_MS;
        long elapsed = System.currentTimeMillis() - startedAtMs - delay;
        if (elapsed <= 0L) return 0.0F;
        if (elapsed >= DURATION_MS) return 1.0F;

        float t = elapsed / (float) DURATION_MS;
        // Smoothstep: 3t^2 - 2t^3.
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Push a subtle centre-origin scale transform for one Grim element.
     * Returns false while that stagger slot has not begun revealing yet.
     */
    public static boolean beginElement(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            float progress
    ) {
        if (progress <= 0.0F) return false;

        float scale = 0.82F + 0.18F * Math.min(1.0F, progress);
        float cx = x + width / 2.0F;
        float cy = y + height / 2.0F;

        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-cx, -cy);
        return true;
    }

    public static void endElement(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
    }
}
