package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.client.config.ClientSettings;
import net.minecraft.client.Minecraft;

/**
 * One size for the whole Sharktower HUD (role card, roles breakdown, player
 * list, voting panels, timer...), on top of Minecraft's own GUI scale. The
 * HUD layer is drawn scaled, so every HUD lays itself out against the
 * scaled screen size from here rather than the window's.
 */
public final class HudScale {
    /** The choices offered in Sharktower Settings, smallest first. */
    public static final float[] STEPS = {0.75F, 1.0F, 1.25F, 1.5F};

    private HudScale() {}

    public static float scale() {
        for (float step : STEPS) {
            if (Math.abs(step - ClientSettings.hudScale) < 0.01F) return step;
        }
        return 1.0F;
    }

    /** The next size, wrapping round from the largest to the smallest. */
    public static float next() {
        float current = scale();
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i] == current) return STEPS[(i + 1) % STEPS.length];
        }
        return 1.0F;
    }

    public static String label(float scale) {
        return Math.round(scale * 100) + "%";
    }

    /** Screen width in HUD units. */
    public static int width(Minecraft minecraft) {
        return (int) Math.floor(minecraft.getWindow().getGuiScaledWidth() / scale());
    }

    /** Screen height in HUD units. */
    public static int height(Minecraft minecraft) {
        return (int) Math.floor(minecraft.getWindow().getGuiScaledHeight() / scale());
    }
}
