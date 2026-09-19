package com.sharktower.bloodonthesharktower.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reflection-backed bridge for the 26.2 PlayerFaceExtractor API.
 *
 * Minecraft 26.1+ renamed PlayerFaceRenderer to PlayerFaceExtractor and 26.2
 * added ResolvableProfile support. Using a tiny reflective bridge keeps the
 * private port resilient to minor signature drift while still using vanilla's
 * native skin/head extraction when it is available. A false return simply
 * means callers should draw the seat-number fallback.
 */
public final class PlayerFaceCompat {
    private static boolean resolved;
    private static Method createUnresolved;
    private static Method extract;
    private static final Map<UUID, Object> PROFILES = new HashMap<>();

    private PlayerFaceCompat() {}

    public static boolean draw(GuiGraphicsExtractor graphics, UUID playerId, int x, int y, int size) {
        if (graphics == null || playerId == null) return false;
        resolve();
        if (createUnresolved == null || extract == null) return false;
        try {
            Object profile = PROFILES.computeIfAbsent(playerId, id -> {
                try { return createUnresolved.invoke(null, id); }
                catch (ReflectiveOperationException ignored) { return null; }
            });
            if (profile == null) return false;
            extract.invoke(null, graphics, profile, x, y, size);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> profileClass = Class.forName("net.minecraft.world.item.component.ResolvableProfile");
            for (Method method : profileClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                if (!method.getName().equals("createUnresolved")) continue;
                Class<?>[] p = method.getParameterTypes();
                if (p.length == 1 && p[0] == UUID.class) {
                    createUnresolved = method;
                    break;
                }
            }

            Class<?> extractorClass = Class.forName("net.minecraft.client.gui.components.PlayerFaceExtractor");
            for (Method method : extractorClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                if (!method.getName().equals("extractRenderState")) continue;
                Class<?>[] p = method.getParameterTypes();
                if (p.length == 5
                        && GuiGraphicsExtractor.class.isAssignableFrom(p[0])
                        && p[1].getName().endsWith("ResolvableProfile")
                        && p[2] == int.class && p[3] == int.class && p[4] == int.class) {
                    extract = method;
                    break;
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            createUnresolved = null;
            extract = null;
        }
    }
}
