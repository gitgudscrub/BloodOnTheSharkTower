# A.13 — Original-Style Grimoire

A.13 is a development-only UI milestone. The released 1.0.0 modpack / 1.1.0-rc1
mod build remains the known-good public test baseline.

Development branch version: `1.2.0-dev-a.13.0`.

## Design goal

Use Blood on the Blocktower 1.3.0 as the structural and visual reference for
the Grimoire while retaining Sharktower's newer backend systems.

The priority is not merely copying colours. The original Grim is cleaner because
its controls are contextual and the Grim itself is the primary interaction
surface.

## Reference constants recovered from the original JAR

- Role token: **32 px**
- Player head: **24 px**
- Role-ring padding: **50 px**
- Reminder token: **14 px**
- Reminder padding: **2 px**
- Seat-number radius offset: **20 px**
- Fade duration: **200 ms**
- Per-element fade delay: **50 ms**

Original phase model:

- SETUP
- NIGHT
- DAY
- NOMINATIONS
- PLAYER_NOMINATED
- PLAYER_MARKED
- CALL_FOR_EXILE
- EXILE_SUPPORT

## Implemented in pass 1

- Imported original BOTB Grimoire icon assets under
  `textures/icons/original/`.
- Changed reminder size from 16 px to the original 14 px.
- Reminder tokens now hug the role token using the original eight-slot geometry.
- Reminder ordering favours the outside of the circle and avoids the inner player
  head.
- Storyteller right-side controls now change with GamePhase rather than exposing
  all setup/game controls simultaneously.
- Main Grim centre counts are Setup-only.
- Bluff tokens remain compact and no longer require permanent role-name labels.
- Left-click player head opens reminders.
- Left-click role token edits role/alignment.
- Right-click remains the Sharktower Player Actions accessibility surface.
- Shift-click nomination shortcuts are retained.
- Compact interaction hints explain the current click model.
- A.13 development builds identify as `1.2.0-dev-a.13.0`.

## Next visual passes

- Use original Dusk / Dawn / Nominations artwork in the phase presentation.
- Rework hover hints to be target-specific rather than always-visible.
- Restore the original staggered 50 ms / 200 ms Grim opening animation.
- Review token/head/name spacing at 7, 10 and 15 players.
- Decide which Sharktower-only controls belong directly in the Grim versus the
  compact TOOLS fallback.
- Continue matching original role/reminder hover details and selection outlines.

## Regression constraints

A.13 must not regress:

- role bag setup;
- Base 3/custom script loading;
- direct nominations;
- vote flow;
- execute dies/lives;
- Traveller exile;
- perceived Drunk/Marionette roles;
- source-role reminders;
- Demon kill reminders;
- three-bluff multi-select;
- Spy/Widow Grim sharing;
- Storyteller centre interaction/private chat.
