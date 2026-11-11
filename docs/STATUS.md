# Blood on the Sharktower — Status

## Current baseline

| Item | Version |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25 |
| Fabric Loader | 0.19.5 |
| Mod version (source of truth: `gradle.properties`) | see `gradle.properties` |
| Live community release | 1.0.1 |
| Runtime dependency pins (Fabric API, Simple Voice Chat, Cloth Config, No Chat Restrictions, Sodium, Lithium, FerriteCore) | `modrinth/pack-config.json` |
| Original port source | Blood on the Blocktower 1.21.1-1.3.0 |
| Distribution | private use only (see `PRIVATE_USE_NOTICE.md`) |

The mod compiles against the Fabric API and Simple Voice Chat API versions in
`gradle.properties`. The client pack and the server install use the runtime
versions pinned in `modrinth/pack-config.json`; those may be newer than the
compile-time versions, which is fine because the mod declares open-ended
dependency ranges in `fabric.mod.json`.

## Where the project is

The staged port from Blood on the Blocktower is finished. The mod has a complete
playable loop: Setup → Send Roles → Night/Day → nominations, votes and executions
→ end game → Final Grimoire → Reset for Next Game.

Version history, including the renumbering from the old `1.1.0-rc1` milestone
name to the released `1.0.0`, is in `CHANGELOG.md`.

Work in progress is tracked in the README roadmap and in `docs/*.md` notes named
after the alpha that introduced each change.

## Storyteller control

- Anyone can claim Storyteller control during Setup with `/bots storyteller claim`.
  Several people can hold it at once.
- Control is **locked** from the moment roles are sent until the Final Grimoire
  reveal publishes every role. Ordinary players cannot claim it during a game,
  and no `/bots` command grants it as a side effect.
- A server operator (gamemaster permission or higher) can override the lock to
  rescue a game whose Storyteller cannot return. The override is logged.
- Releasing control during a game does not reopen it to ordinary players.
- Every state-changing `/bots` command and every Storyteller Grimoire packet is
  checked server-side against the current Storyteller set. Player-facing
  commands (status, hand, vote, exile vote, call for exile, nominate, private
  request/accept/leave, nightchat whoami) remain available to everyone.

## Player profiles

- `/pronouns <text>` and `/pronouns clear` store cosmetic pronouns by UUID in the
  server config directory and broadcast them to every client on join and change.
  Shown under the name tag (vanilla below-name line, grey), in the sidebar and
  in Grimoire hover text; hidden at Night with the name. Client toggle: Pronoun
  Tags. Validation lives in `profile/Pronouns.java` (`pronounsRegression`).
- Above-head stack from the head upwards: name tag + pronoun line (to about
  +1.0), role token centred at +1.25, vote marker centred at +2.10.

## Confirmed functional areas

### Core state and setup

- role/script model;
- custom script loading, including the local script library and botcscripts.com import;
- Base 3 loading;
- player seating and Setup disconnect compaction;
- Role Bag distribution;
- role/seat randomisation and shuffling;
- committed/pending Storyteller Grimoire state;
- perceived-role support for characters such as Drunk/Marionette/Lunatic;
- match-start snapshot and Reset for Next Game.

### Grimoire and Storyteller UX

- circular live Grimoire;
- player heads and role tokens with alignment borders;
- source-role reminder tokens;
- Demon bluff slots and three-role multi-select; separate Lunatic bluffs;
- direct nomination controls from player portrait/role token;
- Player Actions screen;
- public-info books and fresh notebooks on Send Roles;
- private deaths with Storyteller-chosen reveal;
- Final Grimoire reveal;
- Spy/Widow Storyteller-confirmed Grim sharing;
- Magician/Spy and Magician/Widow shared-Grim jinx handling;
- Minion Info / Demon Info preview and private delivery.

### Day game

- nominations;
- hand voting and the nomination-only speaking queue;
- vote clock;
- locked votes;
- ghost votes;
- Traveller exile support;
- Execute — Dies;
- Execute — Lives;
- vote-result cleanup;
- large world-space tick/cross vote markers;
- 3/2/1 pre-vote countdown.

### Night game and information

- Dusk/Dawn flow;
- Storyteller night-order bar;
- first-night/other-night ordering;
- night visit teleport/private-chat invitation;
- triggered night-order support;
- Droisoned and Vortox presentation warnings;
- perceived-role night information;
- Demon kill reminder and resolution flows.

### Voice chat and map integration

- Simple Voice Chat integration;
- physical house voice rooms (isolated per seat home);
- Storyteller private chats;
- Day Chat and persistent private-chat room entrance/exit markers;
- 1.5-block sprint-safe doorway routing;
- reconnect handling;
- voice-route HUD.

### Presentation and end game

- public role-count strip;
- world role icons;
- dead-player translucency and soul wisps;
- BOTC-style Grimoire death shroud;
- game-end cinematic;
- winner banner;
- persistent Final Grimoire;
- controlled reset after post-game discussion.

## Verification

- `gradlew build` compiles common and client sources and runs every regression
  registered in `build.gradle` (death visibility, reminder catalog, Game 1,
  team information, house voice, Storyteller claim lock, script import,
  hand raise mode).
- CI additionally boots the loopback dev server (`tools/smoke_dev_server.py`)
  and builds the client `.mrpack`.
- Graphics, audio and multi-client behaviour still need the live checks listed
  in the per-alpha notes under `docs/`.
