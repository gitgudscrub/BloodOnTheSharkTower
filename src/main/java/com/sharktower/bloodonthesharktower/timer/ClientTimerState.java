package com.sharktower.bloodonthesharktower.timer;

/** Client-side logical timer state from the original BOTB timer subsystem. */
public final class ClientTimerState {
    public static boolean isActive = false;
    public static boolean isPaused = false;
    public static int remainingSeconds = 0;
    public static int totalSeconds = 0;

    private ClientTimerState() {}

    public static void updateTimerState(boolean active, boolean paused, int remaining, int total) {
        isActive = active;
        isPaused = paused;
        remainingSeconds = Math.max(0, remaining);
        totalSeconds = Math.max(0, total);
    }

    public static boolean hasActiveTimer() { return isActive; }
}
