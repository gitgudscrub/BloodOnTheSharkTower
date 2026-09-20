# 0.4.0 — Fast-track playable core

This is the first bulk-port milestone. It deliberately combines several BOTB
subsystems instead of testing each packet as its own release.

## Restored in this batch

- `/bots` command namespace (replaces the original `/botb` root)
- `ServerState` roles / seats / death state / script / day-night state
- bulk `ClientState` core gameplay fields
- `StorytellerState` pending roles, pending seats, reminders, demon bluffs and
  core setup flags
- `Reminder` data model
- `SendSeatsS2CPayload`
- `SendDeathStatusS2CPayload`
- `SendGrimoireS2CPayload` data shape
- join/current-state broadcast now includes role, seat, death and grimoire state
- diagnostic round-trip now verifies seat/death/grimoire counts as well as
  phase/script state

The grimoire packet uses a temporary JSON-backed 26.2 StreamCodec internally so
we can restore the whole state family in one batch. Its public data shape follows
BOTB; the transport can be swapped to a lower-level codec later without changing
the rest of the architecture.

## Test commands

- `/bots`
- `/bots status`
- `/bots sync`
- `/bots testscript`
- `/bots testrole empath`
- `/bots testrole imp`
- `/bots testseat 1`
- `/bots testdead true`
- `/bots testdead false`
- `/bots testphase setup`
- `/bots testphase night`
- `/bots testphase day`

A healthy status after assigning a role/seat should report `Core state parity:
ONLINE` and `Role sync: ONLINE`.

## Next bulk batch

0.5.0 should bring across the original nomination/election/voting/execution flow
and attach the original command handlers under `/bots`.
