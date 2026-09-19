# A.11 Live Setup Seating + Empty World Role Hotfix

This patch makes the Grimoire treat `ClientState.playerSeatNumbers` as the authoritative live seating map whenever that map is available. Previously the screen merged `grimoireSeatNumbers` into the live map; after unseating someone, the stale Grimoire entry could remain because there was no new occupant to overwrite that seat.

The Unseated list now follows the same live map, and the existing seat-layout signature rebuild means the open Grim refreshes automatically when the live seat map changes.

It also stops `RoleIconRenderer` from drawing the empty-role placeholder above players whose actual/perceived role is unassigned. Real assigned roles still render normally.

No voice-chat routing code is changed.
