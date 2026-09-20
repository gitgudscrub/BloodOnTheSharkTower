# Blood on the Sharktower — Port Status

## Current baseline

- Minecraft: **26.3**
- Java: **25**
- Fabric Loader: **0.19.5**
- Fabric API: **0.160.6+26.3**
- Simple Voice Chat: **2.6.23+26.3**
- Current milestone: **1.1.0-rc1 / A.12 Release Candidate**
- Original port source: Blood on the Blocktower 1.21.1-1.3.0
- Distribution: **private use only**

The staged port itself is no longer the active development problem. The project now has a complete playable setup → Night/Day → nomination/vote/execution → end-game loop and is in release-candidate stabilisation.

## Confirmed functional areas

### Core state and setup

- role/script model;
- custom script loading;
- Base 3 loading;
- player seating and Setup disconnect compaction;
- Role Bag distribution;
- role/seat randomisation and shuffling;
- committed/pending Storyteller Grimoire state;
- perceived-role support for characters such as Drunk/Marionette;
- match-start snapshot and Reset for Next Game.

### Grimoire and Storyteller UX

- circular live Grimoire;
- player heads and role tokens;
- source-role reminder tokens;
- Demon bluff slots and three-role multi-select;
- direct nomination controls from player portrait/role token;
- Player Actions screen;
- public-info books;
- Final Grimoire reveal;
- Spy/Widow Storyteller-confirmed Grim sharing;
- Magician/Spy and Magician/Widow shared-Grim jinx handling.

### Day game

- nominations;
- hand voting;
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
- shared Night Chat;
- Storyteller private chats;
- dead-player Night Chat participation;
- Day Chat;
- persistent private-chat room entrance/exit markers;
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

## A.11 status

**Complete.**

A.11 became the release-candidate polish pass and absorbed the final multiplayer regression fixes around:

- setup live updates;
- Grimoire accessibility;
- nomination/vote presentation;
- dead-player presentation;
- private voice routing;
- reminder/bluff workflow;
- Spy/Widow information sharing;
- hidden-information boundaries;
- end-game/reconnect polish.

Historical A.11 implementation notes remain under `docs/history/`.

## Current target — A.12

A.12 is **stabilisation only**.

Release-candidate work should focus on:

- real-player multiplayer soak tests;
- reconnect/disconnect tests during every major phase;
- hidden-information/privacy verification;
- Simple Voice Chat stability over a full game;
- performance/UI readability at normal player counts;
- packaging and installing the release JAR into the real pack;
- fixing release-blocking bugs found during those tests.

New gameplay systems should normally wait until after 1.1.0 unless they are required to fix a broken existing flow.

See `A12_RELEASE_CANDIDATE_CHECKLIST.md`.
