package com.sharktower.bloodonthesharktower.daytime;

/** Server-authoritative presentation timing used by the clockwise vote clock. */
public final class VotePresentationSettings {
    public static final int DEFAULT_STEP_TICKS = 20;
    private static int stepTicks = DEFAULT_STEP_TICKS;

    private VotePresentationSettings() {}

    public static int stepTicks() { return stepTicks; }

    public static int stepMillis() { return stepTicks * 50; }

    public static void setStepTicks(int ticks) {
        stepTicks = Math.max(10, Math.min(60, ticks));
    }
}
