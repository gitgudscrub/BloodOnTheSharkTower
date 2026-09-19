# Blood on the Sharktower — 26.2 port status

## Baseline

- Minecraft: 26.2
- Java: 25
- Fabric clean Sharktower shell: confirmed
- Port source: Blood on the Blocktower 1.21.1-1.3.0
- Original compiled classes inventoried: 377
- Original asset files staged under Sharktower namespace: 222
- Distribution: private use only under the permission granted to the user

## Confirmed by runtime tests

- 0.1.0 — clean 26.2 launch
- 0.1.1 — health command
- 0.2.0 — core state/assets
- 0.2.1 — role/script model
- 0.3.0 — S2C/C2S networking round trip
- 0.3.1 — full compressed script sync
- 0.3.2 — player role sync (rolled into later bulk state)
- 0.4.0 — bulk playable core: roles, seats, death, grimoire, phase parity

## 0.5.0 — game-flow batch (current test target)

Ported:

- DaytimeState
- ElectionState / ElectionConfig / ElectionType / ElectionManager core
- NominationManager
- VotingManager
- ExecutionManager
- ExileManager / ExileSupportManager
- daytime + vote S2C state payloads
- client daytime/election state
- `/bots` commands for backend nomination/vote/execution/exile testing

Still deferred:

- physical vote levers/pistons/indicator blocks
- timed clock presentation
- game HUDs/screens
- role assignment/setup UI
- full grimoire UI
- mixins
- Simple Voice Chat

## Next target

0.6.0 — bulk GUI/HUD restoration and world/presentation hooks.
