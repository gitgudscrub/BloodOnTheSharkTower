# Blood on the Sharktower 1.1.0-dev A9.2 — Automatic Setup Seating

## Added

- Players joining during **Setup** are automatically assigned the lowest free seat.
- Reconnecting players keep their existing pending/committed seat instead of receiving a duplicate/new seat.
- Storytellers are excluded from player seating once Storyteller control is claimed.
- If the Storyteller connected before claiming control and temporarily occupied a seat, that seat is removed and the remaining setup seats are compacted back to `1, 2, 3...`.
- Releasing Storyteller control during Setup automatically places that player back into the next free seat.

## Scope

Automatic seating deliberately runs during **Setup** only. A player joining an already-running game is not silently inserted into the live election/night state; late-game Traveller insertion remains a separate future workflow.

## Dev harness result

After a fresh local-server restart, `TestPlayer1`, `TestPlayer2`, etc. should populate seats automatically. The `Storyteller` client may briefly be auto-seated before claiming control, but claiming Storyteller removes it and compacts the test-player seats automatically.
