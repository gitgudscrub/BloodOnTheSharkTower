# Blood on the Sharktower 0.8.0 — Original Grimoire UI

Private-use port milestone. Do not distribute publicly.

## Goal

Restore the original Blood on the Blocktower Storyteller Grimoire layout and interaction model on Minecraft 26.2, while keeping the 0.7.0 server-authoritative setup backend.

## Restored in this milestone

- Original-style circular Grimoire geometry:
  - 32px role tokens
  - 24px inner player/seat markers
  - role radius `min(width / 2, height / 2) - 50`
  - inner marker radius `roleRadius - 35`
  - players distributed clockwise from 12 o'clock
- Original control placement:
  - Script Builder at top-left
  - Shuffle Roles / Shuffle Seats / Randomize Roles at top-right
  - Unseated and Self visibility toggles
  - Send to Seats / Send Home
  - three Demon bluff slots at lower-left
  - timer button and SEND ROLES at lower-right
  - Players / Storytellers counts in the centre
- Role tokens are clickable and open a seat editor.
- Seat editor can assign/clear roles, override alignment, and add/clear reminders.
- Demon bluff slots are clickable and open a script-role chooser.
- Grimoire controls now call the 0.7.0 setup backend directly over a Fabric C2S payload instead of requiring chat commands.
- SEND ROLES commits pending Storyteller setup state to players.
- Shuffle Roles, Shuffle Seats, Randomize Roles, Send to Seats, and Send Home are wired to the existing backend.
- Self SHOW/HIDE now affects the circular player layout.

## Intentional remaining gaps

These do not block the 0.8.0 UI-parity smoke test:

- Player skin heads are not yet drawn in the inner 24px markers; seat numbers occupy those slots for now.
- The Unseated SHOW/HIDE button is present in the original position, but connected unseated-player discovery is not yet synchronized to the client.
- Script Builder currently opens the existing script reference screen until the full original script-builder workflow is ported.
- Drag-and-drop token manipulation is represented by click-to-edit role tokens for this milestone. The authoritative setup behavior is already functional underneath it.
- Reminder tokens use compact pips around the role token; exact original draggable reminder-token interaction is a later polish pass.

## Smoke test

1. `/bots testscript`
2. `/bots testseats 10`
3. `/bots storyteller claim`
4. `/bots randomizeRoles`
5. Press `R`.
6. Verify the original-style edge controls, circular player layout, centre counts, and three bluff slots.
7. Click a role token, change its role/alignment/reminders, then return to the Grimoire.
8. Click a Demon bluff slot and choose a valid good role.
9. Test Shuffle Roles and Shuffle Seats. Reopen the Grimoire if a shuffled seat arrangement has not visually refreshed yet.
10. Test SEND ROLES, Send to Seats, and Send Home.

