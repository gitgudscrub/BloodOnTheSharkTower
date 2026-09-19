# Blood on the Sharktower 1.0.0-dev — Minecraft 26.3 Migration

This checkpoint migrates the working 0.9.1 project from Minecraft 26.2 to 26.3 before the voting and voice-chat integrations are added.

## Version changes

- Minecraft: `26.2` -> `26.3`
- Fabric Loader: `0.19.5` (unchanged)
- Fabric API: `0.160.0+26.2` -> `0.160.6+26.3`
- Fabric Loom: `1.17.19` -> `1.17.21`
- Gradle: `9.5.1` -> `9.6.0`
- Java: `25` (unchanged)
- BOTS version: `0.9.1` -> `1.0.0-dev`

## Why this migration is deliberately small

No gameplay system has been redesigned in this checkpoint. Keeping the migration isolated makes any 26.3 API regression easy to identify before new voting or voice-chat code is introduced.

The existing BOTS client keybind code already uses Mojang's `InputConstants` abstraction instead of importing GLFW constants directly, which is the correct direction for Minecraft 26.3's SDL input backend.

Minecraft 26.3 provides native cushions with interaction-to-sit behaviour, so BOTS does not add a duplicate cushion block/entity system.

## Smoke test

From PowerShell in the project root:

```powershell
.\bootstrap-gradle.bat
```

Only use the bootstrap script if your normal Gradle wrapper is not already present. Otherwise run:

```powershell
.\gradlew.bat clean runClient
```

Once the client opens:

1. Confirm Minecraft reports version 26.3.
2. Join a test world.
3. Run `/bots status`.
4. Run `/bots testscript`.
5. Run `/bots testseats 15`.
6. Run `/bots storyteller claim`.
7. Press `R` and verify the circular Grimoire still renders correctly.
8. Open `Script Builder -> Base 3`, load each Base 3 script, and verify the Grimoire updates.
9. Open `Role Bag`, select a valid bag, distribute it, and verify pending roles appear around the Grimoire.
10. Use `SEND ROLES` and confirm committed roles synchronize.
11. Exercise one nomination/vote/execution test and the timer controls.
12. Place a native Minecraft cushion on a suitable surface and right-click it to verify vanilla sitting behaviour.

If compilation fails, keep the full Gradle compiler output. The most useful section starts at the first `error:` line; do not delete the 0.9.1 project until the migration passes.

## Voice chat

Simple Voice Chat is intentionally **not** bundled into this migration checkpoint. A Fabric build for Minecraft 26.3 exists, but BOTS will add/revalidate its voice-chat integration as a separate milestone after this core migration passes. Keeping it separate prevents voice API issues from being confused with Minecraft/Fabric migration issues.

## Rollback

The 0.9.1 Minecraft 26.2 build remains the last known-good Sharktower checkpoint. If 26.3 exposes a regression, return to 0.9.1 while the regression is fixed rather than changing the frozen 0.8.2 baseline.

## Keyboard API hotfix

Minecraft 26.3's SDL input migration renamed the keyboard input type used by key mappings
from `InputConstants.Type.KEYSYM` to `InputConstants.Type.KEYBOARD`. `KeyInputHandler`
has been updated accordingly.
