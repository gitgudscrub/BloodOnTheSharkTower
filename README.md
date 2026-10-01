# Blood on the Sharktower

**Current version: 1.0.1**

Blood on the Sharktower is a Minecraft 26.3 Fabric mod and modpack for playing **Blood on the Clocktower** in Minecraft. It began as a port/fork of Blood on the Blocktower and has grown into a Sharktower-focused implementation with its own Grimoire, role bag, voting, voice-chat routing, night flow, script support and quality-of-life systems.

The project is built for the Sharktower community, but the client pack is also distributed through Modrinth.

## Platform

- Minecraft **26.3**
- Java **25**
- Fabric Loader **0.19.5**
- Fabric API **0.160.6+26.3**
- Simple Voice Chat **2.6.23+26.3**
- Blood on the Sharktower **1.0.1**

## Installation

### Players

The recommended client install is the Blood on the Sharktower Modrinth modpack:

https://modrinth.com/modpack/blood-on-the-sharktower

The client pack includes the required Fabric setup and dependencies, the current Blood on the Sharktower mod, Simple Voice Chat, No Chat Restrictions, and the Sharktower Minecraft server preconfigured in the Multiplayer menu.

### Server

The server runs the same Blood on the Sharktower JAR as clients. The server also needs the matching Fabric API and Simple Voice Chat versions listed above.

Client and server builds are produced together by GitHub Actions so that both packages come from the same source revision.

## What is implemented

### Setup and Grimoire

- Automatic player seating during setup, including disconnect handling.
- Script builder with custom scripts and the Base 3:
  - Trouble Brewing
  - Bad Moon Rising
  - Sects & Violets
- Role Bag with current/expected team distribution.
- Random role and seat distribution tools.
- Circular Storyteller Grimoire with player heads, role tokens, reminders and Demon bluffs.
- Direct Grimoire actions for role editing, reminders, nominations and player actions.
- Storyteller represented in the centre of the Grimoire for private-chat requests and Atheist nominations.
- Source-role reminders, alignment markers and Demon kill reminders.
- Spy/Widow Grimoire sharing with hidden-information safeguards.

### Day, nominations and voting

- Storyteller-controlled nominations and executions.
- Public raised-hand voting with visible vote markers.
- Clockwise vote sweep with configurable speed.
- Vote cancellation and vote snapshots.
- Required-vote and hands-raised information in the HUD.
- Living and dead-player vote handling, including ghost votes.
- Traveller exile support.
- Butler/Master voting support designed to avoid mechanically revealing the Butler through the public vote count.
- TOR-aware Butler behaviour.

### Night and voice chat

- Dusk/Dawn phase flow and Storyteller night-order controls.
- Event-driven and manual night visits.
- Shared Night Chat for players, including dead players.
- Storyteller/private voice-chat routing.
- Day private-chat rooms with entrance/exit handling.
- Reconnect-aware voice routing.
- Night information presentation, including Droisoned/Vortox warnings and perceived-role handling.

### End game

- Good/Evil winner selection.
- Original-style end-game presentation.
- Persistent Final Grimoire reveal.
- Reset for Next Game restores the captured start-of-game state.

## Versioning

Blood on the Sharktower uses a simple semantic-style versioning scheme:

- **1.0.0** — first playable baseline with the Base 3 working.
- **1.0.x** — bug fixes, behaviour changes, UI improvements and smaller additions.
- **1.x.0** — substantial new features or systems.
- **2.0.0** — reserved for a major incompatible redesign if the project ever reaches one.

The version in `gradle.properties` is the source of truth for releases. The mod JAR, Fabric metadata and generated Modrinth client pack should all use that same version.

When the Modrinth project is approved, whichever version is current at that point becomes the latest Modrinth release; the numbering does not reset for the platform launch.

## Development

Build the mod with:

```text
gradlew.bat build
```

For a quick local multiplayer smoke test:

```text
dev-harness\start-core-test.bat
```

This launches a loopback-only Fabric server with a Storyteller and two test players.

For a larger vote-circle test:

```text
dev-harness\start-extended-test.bat
```

The extended harness launches the Storyteller plus five test players.

The local development harness deliberately uses offline identities and binds the test server to `127.0.0.1`. It is intended for local development only.

## Automated builds

Pushes to `main` run the GitHub Actions build workflow. A successful build produces:

- a server/client Blood on the Sharktower JAR;
- an installable Modrinth `.mrpack` client package.

Keeping both artifacts in the same workflow helps prevent client/server version drift.

## Repository layout

- `src/` — mod source code and assets.
- `dev-harness/` — local multiplayer test harness.
- `docs/` — current and historical technical notes.
- `tools/` — packaging and development utilities.
- `.github/workflows/` — automated build and packaging workflow.
- `CHANGELOG.md` — release and milestone history.

## Project history

The project began as a Minecraft 26.x reconstruction of Blood on the Blocktower and gradually moved from staged porting into a playable Sharktower-specific implementation.

Version **1.0.0** marks the first baseline where the Base 3 scripts were working for regular play. Development from **1.0.1** onward follows the numbered release process above so test builds, server builds and Modrinth releases can be tracked consistently.

## Credits

Blood on the Sharktower uses and adapts code and assets from the original **Blood on the Blocktower** project with permission from its creators. Blood on the Clocktower is created by The Pandemonium Institute.

This project is an independent community implementation and is not an official Blood on the Clocktower product.
