package com.sharktower.bloodonthesharktower.networking;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether a joining client may play on this server, based only on the
 * two Sharktower version strings. No Minecraft types, so it can be regression
 * tested on its own; {@link VersionCheck} does the networking.
 *
 * <p>Versions must match exactly: client and server share game-state payloads,
 * and a mismatch silently breaks features. The message tells the player what
 * to do, because "relaunch to update" is all the Prism auto-update setup needs.
 */
public final class VersionGate {
    private VersionGate() {}

    /** @return null if the client may join, otherwise the disconnect text. */
    public static String refusal(String serverVersion, String clientVersion) {
        if (clientVersion == null) {
            return "This server needs the Blood on the Sharktower pack, version " + serverVersion + ".\n\n"
                    + "Your pack is missing or too old to check.\n"
                    + "Get the Sharktower Prism Launcher instance, which keeps itself up to date\n"
                    + "(ask in the Sharktower Discord), or update the pack in your launcher.";
        }
        if (clientVersion.equals(serverVersion)) {
            return null;
        }
        if (compare(clientVersion, serverVersion) < 0) {
            return "Your Blood on the Sharktower pack is out of date.\n\n"
                    + "Server: " + serverVersion + "    You: " + clientVersion + "\n\n"
                    + "Close Minecraft and press Launch again to update.\n"
                    + "(Modrinth App: press Update on the pack first.)";
        }
        return "The server has not updated yet.\n\n"
                + "Server: " + serverVersion + "    You: " + clientVersion + "\n\n"
                + "It should catch up soon. Try again in a few minutes.";
    }

    /**
     * Semver-style order: numeric core first, a release outranks its
     * pre-releases (1.0.2 > 1.0.2-alpha.47), pre-release parts compare
     * numerically when both are numbers (alpha.9 < alpha.10).
     */
    public static int compare(String a, String b) {
        String[] pa = splitPre(a);
        String[] pb = splitPre(b);
        int core = compareParts(parts(pa[0], "\\."), parts(pb[0], "\\."));
        if (core != 0) return core;
        if (pa[1] == null || pb[1] == null) {
            if (pa[1] == null && pb[1] == null) return 0;
            return pa[1] == null ? 1 : -1;
        }
        return compareParts(parts(pa[1], "\\."), parts(pb[1], "\\."));
    }

    private static String[] splitPre(String version) {
        String v = version.trim();
        int plus = v.indexOf('+');
        if (plus >= 0) v = v.substring(0, plus);
        int dash = v.indexOf('-');
        return dash < 0 ? new String[] {v, null} : new String[] {v.substring(0, dash), v.substring(dash + 1)};
    }

    private static List<String> parts(String s, String regex) {
        List<String> out = new ArrayList<>();
        for (String part : s.split(regex)) out.add(part);
        return out;
    }

    private static int compareParts(List<String> a, List<String> b) {
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            if (i >= a.size()) return -1;
            if (i >= b.size()) return 1;
            String x = a.get(i);
            String y = b.get(i);
            boolean xn = x.matches("\\d+");
            boolean yn = y.matches("\\d+");
            int c;
            if (xn && yn) {
                c = Long.compare(Long.parseLong(x), Long.parseLong(y));
            } else if (xn != yn) {
                c = xn ? -1 : 1;
            } else {
                c = x.compareTo(y);
            }
            if (c != 0) return c;
        }
        return 0;
    }
}
