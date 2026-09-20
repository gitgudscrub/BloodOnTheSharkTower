# A.11 Regression Fixes — Setup Seats, Cushion Teleports, Base 3 Flow, Role Bag Counts

This patch addresses four issues found during release regression testing.

## Setup HUD seat count

During Setup, seat assignments live in the Storyteller pending setup state until SEND ROLES commits the game. The normal seat packet previously sent only the committed live map, so the top Setup HUD could remain at `0 seated players` even while the Grimoire correctly showed seated players.

Seat sync now uses the working/pending setup seat map while the game is still in Setup. Joining, disconnecting and reseating therefore update the top HUD immediately.

## Teleporting while seated on cushions

All configured seat/home teleports now dismount a player before teleporting. This allows `Send to Seats`, `Send Home`, individual town-square/home teleports and Storyteller night visits to work while the target is currently sitting on a cushion/seat entity.

## Base 3 selection flow

The Base 3 picker no longer opens a transient Grimoire immediately after clicking a script. It remains open while the server loads the bundled script. The existing script-sync receiver opens the Grimoire only after the authoritative Base 3 script arrives, preventing the UI from dropping back to the world during the reset/sync sequence.

## Role Bag distribution display

The Role Bag header now shows **current / expected** counts for the standard BOTC distribution at the current seated player count, for example:

`5/5 Townsfolk • 0/0 Outsider • 1/1 Minion • 1/1 Demon`

The left number is what is currently selected in the bag. The right number is the normal expected distribution for that player count. This deliberately shows the base expected setup; roles that modify Outsider counts can therefore be seen as deviations from that baseline.
