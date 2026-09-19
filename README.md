# Blood on the Sharktower — 1.1.0-dev A.11.0

A.10 — End Game & Reset UX builds on the multiplayer-tested A.9 Final baseline.

The match can now end ceremonially without immediately destroying the final game state. The Storyteller chooses the winning team, all players enter a read-only reveal period, and the existing start-of-game snapshot is only restored when the Storyteller is ready for the next match.

Highlights:
- Storyteller Tools now has an **End Game** workflow.
- Choose **GOOD WINS** or **EVIL WINS**.
- End Game immediately stops Night Chat/timers and closes active election state, but preserves roles, deaths and exiles for the reveal.
- Every player gets a public **GAME OVER** HUD banner.
- During reveal mode, the normal Grimoire key opens a **read-only Final Grimoire** for every player.
- The Final Grimoire shows each seat, player, real role, final alignment and dead/exiled status.
- Storytellers can change the declared winning team if they clicked the wrong result.
- **Reset for Next Game** restores the tested start-of-game snapshot/world state only after the reveal/discussion period is finished.
- Full Reset remains available as the immediate emergency rollback.
- End-game reveal blocks ordinary player game actions and phase changes until reset/cancelled.
- Commands are available for testing:
  - `/bots endGame good`
  - `/bots endGame evil`
  - `/bots endGame cancel`
  - `/bots resetForNextGame`

A.9 Final also includes automatic setup seating, Traveller exile voting, exiled-Traveller Night Chat support, the local multiplayer dev harness, and the tested world snapshot restoration path.

Use `dev-harness/start-core-test.bat` for Storyteller + two local dummy players.

This project is for private use only under the permissions noted in `PRIVATE_USE_NOTICE.md`.

## A.10.1 — Original-style End Game cinematic

A.10.1 adds the original BOTB-style black-screen game-end sequence before the persistent A.10 Final Grimoire reveal. It shows Victory/Defeat, the winning team, then reveals players/roles in seat order while the original game-end sound plays.

## A.10.2 — End-game UI hotfix

A.10.2 prevents the SETUP HUD from drawing over the persistent winner banner and replaces the Final Grimoire's unsafe circular placement with a responsive centred grid that reserves space for the title and bottom controls.

### A.10.3 UI follow-up
- Role/bluff selection returns directly to the Grimoire after a choice.
- Final Grimoire uses the same radial token/head layout as the live Grimoire, with final-alignment outlines and hover details.


## A.11.0 — Setup disconnect seat cleanup

A.11 begins the release-candidate polish pass. When an ordinary player disconnects while the match is in Setup, their seat is now released immediately and every higher-numbered seat is compacted down to remove the gap. Example: if Seat 2 leaves from a 1/2/3/4 roster, the old Seat 3 becomes Seat 2 and the old Seat 4 becomes Seat 3.

- The departing player's pending/live role, death state and reminders are removed with the seat.
- Remaining roles stay attached to the same players while only their seat numbers move.
- Storyteller clients receive the updated Grimoire immediately.
- If a start-of-game checkpoint already exists, its setup roster is refreshed without recapturing or rotating the world snapshot, so Reset for Next Game cannot restore a player who left during Setup.
- Disconnects during an active Day/Night game still retain the player's seat for reconnects.
- The completed Final Grimoire is preserved during end-game reveal; cleanup happens after returning to Setup.
