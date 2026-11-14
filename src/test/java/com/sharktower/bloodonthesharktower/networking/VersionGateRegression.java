package com.sharktower.bloodonthesharktower.networking;

/** Plain Java regression executable: login version gate and version ordering. */
public final class VersionGateRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(VersionGate.refusal("1.0.2-alpha.48", "1.0.2-alpha.48") == null, "same version joins");
        check(VersionGate.refusal("1.0.2-alpha.48", null).contains("missing"), "no mod / old mod refused as missing");
        check(VersionGate.refusal("1.0.2-alpha.48", "1.0.2-alpha.47").contains("out of date"), "older client told to update");
        check(VersionGate.refusal("1.0.2-alpha.47", "1.0.2-alpha.48").contains("not updated yet"), "newer client told to wait");
        check(VersionGate.refusal("1.0.2-alpha.48", "1.0.2-alpha.47").contains("1.0.2-alpha.47"), "message names both versions");

        check(VersionGate.compare("1.0.2-alpha.9", "1.0.2-alpha.10") < 0, "pre-release numbers compare numerically");
        check(VersionGate.compare("1.0.2-alpha.47", "1.0.2") < 0, "release outranks its pre-releases");
        check(VersionGate.compare("1.0.2-alpha.47", "1.0.2-beta.1") < 0, "alpha before beta");
        check(VersionGate.compare("1.0.10", "1.0.9") > 0, "core numbers compare numerically");
        check(VersionGate.compare("1.1.0", "1.0.2") > 0, "minor beats patch");
        check(VersionGate.compare("1.0.2+abc", "1.0.2") == 0, "build metadata ignored");
        check(VersionGate.compare("1.0.2-alpha", "1.0.2-alpha.1") < 0, "shorter pre-release is lower");
        check(VersionGate.compare("0.0.0-ci", "1.0.2-alpha.48") < 0, "CI dry-run version sorts lowest");

        System.out.println("VersionGateRegression passed (" + checks + " checks).");
    }
}
