# Blood on the Sharktower

**Current version: 1.0.1**

Blood on the Sharktower is a Minecraft 26.3 Fabric mod and modpack for playing **Blood on the Clocktower** in Minecraft. It began as a port/fork of Blood on the Blocktower and has grown into a Sharktower-focused implementation with its own Grimoire, role bag, voting, voice-chat routing, night flow, script support and quality-of-life systems.

The project is built for the Sharktower community. A Modrinth client package is being prepared for public distribution once the project is approved.

## Platform

- Minecraft **26.3**
- Java **25**
- Fabric Loader **0.19.5**
- Fabric API **0.160.6+26.3**
- Simple Voice Chat **2.6.23+26.3**
- Blood on the Sharktower **1.0.1**

## Installation

### Players

While the Modrinth project is awaiting approval, client builds can be shared directly as `.mrpack` files. Once approved, the recommended client install will be the Blood on the Sharktower Modrinth modpack:

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
- Reset for Next Game restores the captured map/setup while clearing player roles (1.0.2 development).

## Versioning

Blood on the Sharktower uses a simple semantic-style versioning scheme:

- **1.0.0** — first playable baseline with the Base 3 working.
- **1.0.x** — bug fixes, behaviour changes, UI improvements and smaller additions.
- **1.x.0** — substantial new features or systems.
- **2.0.0** — reserved for a major incompatible redesign if the project ever reaches one.

The version in `gradle.properties` is the source of truth for releases. The mod JAR, Fabric metadata and generated Modrinth client pack should all use that same version.

When the Modrinth project is approved, whichever version is current at that point becomes the latest Modrinth release; the numbering does not reset for the platform launch.

## Roadmap

Development is deliberately staged so that the core Clocktower rules are reliable before the project expands into the much larger Experimental and homebrew rulesets.

### 1.0.2 — Session feedback and polish *(in development)*

Version **1.0.2** is the next feedback-driven patch following real multiplayer play on 1.0.1. Its scope is deliberately focused on issues and quality-of-life improvements found during live sessions, with room for additional feedback before release.

Implemented on the 1.0.2 development branch, awaiting multiplayer validation:

- fix the newest connected player sometimes missing the latest player/sidebar state until another refresh occurs;
- fix **RMB Actions** in the Grimoire so right-click player actions work as advertised;
- add at-a-glance alignment borders to Grimoire role tokens:
  - **blue** for Good;
  - **red** for Evil;
- on the **Storyteller Grimoire**, the border represents the player's real current alignment and updates when Force Good/Force Evil changes it;
- on a **player's own Grimoire**, the border represents only that player's personal alignment read and never exposes real hidden alignment;
- tie player-side Good/Evil reminder tokens to those personal alignment borders;
- make player-side Good and Evil reminders mutually exclusive, so adding one automatically removes the other;
- clearing the active player-side Good/Evil reminder returns that player's border to neutral;
- keep player alignment notes completely separate from authoritative Storyteller game state so no hidden information can leak.

### Additional live-session feedback (4 October 2026)

The following work is **implemented on the draft 1.0.2 branch; live validation remains**:

- **Role/bluff colours:** Make bluff selection and displayed bluffs use the same role-category colours as the rest of the UI, including custom roles. Keep category colours separate from alignment borders.
- **Client performance mods:** Include compatible Minecraft 26.3 Fabric releases of Sodium, Lithium and FerriteCore after checking exact versions, dependencies and compatibility with Sharktower and Simple Voice Chat. Sodium is client-only; evaluate Lithium/FerriteCore separately for server use. Players reported roughly 30 FPS, dropping to 20–25 FPS with VSync disabled; after adding performance mods to their clients, players reported reaching 60 FPS. This is live-player feedback, not a controlled benchmark.
- **Setup Storyteller disconnect:** Clear the Storyteller assignment when they disconnect during setup so another player can take over. Preserve the script, players and other setup choices. Reconnecting must not automatically reclaim a released assignment.
- **Storyteller teleport:** Allow the assigned Storyteller to use `/tp <player>` to teleport themselves to an online player without operator permissions. Check current Storyteller access server-side on every use, support player-name suggestions, and handle players in another dimension. This permission covers self-teleporting to a player only; it does not grant coordinate teleporting, moving other players or unrestricted vanilla `/tp` access. Preserve voice routing and revoke access when Storyteller control is released.
- **Storyteller flight and spectator:** Allow the assigned Storyteller to fly and enter/leave spectator mode without operator permissions. Validate access server-side, restore previous game mode/flight permissions when appropriate, and preserve Grimoire access and voice routing.
- **Private deaths and manual reveal:** Let the Storyteller mark deaths privately with pending indicators in their Grimoire, then reveal deaths during the day when they choose using a Grimoire control. Resolved deaths must count for rules and abilities immediately, but public death displays must remain unrevealed until that action. Dawn must not automatically publish staged deaths; check reconnects and every public sync path for premature disclosure.
- **Reset clears roles:** Both **Full Reset** and **Game Reset / Reset for Next Game** must clear all player role assignments rather than restore starting roles. Clear pending/setup and perceived-role assignments, personal role guesses and stale own-role/ability displays; prevent old snapshots or reconnects from repopulating them. Preserve each reset's other intended scope.

