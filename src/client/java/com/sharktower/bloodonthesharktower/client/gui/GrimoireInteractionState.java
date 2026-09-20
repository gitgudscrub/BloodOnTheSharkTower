package com.sharktower.bloodonthesharktower.client.gui;

import java.util.UUID;

/**
 * Small local interaction state for fast Storyteller actions from the Grimoire.
 *
 * A nomination is deliberately a two-step interaction: choose a nominator, then
 * choose a nominee. The server remains authoritative and still performs every
 * normal legality check when the pair is submitted.
 */
public final class GrimoireInteractionState {
    private static UUID selectedNominator;

    private GrimoireInteractionState() {}

    public static UUID selectedNominator() {
        return selectedNominator;
    }

    public static boolean hasSelectedNominator() {
        return selectedNominator != null;
    }

    public static boolean isSelectedNominator(UUID playerId) {
        return playerId != null && playerId.equals(selectedNominator);
    }

    public static void selectNominator(UUID playerId) {
        selectedNominator = playerId;
    }

    public static void toggleNominator(UUID playerId) {
        if (playerId == null) {
            selectedNominator = null;
            return;
        }
        selectedNominator = playerId.equals(selectedNominator) ? null : playerId;
    }

    public static void clearNominator() {
        selectedNominator = null;
    }
}
