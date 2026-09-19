# Blood on the Sharktower — 1.1.0-dev A.11.0

## Setup disconnect seat compaction

- Added automatic seat release when an ordinary seated player disconnects during Setup.
- Remaining setup seats are compacted into contiguous 1..N order.
- Pending/live roles and setup metadata for the departed player are removed.
- Active-game disconnects retain seats so reconnect behavior is unchanged.
- End-game reveal retains the completed roster until the Storyteller resets.
- Existing start-of-game setup snapshots are refreshed in memory after setup roster cleanup without touching the captured world snapshot or previous-game checkpoint.

## Player awareness / world role presentation

- Added a compact top-centre public role-count strip matching the source mod: Townsfolk : Outsiders : Minions : Demons, with Travellers appended when present.
- Counts come from the standard BOTC player-count table rather than the actual bag, so hidden setup modifiers cannot leak through the HUD.
- Added floating role icons above players whenever that role is present in the local client's Grimoire knowledge.
- Storytellers therefore see the complete working Grim above players; ordinary players see only roles they are entitled to know; the full set becomes visible to everyone after Final Grimoire reveal.
- Hardened ordinary-player Grimoire sync so hidden roles, reminders and Demon bluffs are no longer sent wholesale to every client. Public Traveller characters remain visible without exposing their alignment.
- Added local settings toggles for Role Counts and World Roles.

## Storyteller phase / night-order bar

- Added the original-style Storyteller top-centre phase bar after roles are committed.
- Left/Right Arrow moves the selected phase/night-order entry; Up Arrow activates it; N toggles the bar.
- Dusk starts Night, Dawn starts Day, and Nominations opens nominations from the same bar.
- During Night, regular character wake-ups are inserted automatically in first-night / other-night order from the Storyteller's Grimoire.
- Selecting a character and pressing Up teleports the Storyteller to that player's configured house and sends the player the existing private-chat Join button.
- First-night-only characters such as Chef appear for Night 1 and are removed from the later-night bar automatically.
- Custom script roles with firstNight / otherNight ordering values are supported.
- Undertaker is included on later nights when an execution occurred that day.
- Event/death-triggered characters (for example Ravenkeeper, Barber or Sage) are not auto-synthesized yet because their exact trigger is not represented by the current synchronized game state; those visits remain manual for this pass.
- Once roles are committed, the Setup card yields the top-centre area to the phase/night-order HUD before first Dusk.
