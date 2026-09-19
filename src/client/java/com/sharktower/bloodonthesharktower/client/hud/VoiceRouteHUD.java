package com.sharktower.bloodonthesharktower.client.hud;

import com.sharktower.bloodonthesharktower.client.event.KeyInputHandler;
import com.sharktower.bloodonthesharktower.states.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

/**
 * Compact local-only indicator for Sharktower voice routing.
 *
 * A.11 follow-up: render the route in the same bottom-centre text slot used by
 * the small nominations-open helper instead of as a large top-of-screen panel,
 * so it no longer overlaps the Storyteller phase / night-order HUD at Night.
 */
public final class VoiceRouteHUD {
    private static final int ROUTE_GREEN = 0xFF55FF55;
    private static final int ROUTE_AQUA = 0xFF55FFFF;
    private static final int ROUTE_GOLD = 0xFFFFD966;
    private static final int ROUTE_MUTED = 0xFFB0B0B0;

    private VoiceRouteHUD() {}

    public static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!ClientState.isHudEnabled || minecraft.player == null) return;

        RouteLabel label = describe(ClientState.voiceRoute);
        if (label == null || label.text().isBlank()) return;

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = (screenWidth - minecraft.font.width(label.text())) / 2;
        int y = screenHeight - 82;
        graphics.text(minecraft.font, label.text(), x, y, label.colour(), true);
    }

    private static RouteLabel describe(String rawRoute) {
        if (rawRoute == null || rawRoute.isBlank()) return null;
        String route = rawRoute.trim();

        if ("PROXIMITY".equals(route) || "DAY_SHARED".equals(route)) return null;

        if (route.startsWith("DAY_ZONE:")) {
            String zone = route.substring("DAY_ZONE:".length()).trim();
            if (zone.isEmpty() || ClientState.nominationsOpen) return null;
            return new RouteLabel("PRIVATE CHAT: " + prettyZone(zone), ROUTE_GREEN);
        }

        return switch (route) {
            case "SHARED_NIGHT" -> new RouteLabel("NIGHT CHAT", ROUTE_GREEN);
            case "SHARED_NIGHT_STORYTELLER" -> new RouteLabel("NIGHT CHAT - STORYTELLER", ROUTE_GOLD);
            case "PRIVATE" -> new RouteLabel("PRIVATE CHAT", ROUTE_GOLD);
            case "PRIVATE_STORYTELLER" -> new RouteLabel("PRIVATE CHAT - STORYTELLER", ROUTE_GOLD);
            case "PRIVATE_HOLD" -> new RouteLabel(privateHoldText(), ROUTE_GOLD);
            case "WAITING" -> new RouteLabel("VOICE CHAT CONNECTING", ROUTE_MUTED);
            default -> route.startsWith("DAY_ZONE_")
                    ? new RouteLabel("PRIVATE CHAT: " + prettyZone(route.substring("DAY_ZONE_".length())), ROUTE_GREEN)
                    : new RouteLabel(route.replace('_', ' '), ROUTE_AQUA);
        };
    }

    private static String privateHoldText() {
        String key = KeyInputHandler.leavePrivateChatKey == null
                ? "J"
                : KeyInputHandler.leavePrivateChatKey.getTranslatedKeyMessage().getString();
        return "PRIVATE HOLD - [" + key + "] TO LEAVE";
    }

    private static String prettyZone(String raw) {
        String cleaned = raw.replace('-', ' ').replace('_', ' ').trim();
        if (cleaned.isEmpty()) return "Unknown";
        String[] parts = cleaned.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            String lower = part.toLowerCase(Locale.ROOT);
            builder.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) builder.append(lower.substring(1));
        }
        return builder.toString();
    }

    private record RouteLabel(String text, int colour) {}
}
