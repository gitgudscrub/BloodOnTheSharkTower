package com.sharktower.bloodonthesharktower.states;

import com.sharktower.bloodonthesharktower.core.AlignmentOverride;
import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.setup.SetupOperations;

import java.util.UUID;

/**
 * Plain Java regression executable: Storyteller control is open to anyone during
 * Setup and locked while a game holds hidden information.
 */
public final class StorytellerClaimRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void freshSetup() {
        SetupOperations.clearRolesForFreshSetup();
        ServerState.PLAYER_SEAT_NUMBERS.clear();
        ServerState.currentNight = 0;
        ServerState.currentDay = 0;
        ServerState.gameEnded = false;
        ServerState.rolesRevealed = false;
        StorytellerState.STORYTELLERS.clear();
    }

    public static void main(String[] args) {
        UUID storyteller = UUID.randomUUID(), player = UUID.randomUUID(), latecomer = UUID.randomUUID();
        PendingRoleAssignment empath = new PendingRoleAssignment(Role.EMPATH, AlignmentOverride.DEFAULT);
        PendingRoleAssignment none = new PendingRoleAssignment(Role.NO_ROLE, AlignmentOverride.DEFAULT);

        freshSetup();
        check(!StorytellerState.gameInProgress(), "empty server is Setup");
        check(StorytellerState.claimRefusal(storyteller, false) == null, "anyone may claim during Setup");
        StorytellerState.claimStoryteller(storyteller);
        check(StorytellerState.claimRefusal(latecomer, false) == null, "a second Storyteller may claim during Setup");

        // Seated players with no role yet are still Setup, even with a placeholder assignment.
        ServerState.PLAYER_SEAT_NUMBERS.put(player, 1);
        ServerState.PLAYER_ROLES.put(player, none);
        check(!StorytellerState.gameInProgress(), "unassigned seats do not lock control");
        StorytellerState.PENDING_ROLES.put(player, empath);
        check(!StorytellerState.gameInProgress(), "pending (unsent) roles do not lock control");

        // Send Roles commits hidden information: locked from this moment, before Dusk.
        ServerState.PLAYER_ROLES.put(player, empath);
        check(StorytellerState.gameInProgress(), "committed roles lock control before the first Night");
        check(StorytellerState.claimRefusal(latecomer, false) != null, "ordinary player refused once roles are sent");
        check(StorytellerState.claimRefusal(storyteller, false) == null, "existing Storyteller keeps control (reconnect)");
        check(StorytellerState.claimRefusal(latecomer, true) == null, "operator may override the lock");

        ServerState.currentNight = 1;
        check(StorytellerState.claimRefusal(latecomer, false) != null, "locked during Night");
        ServerState.currentDay = 1;
        check(StorytellerState.claimRefusal(latecomer, false) != null, "locked during Day");

        // Releasing control mid-game does not reopen it to everyone.
        StorytellerState.releaseStoryteller(storyteller);
        check(StorytellerState.STORYTELLERS.isEmpty() && StorytellerState.claimRefusal(latecomer, false) != null,
                "release during a game leaves control locked for ordinary players");

        // End game: still locked until the reveal publishes every role.
        ServerState.gameEnded = true;
        check(StorytellerState.claimRefusal(latecomer, false) != null, "end-game before reveal stays locked");
        ServerState.rolesRevealed = true;
        check(!StorytellerState.gameInProgress(), "final reveal makes roles public");
        check(StorytellerState.claimRefusal(latecomer, false) == null, "after the reveal anyone may claim to run the reset");

        // Reset returns to Setup and unlocks normally.
        freshSetup();
        ServerState.PLAYER_SEAT_NUMBERS.put(player, 1);
        check(StorytellerState.claimRefusal(latecomer, false) == null, "reset reopens Storyteller control");

        System.out.println("PASS: " + checks + " Storyteller claim lock regression checks");
    }
}
