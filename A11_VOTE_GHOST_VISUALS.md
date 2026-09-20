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


## Grimoire game-flow interactions

The Storyteller Grimoire now supports direct day-game interactions without
requiring the separate Nomination Flow screen for routine play:

- Left-click a player role token: role/alignment/reminder setup.
- Right-click a player role token: open the accessible Player Actions sheet.
- Shift + left-click: select/toggle that player as the nominator.
- Shift + right-click: nominate the clicked player using the selected nominator.
- The selected nominator has a green outline + N marker.
- The current nominee has a gold outline.
- The Grim displays the active shortcut legend and selected nominator.

The Player Actions sheet exposes the same flow with ordinary buttons, so modifier
clicks are optional rather than required.

Execution now has two explicit outcomes:

- Execute — Dies: records the execution, marks the player dead, triggers death
  handling, and plays the original execution sound.
- Execute — Lives: records the execution and closes nominations, but leaves the
  player alive, does not fire death handling, and plays the original survived
  execution sound.

The legacy `execute_marked` action remains the dies path; the new
`execute_marked_survives` action handles survival.


## Alignment + Demon reminder polish

- Generic `Good` reminders render as a pixel-art thumbs-up marker.
- Generic `Evil` reminders render as a pixel-art thumbs-down marker.
- The Reminder screen has a dedicated **Demon Kill** picker.
- Demon Kill options are generated from Demon roles on the currently loaded
  script, e.g. `Imp: Kill`, `Po: Kill`, `Shabaloth: Kill`.
- Those Kill reminders retain their Demon source role and therefore render the
  matching Demon token in the Grimoire.
- Source-role reminders now also accept script/custom role ids, so the same data
  path is not restricted to the base official-role enum.


## In-play Demon kill markers

- Demon Kill reminder options are sourced from Demon roles actually present in
  the Storyteller's Grimoire, not every Demon listed on the loaded script.
- Duplicate copies of the same Demon character collapse to one reminder choice.
- If exactly one Demon is in the Grimoire, pressing **Demon Kill** immediately
  adds that Demon's `Kill` reminder without opening another chooser.
- If multiple Demons are in play, the chooser contains only those in-play Demons.
- If no Demon is currently represented in the Grimoire, the Demon Kill button is
  disabled.


## Demon action + death-shroud follow-up

- Player Actions now separates **Demon Kill Reminder** from **Resolve Demon Kill**.
  The reminder action never changes death state; Resolve Demon Kill is the explicit
  lethal game action.
- If one Demon is in the Storyteller Grim, Demon Kill Reminder immediately places
  that Demon's `Kill` reminder. Multiple Demons still open the in-play chooser.
- Dead role tokens now use a high-contrast hood/drape shroud overlay rather than
  the old red X, making death readable on red Minion/Demon tokens.

## Spy / Widow share follow-up

- Night visits no longer auto-open a separate true-Grim snapshot.
- Storyteller receives a clickable **[SHARE GRIMOIRE]** chat action.
- The shared snapshot populates the player's normal personal Grimoire:
  - roles overwrite local role guesses;
  - Storyteller reminders are added;
  - player-created reminders are preserved;
  - Demon bluffs are included.
- A later refresh replaces only the Storyteller-shared reminder layer.
- Droisoned Spy/Widow true-Grim sharing remains blocked.


## Demon bluff legality

- The manual Demon bluff picker now excludes good roles currently represented in
  the Storyteller's Grimoire.
- Believed-role tokens shown to Drunk/Marionette are also excluded, matching the
  existing random-bluff logic.
- The server independently rejects an in-play/believed-role bluff even if a stale
  client tries to submit one.
- Example: if Chef is in play, Chef no longer appears as a selectable Demon bluff.
