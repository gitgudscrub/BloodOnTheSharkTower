package com.sharktower.bloodonthesharktower.core;

/**
 * Temporary storage bridge for ServerState until the full BOTB Script parser,
 * custom roles and packet codecs are ported.
 */
public record PortedScript(String name, String author, String rawJson) {
}
