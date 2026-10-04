package com.sharktower.bloodonthesharktower.core;

import java.util.*;

/** Pure, server-side identity plan. No recipient receives actual role assignments. */
public final class TeamInformation {
    private TeamInformation() {}
    public record Info(List<UUID> demons, List<UUID> minions, List<UUID> marionettes, boolean withheld) {}
    public record Plan(Map<UUID, Info> recipients, boolean magician, boolean withheld) {}

    public static Plan plan(Map<UUID, PendingRoleAssignment> roles, Map<UUID, Integer> seats,
                            Map<UUID, Boolean> deaths, boolean demonInfo,
                            boolean allowMagician, boolean allowWithheld) {
        var players = roles.keySet().stream().filter(seats::containsKey)
                .sorted(Comparator.<UUID>comparingInt(seats::get).thenComparing(UUID::toString)).toList();
        long count = players.stream().filter(id -> roles.get(id).getRoleType() != RoleType.TRAVELER).count();
        if (count < 7 && !allowWithheld) throw new IllegalArgumentException("No starting team information below 7 players. The ST may explicitly override this rule.");
        if (players.stream().anyMatch(id -> is(roles.get(id), Role.LEGION)))
            throw new IllegalArgumentException("Legion needs different information. Use a manual night visit for this setup.");
        boolean withheld = !allowWithheld && players.stream().anyMatch(id -> is(roles.get(id), Role.POPPY_GROWER));
        // Keep Poppy Grower identities withheld even after death until the ST explicitly
        // authorizes them: a drunk/poisoned death does not automatically release info.
        boolean magician = allowMagician
                && players.stream().anyMatch(id -> is(roles.get(id), Role.MAGICIAN) && !deaths.getOrDefault(id, false))
                && players.stream().noneMatch(id -> is(roles.get(id), Role.VIZIER));
        var demons = players.stream().filter(id -> roles.get(id).getRoleType() == RoleType.DEMON
                || (magician && is(roles.get(id), Role.MAGICIAN) && !deaths.getOrDefault(id, false))).toList();
        var minions = players.stream().filter(id -> (roles.get(id).getRoleType() == RoleType.MINION && !is(roles.get(id), Role.MARIONETTE))
                || (demonInfo && magician && is(roles.get(id), Role.MAGICIAN) && !deaths.getOrDefault(id, false))).toList();
        var marionettes = players.stream().filter(id -> is(roles.get(id), Role.MARIONETTE)).toList();
        var result = new LinkedHashMap<UUID, Info>();
        for (UUID id : players) {
            var role = roles.get(id);
            if (role.getRoleType() != (demonInfo ? RoleType.DEMON : RoleType.MINION)
                    || is(role, Role.MARIONETTE)) continue;
            var shownMinions = new ArrayList<>(minions.stream().filter(other -> !other.equals(id)).toList());
            // With a living Magician, the Demon must not learn which neighbour is
            // their Marionette. Otherwise identify it separately for the Demon.
            if (demonInfo && !magician) shownMinions.addAll(marionettes);
            shownMinions.sort(Comparator.<UUID>comparingInt(seats::get).thenComparing(UUID::toString));
            result.put(id, new Info(withheld || demonInfo ? List.of() : demons,
                    withheld ? List.of() : List.copyOf(shownMinions),
                    withheld || !demonInfo || magician ? List.of() : marionettes, withheld));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("No eligible recipients have committed roles and seats.");
        return new Plan(Collections.unmodifiableMap(result), magician, withheld);
    }
    private static boolean is(PendingRoleAssignment assignment, Role role) {
        return assignment.isOfficialRole() && assignment.role() == role;
    }
}