Implementation notes and regression checks are tracked in `docs/1.0.2-session-feedback.md` on the draft 1.0.2 branch.

Additional fixes or polish found during the same testing cycle may be added before 1.0.2 is released.

Development builds use **1.0.2-alpha.4**; the current live release remains **1.0.1**. See `docs/1.0.2-session-feedback.md` for the regression checks.

### 1.0.x — Base 3 full support *(current focus)*

Version **1.0.0** marked the first playable Base 3 baseline. The rest of the **1.0.x** line is focused on making **Trouble Brewing, Bad Moon Rising and Sects & Violets** fully supported and dependable in real games, while also finishing the core player experience needed for regular community play.

Priorities include:

- testing every Base 3 character and its important interactions;
- correctly handling setup changes, role changes, death, resurrection, poisoning/drunkenness and registration;
- reliable reminders, night order and Storyteller information;
- nominations, executions, ghost votes and character-specific voting rules;
- reconnect and hidden-information behaviour;
- player identity and customisation support;
- fixing UI or quality-of-life issues found during real multiplayer games.

#### Player identity and customisation

The **1.0.x** line will include a persistent player profile system so players can present themselves consistently in Sharktower games without changing their underlying Minecraft identity.

Planned features include:

- `/nick` commands so players can choose the name they want to be called;
- `/pronouns` commands with common presets plus custom pronoun text;
- optional display of pronouns above the player's head;
- optional cosmetic display colours that do not encode role, alignment or other game information;
- persistence by UUID so settings survive reconnects and server restarts;
- use of the chosen nickname in player-facing Sharktower UI such as the Grimoire, nominations and voting where practical;
- retention of the real Minecraft username internally for permissions, moderation and debugging;
- sensible validation to prevent control characters, misleading system-style names and other problematic display values.

These options are intended to be cosmetic and social only. They must never reveal or imply hidden Clocktower information.

The goal is to finish both the Base 3 rules foundation and the core player experience before treating the wider character pool as fully supported.

### 1.1.x — Custom scripts and Experimental characters

Once the Base 3 is stable, the next milestone is to turn the existing custom-script functionality into a fully supported gameplay path and systematically test the **Experimental** character pool.

This phase will focus on:

- robust custom script loading and validation;
- Experimental character abilities and reminder tokens;
- interactions between characters that do not normally appear together in the Base 3;
- official jinxes and unusual setup interactions;
- expanding the reusable rules engine where Experimental characters expose gaps in the Base 3 implementation.

### 1.2.x — Klutzbanana homebrew support

The next major capability will be support for homebrew content created with **Klutzbanana**.

The aim is to support imported homebrew characters and scripts, including the information the mod can represent generically such as:

- character names, teams and ability text;
- custom icons and script data;
- reminder tokens;
- night-order information;
- setup metadata and other supported character properties.

Homebrew abilities can be arbitrarily complex, so this phase will distinguish between mechanics the mod can automate safely and mechanics that should remain under Storyteller control.

### Design principle

Where practical, Blood on the Sharktower implements **reusable game concepts rather than one-off character exceptions**. Systems such as role changes, alignment changes, poisoning, registration, extra deaths, resurrection, setup modification and reminder ownership should be reusable by many characters.

Clocktower will always contain special cases, but building strong shared systems during the Base 3 phase should make Experimental and homebrew support substantially easier and less fragile later.

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

Game 1 playtest changes and retest steps: [Game 1 feedback](docs/game-1-feedback.md). Personal notebooks open with **B** by default.
