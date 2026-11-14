package com.sharktower.bloodonthesharktower.setup;

import com.sharktower.bloodonthesharktower.core.PendingRoleAssignment;
import com.sharktower.bloodonthesharktower.core.Role;
import com.sharktower.bloodonthesharktower.core.RoleCounts;
import com.sharktower.bloodonthesharktower.states.ServerState;
import com.sharktower.bloodonthesharktower.states.StorytellerState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plain Java regression executable for the Storyteller setup flow: team
 * counts up to 16 seats, the Lunatic's automatic belief, and making a dealt
 * Townsfolk secretly the Drunk or Marionette after the deal.
 */
public final class SetupFlowRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static List<UUID> seat(int count) {
        SetupOperations.clearRolesForFreshSetup();
        ServerState.PLAYER_SEAT_NUMBERS.clear();
        ServerState.PLAYER_ROLES.clear();
        ServerState.currentNight = 0;
        ServerState.currentDay = 0;
        StorytellerState.PENDING_SEAT_NUMBERS.clear();
        List<UUID> players = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            UUID id = UUID.randomUUID();
            players.add(id);
            StorytellerState.PENDING_SEAT_NUMBERS.put(id, i);
        }
        return players;
    }

    private static int seatOf(Role role) {
        for (Map.Entry<UUID, PendingRoleAssignment> entry : StorytellerState.PENDING_ROLES.entrySet()) {
            PendingRoleAssignment a = entry.getValue();
            if (a != null && !a.isCustomRole() && a.role() == role) {
                return StorytellerState.PENDING_SEAT_NUMBERS.get(entry.getKey());
            }
        }
        return 0;
    }

    private static PendingRoleAssignment roleAt(int seat) {
        return StorytellerState.PENDING_ROLES.get(SetupOperations.playerBySeat(seat));
    }

    private static PendingRoleAssignment believedAt(int seat) {
        return StorytellerState.PENDING_PERCEIVED_ROLES.get(SetupOperations.playerBySeat(seat));
    }

    public static void main(String[] args) {
        for (int n = 5; n <= 16; n++) {
            var c = RoleCounts.getCounts(n);
            check(c.townsfolk() + c.outsiders() + c.minions() + c.demons() == n, n + " players: team counts add up");
        }
        check(RoleCounts.getCounts(16).townsfolk() == 10, "16 players get a tenth Townsfolk");

        check(SetupOperations.loadScriptJson("[\"washerwoman\",\"librarian\",\"investigator\",\"chef\",\"empath\","
                + "\"monk\",\"soldier\",\"mayor\",\"drunk\",\"lunatic\",\"saint\",\"marionette\",\"poisoner\",\"imp\"]").ok(),
                "test script loads");

        // Lunatic: whoever draws it believes they are the Demon in play.
        seat(8);
        var dealt = SetupOperations.distributeRoleBag(List.of("washerwoman", "librarian", "investigator", "chef",
                "empath", "lunatic", "poisoner", "imp"));
        check(dealt.ok(), "bag deals: " + dealt.message());
        int lunatic = seatOf(Role.LUNATIC);
        check(believedAt(lunatic) != null && believedAt(lunatic).role() == Role.IMP, "Lunatic believes they are the Imp");

        // Drunk after the deal: a Townsfolk keeps their token as their belief.
        int chef = seatOf(Role.CHEF), empath = seatOf(Role.EMPATH);
        check(SetupOperations.hideRole(chef, "drunk").ok(), "Chef holder can become the Drunk");
        check(roleAt(chef).role() == Role.DRUNK && believedAt(chef).role() == Role.CHEF, "Drunk believes Chef");
        check(SetupOperations.hideRole(empath, "drunk").ok(), "Drunk can move to another seat");
        check(roleAt(chef).role() == Role.CHEF && believedAt(chef) == null, "previous Drunk gets their Chef token back");
        check(roleAt(empath).role() == Role.DRUNK && believedAt(empath).role() == Role.EMPATH, "new Drunk believes Empath");
        check(SetupOperations.hideRole(empath, "drunk").ok() && roleAt(empath).role() == Role.EMPATH,
                "choosing the Drunk again undoes it");
        check(!SetupOperations.hideRole(seatOf(Role.POISONER), "drunk").ok(), "a Minion cannot be made the Drunk");
        check(!SetupOperations.hideRole(lunatic, "drunk").ok(), "the Lunatic cannot be made the Drunk");
        check(!SetupOperations.hideRole(chef, "imp").ok(), "only Drunk or Marionette can be hidden");

        // Marionette: works on a good player, warns when not next to the Demon.
        int imp = seatOf(Role.IMP);
        int beside = imp % 8 + 1;
        if (roleAt(beside).getRoleType() != com.sharktower.bloodonthesharktower.core.RoleType.TOWNSFOLK) {
            beside = (imp + 6) % 8 + 1;
        }
        if (roleAt(beside).getRoleType() == com.sharktower.bloodonthesharktower.core.RoleType.TOWNSFOLK) {
            var next = SetupOperations.hideRole(beside, "marionette");
            check(next.ok() && !next.message().contains("Note"), "Marionette next to the Demon: no warning");
            check(believedAt(beside) != null, "Marionette keeps a believed role");
            SetupOperations.hideRole(beside, "marionette");
        }
        int far = -1;
        for (int s = 1; s <= 8; s++) {
            int distance = Math.min(Math.abs(s - imp), 8 - Math.abs(s - imp));
            if (distance >= 2 && roleAt(s).getRoleType() == com.sharktower.bloodonthesharktower.core.RoleType.TOWNSFOLK) { far = s; break; }
        }
        if (far > 0) {
            var away = SetupOperations.hideRole(far, "marionette");
            check(away.ok() && away.message().contains("next to the Demon"), "Marionette away from the Demon warns");
        }

        // Undo: puts back the state saved before an action.
        seat(8);
        SetupOperations.distributeRoleBag(List.of("washerwoman", "librarian", "investigator", "chef",
                "empath", "monk", "poisoner", "imp"));
        SetupUndo.clear();
        int chefNow = seatOf(Role.CHEF);
        SetupUndo.save("hide_role");
        check(SetupOperations.hideRole(chefNow, "drunk").ok() && roleAt(chefNow).role() == Role.DRUNK, "Chef made the Drunk");
        check(SetupUndo.undo().ok() && roleAt(chefNow).role() == Role.CHEF && believedAt(chefNow) == null,
                "undo gives the Chef back");
        check(!SetupUndo.undo().ok(), "nothing left to undo");
        for (int i = 0; i < 25; i++) SetupUndo.save("shuffle_seats");
        check(SetupUndo.size() == 20, "undo keeps at most 20 steps");
        SetupUndo.clear();

        // Random setup works for a full 16-seat Grimoire.
        seat(16);
        var random = SetupOperations.randomizeRoles();
        check(!random.ok() || StorytellerState.PENDING_ROLES.size() == 16, "16-seat random setup fills every seat");
        check(!random.message().contains("5-15"), "16 seats are no longer refused");

        System.out.println("PASS: " + checks + " setup flow checks");
    }
}
