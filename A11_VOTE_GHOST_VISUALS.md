# A.11 — Vote markers + dead-player ghost visuals

This patch adds two world-space readability features inspired by the Minecraft
Clocktower playtest pack while keeping Sharktower's existing authoritative vote
and death state.

## Vote markers

During an active nomination vote / Traveller exile support vote:

- living YES -> green tick above the player's head
- dead/ghost YES -> blue tick above the player's head
- NO -> red cross above the player's head
- the seat currently being counted gets a subtle pulse
- once a seat is counted, its icon follows the locked vote
- Organ Grinder hides the public markers from ordinary players; Storytellers
  retain them for administration

The renderer uses the existing synchronized vote maps. No new vote protocol is
introduced.

## Dead-player presentation

- BOTS-dead player bodies render at approximately 50% opacity.
- Dead players emit subtle soul wisps.
- A dead player with an unused ghost vote has a stronger/faster wisp cadence.
- After spending the ghost vote, the player remains translucent/dead but the
  ambient soul effect becomes fainter.

The transparency is client-side presentation driven by the existing public death
map. It does not alter Minecraft invisibility, collision, voice, seating, or
Clocktower death mechanics.

## Regression test

1. Mark a seated player dead and confirm their body becomes translucent with
   soul wisps.
2. Start a vote.
3. Living raised hand -> green tick.
4. Living lowered hand -> red cross.
5. Dead player with unused ghost vote raises hand -> blue tick.
6. Let the clock pass seats and confirm locked icons no longer change.
7. Resolve the vote and confirm the dead player's ghost vote is consumed.
8. Start another vote and confirm that player cannot produce another blue YES.
9. Enable Organ Grinder and confirm ordinary players cannot see individual
   tick/cross markers while the Storyteller still can.


## Follow-up polish from multiplayer test

- Vote tick/cross markers are now approximately one block tall and render above
  the floating role token so they remain readable across the town-square circle.
- Vote intent markers appear during the active nomination/exile discussion before
  the clock starts, using the raised-hand state.
- Starting either election holds the server clock for a visible 3-second 3/2/1
  countdown before the first seat can lock.
- YES presentation is consistently green and NO presentation is consistently red
  in the voting HUD, sidebar hand indicator, and Storyteller boolean night-info lines.
- The local dead player no longer sees their own ambient soul particles.
- Daytime voice-room entrance and exit markers now use a true 1.5-block radius.
- Daytime doorway routing scans every server tick, with entry/exit grace windows,
  so sprinting through a doorway cannot skip the trigger or immediately bounce
  from Private Chat back to Day Chat.
