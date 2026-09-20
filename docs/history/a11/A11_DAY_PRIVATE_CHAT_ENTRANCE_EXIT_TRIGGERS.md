# A.11 Day Private Chat — Entrance / Exit Triggers

This replaces the cuboid-zone version of Day Chat routing.

## Behaviour

- During Day, before nominations, seated players are kept in the shared hidden `BOTS Day Chat` group.
- Walking within ~2.25 blocks of a configured **entrance** moves that player into the matching isolated private group.
- Once inside, the player's physical position no longer matters. They remain in that private group until they walk within ~2.25 blocks of one of that area's configured **exits**.
- Crossing an exit returns the player to shared Day Chat.
- Opening nominations immediately returns all automatically-routed players to shared Day Chat and disables entrance/exit routing for the rest of that Day.
- Dusk hands routing over to the existing Night Chat system.
- Storytellers are never moved by these automatic player-to-player private-chat gates.
- Existing explicit Storyteller private chats still take priority.

## Setup

Stand just **inside** a doorway/gate and run:

`/bots daychat zone Graveyard entrance`

Stand just **outside** the same doorway/gate and run:

`/bots daychat zone Graveyard exit`

Repeat either command to add more entrances/exits for the same area.

This means an irregular location such as a graveyard, garden, house, alley or cave needs no cuboid/polygon definition at all. The mod only cares that players pass its gates.

## Commands

- `/bots daychat status`
- `/bots daychat zones`
- `/bots daychat zone <name> entrance`
- `/bots daychat zone <name> exit`
- `/bots daychat zone <name> clearEntrances`
- `/bots daychat zone <name> clearExits`
- `/bots daychat zone <name> remove`
- `/bots daychat clearZones`

A private chat becomes active once it has at least one entrance and one exit.

## Doorway placement

For a doorway used in both directions, do not put the two markers on exactly the same block. Put the **entrance just inside** and the **exit just outside**. A short re-entry cooldown prevents closely-spaced markers from causing group bouncing while a player walks through the gate.
