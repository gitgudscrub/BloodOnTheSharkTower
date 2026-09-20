# 1.0.0-dev — Minecraft 26.3 Migration Candidate

## Goal

Move the confirmed 0.9.1 Blood on the Sharktower feature set onto Minecraft 26.3 before adding the new voting system or Simple Voice Chat/night-chat integration.

## Carried forward

- `/bots` command family
- server/client core state synchronization
- full script synchronization
- player role synchronization
- nominations, voting, execution and timer backend
- circular BOTB-style Grimoire
- Storyteller setup controls
- real player heads and connected-player seating
- reminders and demon bluffs
- Role Bag
- Base 3 script loader

## Migration-specific changes

- Minecraft dependency and manifest range now target 26.3.
- Fabric API updated to 0.160.6+26.3.
- Fabric Loom updated to 1.17.21.
- Gradle wrapper/bootstrap target updated to 9.6.0.
- Existing Java 25 target retained.
- Existing `InputConstants` key mappings retained for the SDL-backed 26.3 input stack.
- Native Minecraft cushions are used rather than implementing a duplicate BOTS cushion feature.

## Not included yet

- Simple Voice Chat dependency/plugin integration
- Night-chat rules
- New Sharktower hand-voting UI/system

Those should be introduced only after this migration passes the smoke test.
