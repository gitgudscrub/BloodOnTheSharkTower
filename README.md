# Blood on the Sharktower

**Current milestone:** 1.1.0-rc1 — A.12 Release Candidate

Blood on the Sharktower is a private Minecraft 26.3 Fabric port/fork of Blood on the Blocktower, rebuilt around the way the Sharktower group actually runs Blood on the Clocktower.

> **Private-use project.** The project owner has permission to use the inherited Blood on the Blocktower code and assets for private use. Do not publicly redistribute this repository or compiled builds without any additional permission required by the relevant rights holders. See `PRIVATE_USE_NOTICE.md`.

## Current platform

- Minecraft **26.3**
- Java **25**
- Fabric Loader **0.19.5**
- Fabric API **0.160.6+26.3**
- Simple Voice Chat **2.6.23+26.3**
- Mod version **1.1.0-rc1**

## What is implemented

The A.11 playable loop is complete and has passed the local multiplayer regression checklist.

### Setup and Grimoire

- Automatic player seating during Setup, with disconnect seat compaction.
- Script builder with custom scripts and the Base 3.
- Role Bag with current/expected distribution.
- Randomise/shuffle role and seat tools.
- Circular Storyteller Grimoire with player heads, role tokens, reminders and Demon bluffs.
- Direct Grimoire game actions:
  - left-click: edit role/alignment/reminders;
  - right-click: Player Actions;
  - Shift + left-click: select nominator;
  - Shift + right-click: nominate.
- Source-role reminder tokens, Good/Evil thumbs markers and Demon kill reminders.
- Three-role Demon bluff multi-select with in-play-role filtering.

### Day, nominations and voting

- Nominations and Storyteller vote controls.
- Public raised-hand voting.
- Large world vote markers:
  - green tick = living YES;
  - blue tick = dead ghost YES;
  - red cross = NO.
- Visible 3/2/1 pre-vote countdown and physical clock presentation.
- Execute — Dies and Execute — Lives outcomes.
- Clear dead-player presentation in the Grim with BOTC-style shrouds.
- Dead players render translucently with soul-wisp effects.
- Traveller exile support.

### Night and voice chat

- Dusk/Dawn phase flow and Storyteller night-order bar.
- Event-driven and manual night visits.
- Storyteller/private voice chat plus shared Night Chat.
- Day private-chat rooms with persistent entrance/exit points.
- Sprint-safe 1.5-block doorway triggers.
- Reconnect-aware voice routing.
- Night visit information, Droisoned/Vortox warnings and perceived-role handling.

### Information roles and hidden state

- Ordinary clients do not receive the full hidden Storyteller Grimoire.
- Spy/Widow Grimoire sharing is Storyteller-confirmed through a clickable chat action.
- Shared roles overwrite guesses while player-created reminders are preserved.
- Storyteller reminders and Demon bluffs are shared to Spy/Widow.
- Magician/Spy and Magician/Widow jinx handling automatically blanks the Magician and in-play Demon character tokens in the shared copy.

### End game

- Good/Evil winner selection.
- Original-style end-game presentation.
- Persistent Final Grimoire reveal.
- Reset for Next Game restores the captured start-of-game snapshot.

## Development

The quickest local multiplayer smoke test is:

```text
dev-harness\start-core-test.bat
```

This launches a loopback-only Fabric server plus:

- Storyteller
- TestPlayer1
- TestPlayer2

For a larger vote-circle test use:

```text
dev-harness\start-extended-test.bat
```

The extended harness launches the Storyteller plus five dummy players.

To build the mod:

```text
gradlew.bat build
```

The local harness deliberately uses offline-mode identities and binds the server to `127.0.0.1`. It is for local development only.

## A.12 — Release Candidate

A.12 is a stabilisation milestone, not a feature-expansion milestone. The goals are:

1. run full multiplayer soak tests with real players;
2. fix release-blocking regressions only;
3. verify hidden-information boundaries and reconnect behaviour;
4. produce a clean release JAR for the real server/client pack;
5. graduate 1.1.0 from RC to the private stable build.

See `A12_RELEASE_CANDIDATE_CHECKLIST.md`.

## Repository layout

- `src/` — mod source.
- `dev-harness/` — local multiplayer test harness.
- `docs/` — current technical documentation.
- `docs/history/` — archived port notes, patch instructions and milestone implementation notes.
- `tools/` — development/support tooling.
- `PRIVATE_USE_NOTICE.md` — distribution restrictions.
- `CHANGELOG.md` — release/milestone summary.

## Project history

The port began as a staged Minecraft 26.x reconstruction of Blood on the Blocktower. Historical milestone notes and one-off patch instructions are retained under `docs/history/` for reference, but they are not current installation instructions.

For current status, use this README, `docs/PORT_STATUS.md`, and the A.12 release-candidate checklist.
