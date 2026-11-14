package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.BloodOnTheSharktower;
import com.sharktower.bloodonthesharktower.client.config.ClientSettings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Simple Voice Chat draws the players in your voice group as a column of
 * heads at the top-left, running down over the role card. Once per install
 * we turn it into a row along the left, between the roles breakdown at the
 * top (y 8-17) and the role card (from y 50), unless the player had already
 * moved it themselves. They can still change it in SVC's own settings; we
 * never touch it again.
 *
 * SVC is reached by reflection: only its API is on our compile classpath, and
 * the HUD must keep working if it is missing or changes.
 */
public final class VoiceGroupHud {
    private static final int DEFAULT_POS = 4;
    private static final int LEFT_X = 4;
    /** Below the roles breakdown line (y 8-17), which can span the top of the screen. */
    private static final int TOP_Y = 22;

    private VoiceGroupHud() {}

    /** Turns SVC's group list into a row under the roles breakdown, once. Safe to call repeatedly. */
    public static void moveOnce() {
        if (ClientSettings.voiceIconsMoved) return;
        try {
            Object config = config();
            if (config == null) return; // SVC not ready yet; try again on the next join
            Object posX = entry(config, "groupPlayerIconPosX");
            Object posY = entry(config, "groupPlayerIconPosY");
            Object orientation = entry(config, "groupPlayerIconOrientation");
            if (get(posX) instanceof Integer x && get(posY) instanceof Integer y && x == DEFAULT_POS && y == DEFAULT_POS) {
                set(posX, LEFT_X);
                set(posY, TOP_Y);
                Class<?> orientations = Class.forName("de.maxhenkel.voicechat.voice.client.GroupPlayerIconOrientation");
                for (Object value : orientations.getEnumConstants()) {
                    if (value.toString().equals("HORIZONTAL")) set(orientation, value);
                }
                orientation.getClass().getMethod("save").invoke(orientation);
                BloodOnTheSharktower.LOGGER.info("Moved the voice group list to a row above the role card.");
            }
            ClientSettings.voiceIconsMoved = true;
            ClientSettings.save();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            ClientSettings.voiceIconsMoved = true; // don't retry a layout we can't read
            ClientSettings.save();
            BloodOnTheSharktower.LOGGER.debug("Could not move the voice group list: {}", ex.toString());
        }
    }

    private static Object config() throws ReflectiveOperationException {
        Field field = Class.forName("de.maxhenkel.voicechat.VoicechatClient").getField("CLIENT_CONFIG");
        return field.get(null);
    }

    private static Object entry(Object config, String name) throws ReflectiveOperationException {
        return config.getClass().getField(name).get(config);
    }

    private static Object get(Object entry) throws ReflectiveOperationException {
        return entry == null ? null : entry.getClass().getMethod("get").invoke(entry);
    }

    private static void set(Object entry, Object value) throws ReflectiveOperationException {
        Method set = null;
        for (Method method : entry.getClass().getMethods()) {
            if (method.getName().equals("set") && method.getParameterCount() == 1) set = method;
        }
        if (set == null) throw new NoSuchMethodException("set");
        set.invoke(entry, value);
    }
}
