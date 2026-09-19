# 1.1.0-dev A.7 — Controls & Settings Polish

## Player controls
- Added `Open Sharktower Settings` (default **O**) as a normal Minecraft-rebindable key.
- Voting hand (**U**) and leave-private-chat (**J**) now have explicit entries in the Blood on the Sharktower Controls category.
- The role HUD no longer hard-codes `X` / `Z`; it displays the player's actual current bindings.

## Local HUD settings
The new Sharktower Settings screen can independently toggle:
- Voice route HUD
- Role HUD
- Setup HUD
- Voting HUD
- Hand-vote HUD
- Timer HUD

These are client-only preferences and are saved in `config/blood-on-the-sharktower-client.properties`.

## Storyteller presentation settings
When the local player is the active Storyteller, the same screen also exposes:
- Vote clock speed (0.50s–2.00s presets per seat)
- World clock-hand scale (1.5–8.0)

Vote speed is server-authoritative and cannot be changed during a running vote. The physical clock-hand animation uses the same synchronized step duration, so visuals and locked vote timing stay together.

The Storyteller Tools screen now includes a Settings button.
