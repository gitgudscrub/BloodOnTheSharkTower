# Blood on the Sharktower 0.7.0 — Functional parity backend

0.7.0 deliberately prioritizes Storyteller/game functionality over final UI parity.
The original BOTB circular Grimoire remains the visual target, but the operations
behind its buttons are restored first so 0.8.0 can wire the original UI directly
to stable backend behavior.

## Restored in this batch

- Storyteller claim/release state
- pending setup roles and seats distinct from live/sent roles
- assign / clear a role by seat
- good/evil/default alignment overrides
- seat connected players and auto-seat all non-Storytellers
- swap roles / swap seats
- shuffle roles / shuffle seats
- standard 5–15 player role randomization from the current script
- Demon bluff add / clear / randomize
- reminder add / clear / clear-all
- `sendRoles` commit: pending setup -> live server state -> personal role packets
- discard pending setup edits
- soft reset and hard reset
- BOTB-style seat-home and town-square-seat position storage
- send all seated connected players to town-square seats
- send all seated connected players home
- teleport the executing player to a configured seat home
- Storyteller-targeted Grimoire sync now sees pending setup edits while normal
  clients continue to receive only committed/live role state
- `/bots status` now reports setup-backend and seat-position diagnostics
- `/bots testseats` utility retained for populated Grimoire testing

## Main commands

```text
/bots storyteller claim
/bots storyteller release
/bots setupStatus

/bots seatPlayer <player> <seat>
/bots unseatPlayer <player>
/bots seatAll

/bots assignRole <seat> <roleId>
/bots clearRole <seat>
/bots alignment <seat> <default|good|evil>
/bots swapRoles <seatA> <seatB>
/bots swapSeats <seatA> <seatB>
/bots shuffleRoles
/bots shuffleSeats
/bots randomizeRoles

/bots bluffs randomize
/bots bluffs add <roleId>
/bots bluffs clear

/bots reminder add <seat> <text...>
/bots reminder clear <seat>
/bots reminder clearAll

/bots sendRoles
/bots discardSetup
/bots resetGame
/bots resetGameHard

/bots setSeatHome <seat>
/bots setTownSquareSeat <seat>
/bots sendToSeats
/bots sendHome
/bots teleportToSeat <seat>
```

## Useful one-client smoke test

The `Network Test` script has exactly enough roles for a six-player standard
setup, so synthetic Grimoire players can exercise the full setup path:

```text
/bots testscript
/bots testseats 6
/bots storyteller claim
/bots randomizeRoles
/bots reminder add 2 Poisoned
/bots setupStatus
```

Open the Grimoire with `R`: the pending randomized roles/reminder should be
visible to the Storyteller before they are sent. Then:

```text
/bots sendRoles
/bots status
```

The executing real player's personal role should update and server/client role
sync should remain ONLINE.

## Multiplayer setup test

```text
/bots storyteller claim
/bots seatAll
/bots randomizeRoles
/bots sendRoles
```

`seatAll` excludes claimed Storytellers.

## Teleport test

Stand at each desired location and record it:

```text
/bots setSeatHome 1
/bots setTownSquareSeat 1
```

Then move away and test:

```text
/bots teleportToSeat 1
```

With multiple connected seated players and positions configured:

```text
/bots sendToSeats
/bots sendHome
```

Seat positions are intentionally in-memory in 0.7.0. Persistent config JSON is
part of the later world/config integration pass.

## Next: 0.8.0 original UI parity

With the backend stable, 0.8.0 can port the original BOTB circular Grimoire
controls nearly 1:1: player heads around the ring, drag/drop roles, reminders,
Demon bluff slots, Shuffle Roles/Seats, Randomize Roles, Unseated/Self toggles,
Send to Seats/Home and SEND ROLES.
