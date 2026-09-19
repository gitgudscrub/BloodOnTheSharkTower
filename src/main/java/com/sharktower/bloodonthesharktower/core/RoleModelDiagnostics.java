package com.sharktower.bloodonthesharktower.core;

import java.util.Optional;

/**
 * Small launch-time smoke test for the newly ported role/script model.
 */
public final class RoleModelDiagnostics {
    private static final String SMOKE_SCRIPT = """
            [
              {"id":"_meta","name":"Sharktower Port Smoke Test","author":"Sharktower"},
              "chef",
              "imp",
              {"id":"sharktest","name":"Shark Test","team":"outsider","ability":"Diagnostic custom role"},
              {"id":"sharkfabled","name":"Shark Fabled","team":"fabled","ability":"Diagnostic fabled"},
              {"id":"sharkloric","name":"Shark Loric","team":"loric","ability":"Diagnostic loric"}
            ]
            """;

    private RoleModelDiagnostics() {}

    public static Result run() {
        Optional<Script> parsed = Script.fromJson(SMOKE_SCRIPT);
        int enumEntries = Role.values().length;
        int selectable = Role.SELECTABLE_ROLES.size();
        int parsedRoles = parsed.map(script -> script.allRoles().size()).orElse(0);
        boolean customFound = parsed.flatMap(script -> script.getCustomRole("sharktest")).isPresent();
        boolean fabledFound = parsed.flatMap(script -> script.getFabledOrLoric("sharkfabled"))
                .filter(role -> role instanceof ScriptRole.Fabled fabled && fabled.isFabled())
                .isPresent();
        boolean loricFound = parsed.flatMap(script -> script.getFabledOrLoric("sharkloric"))
                .filter(role -> role instanceof ScriptRole.Fabled fabled && fabled.isLoric())
                .isPresent();

        boolean ok = enumEntries == 182
                && selectable == 181
                && parsedRoles == 3
                && customFound
                && fabledFound
                && loricFound
                && Role.findById("fortune_teller") == Role.FORTUNE_TELLER
                && Role.findById("no-dashii") == Role.NO_DASHII;

        return new Result(ok, enumEntries, selectable, parsedRoles, customFound, fabledFound, loricFound);
    }

    public record Result(
            boolean ok,
            int enumEntries,
            int selectableRoles,
            int parsedSmokeRoles,
            boolean customRoleParsed,
            boolean fabledParsed,
            boolean loricParsed
    ) {}
}
