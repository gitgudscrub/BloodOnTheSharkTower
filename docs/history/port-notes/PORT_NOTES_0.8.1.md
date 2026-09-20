# Blood on the Sharktower 0.8.1 — BOTB Baseline Polish

This private-use milestone finishes the remaining high-value Grimoire parity work before the Sharktower-specific feature phase.

## Added

- Connected-player directory sync (UUID -> player name, connected players, Storytellers).
- Real connected-player names in the Grimoire.
- Vanilla player-head rendering bridge for connected players, with safe seat-number fallback for synthetic/offline test seats.
- Functional `Unseated: SHOW/HIDE` list. Clicking an unseated connected player seats them in the next free seat.
- Clickable reminder tokens around player roles.
- Reminder picker/editor with add, remove-one, and clear-all actions.
- Functional Script Builder for official roles. The builder loads the resulting script directly onto the server and syncs it to clients.
- Player setup screen now shows the synced player name and opens the reminder editor.
- Storyteller count now reflects the synced Storyteller directory rather than being hard-coded.
- Player directory refresh is broadcast when a player joins.

## Deliberate limitations

- Synthetic `/bots testseats` players have no real Minecraft profile, so they keep the numbered-head fallback. Real connected players use Minecraft's native player-face extraction when available.
- The Script Builder in this parity build authors official-role scripts. Custom role authoring/import workflows remain a later Sharktower feature.
- The player-face renderer is isolated behind a compatibility bridge so minor 26.2 signature drift falls back cleanly rather than breaking the Grimoire.

## Baseline intent

If this milestone passes multiplayer testing, the 0.8.x line can be frozen as the private BOTB-on-Minecraft-26.2 baseline. Sharktower-specific systems can then be added on top without changing the restored BOTB Grimoire layout unless intentionally redesigned.
