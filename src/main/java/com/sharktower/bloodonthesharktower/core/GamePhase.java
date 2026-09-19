package com.sharktower.bloodonthesharktower.core;

/**
 * Direct 26.2 port of the phase model from Blood on the Blocktower 1.3.0.
 *
 * The original class is deliberately kept small and Minecraft-independent so
 * later daytime, nomination, exile and voting systems can plug back into the
 * same phase semantics one at a time.
 */
public enum GamePhase {
    SETUP,
    NIGHT,
    DAY,
    NOMINATIONS,
    PLAYER_NOMINATED,
    PLAYER_MARKED,
    CALL_FOR_EXILE,
    EXILE_SUPPORT;

    public static GamePhase determine(
            int currentNight,
            int currentDay,
            boolean nominationsOpen,
            Object currentNominee,
            Object markedPlayer
    ) {
        return determine(
                currentNight,
                currentDay,
                nominationsOpen,
                currentNominee,
                markedPlayer,
                null,
                false
        );
    }

    public static GamePhase determine(
            int currentNight,
            int currentDay,
            boolean nominationsOpen,
            Object currentNominee,
            Object markedPlayer,
            Object exileTarget,
            boolean exileSupportActive
    ) {
        if (currentNight == 0 && currentDay == 0) {
            return SETUP;
        }

        if (currentNight != currentDay) {
            return NIGHT;
        }

        if (exileSupportActive) {
            return EXILE_SUPPORT;
        }

        if (exileTarget != null) {
            return CALL_FOR_EXILE;
        }

        if (!nominationsOpen) {
            return DAY;
        }

        if (currentNominee != null) {
            return PLAYER_NOMINATED;
        }

        if (markedPlayer != null) {
            return PLAYER_MARKED;
        }

        return NOMINATIONS;
    }
}
