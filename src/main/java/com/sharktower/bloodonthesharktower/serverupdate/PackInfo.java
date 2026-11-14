package com.sharktower.bloodonthesharktower.serverupdate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The three values the server updater needs from a packwiz {@code pack.toml}:
 * the pack version and the Minecraft / Fabric versions it targets. A tiny
 * line parser is enough because the publish workflow writes the file.
 */
public record PackInfo(String version, String minecraft, String fabric) {
    private static final Pattern LINE = Pattern.compile("^\\s*([A-Za-z_-]+)\\s*=\\s*\"([^\"]*)\"\\s*$");

    public static PackInfo parse(String toml) {
        String version = null;
        String minecraft = null;
        String fabric = null;
        String section = "";
        for (String raw : toml.split("\\R")) {
            String line = raw.trim();
            if (line.startsWith("[")) {
                section = line;
                continue;
            }
            Matcher m = LINE.matcher(line);
            if (!m.matches()) continue;
            if (section.isEmpty() && m.group(1).equals("version")) version = m.group(2);
            if (section.equals("[versions]") && m.group(1).equals("minecraft")) minecraft = m.group(2);
            if (section.equals("[versions]") && m.group(1).equals("fabric")) fabric = m.group(2);
        }
        if (version == null || minecraft == null) {
            throw new IllegalArgumentException("pack.toml is missing version or [versions] minecraft");
        }
        return new PackInfo(version, minecraft, fabric);
    }
}
