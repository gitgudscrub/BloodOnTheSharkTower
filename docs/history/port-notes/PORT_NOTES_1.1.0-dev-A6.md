# Blood on the Sharktower 1.1.0-dev A.6 — Match Snapshot & World Restore

A.6 changes Full Reset from a destructive wipe into a safe return to the clean state captured when roles are committed.

## Start-of-game checkpoint

`Send Roles` / setup commit now creates one immutable match snapshot containing:

- committed seats and roles
- loaded script
- Storyteller assignment
- reminders and Demon bluffs
- seat homes / town-square seat positions
- clock centre and clock-hand scale
- clean alive/dead setup state

Nothing that happens during the game writes back into this checkpoint.

## Optional world checkpoint

The Storyteller can define an Overworld restore cuboid before starting the game:

- `/bots snapshot corner1`
- `/bots snapshot corner2`
- `/bots snapshot status`

At `Send Roles`, that region is captured using vanilla structure files. Large regions are split into 48x48x48 tiles automatically. Blocks, block entities and non-player map entities are restored. Players are never cloned. Runtime non-player entities inside the region are cleared before the clean snapshot entities are placed back.

The world region is optional. Without it, Sharktower still snapshots and restores the game/setup state.

## Reset / completion

- **Full Reset** (`resetGameHard`) restores the current start-of-game snapshot if one exists.
- `/bots gameComplete` performs the same safe restore.
- `/bots snapshot restore` is an explicit recovery alias.
- `/bots snapshot restorePrevious` restores the previous start snapshot kept before a new one replaces it.

Restore discards runtime-only state such as deaths during play, ghost-vote use, nominations, hands, block status, active vote state, timers and private/night voice rooms.

## Previous snapshot protection

When a new match snapshot is captured, the previous setup snapshot is retained and the prior world-structure files are copied to the `match_previous` structure set before the new checkpoint is written.

## Current limitation

A.6 world-region capture is intentionally Overworld-only for the first pass. This matches the normal Sharktower map layout and avoids silently restoring a region in the wrong dimension. Multi-dimension snapshots can be added later if needed.
