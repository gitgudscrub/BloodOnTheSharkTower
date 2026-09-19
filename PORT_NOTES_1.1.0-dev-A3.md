# 1.1.0-dev A.3 — Night Chat Polish & Edge Cases

This update keeps the A.2 automated Storyteller workflow and adds player-facing route feedback plus defensive reconnect/disconnect handling.

## Voice-route HUD

A small local HUD strip now shows the route the server believes you are using:

- **NIGHT CHAT** — seated player in the shared night room.
- **NIGHT CHAT - STORYTELLER** — active Storyteller monitoring the shared night room.
- **PRIVATE CHAT** — player privately speaking with the Storyteller.
- **PRIVATE CHAT - STORYTELLER** — Storyteller privately speaking with a player.
- **PRIVATE CHAT - ST LEFT** — the Storyteller has moved on, but the player is intentionally still isolated.
- **NIGHT CHAT - CONNECTING** — the player belongs in Night Chat but Simple Voice Chat has not connected yet.

The normal **PROXIMITY** route is intentionally not shown, so daytime play does not gain another permanent HUD element.

## Private-room leave key

- A new rebindable **Leave Private Storyteller Chat** control is registered under the Blood on the Sharktower keybind category.
- Default key: **J**.
- The key only sends an action while the local route is private, so pressing J during ordinary play does nothing.
- In `PRIVATE CHAT - ST LEFT`, the HUD shows the player's actual current binding rather than hard-coding J.
- `/bots private leave` remains available as the command fallback.

## Hand-vote rebind polish

The voting-hand HUD no longer says `[U]` permanently. It reads the live Minecraft key mapping and displays whatever key the player has rebound **Raise / Lower Voting Hand** to in Controls.

## Dead-player Night Chat

Death does not remove a player from Night Chat. Every committed seated player, living or dead, uses the same shared night room unless temporarily in a private Storyteller room. Dead players can also use the normal private-chat flow.

`/bots nightchat status` now reports the seated-player total and how many are dead so this can be checked during multiplayer testing.

## Reconnect/disconnect hardening

There is now an explicit distinction between a voice-plugin dropout and leaving the Minecraft server:

- **Simple Voice Chat drops for a player in private chat/hold:** preserve the private room bookkeeping. If the Storyteller was attached, free only the Storyteller. When the player's voice connection returns, they are restored to the private hold rather than unexpectedly exposed to public Night Chat.
- **Minecraft client disconnects while private:** clean up that player's private session and free an attached Storyteller so the night cannot become stuck.
- **Storyteller disconnects:** the player remains in private hold, matching the normal "Storyteller leaves" behaviour.
- The connected-player directory is refreshed after a hard disconnect.

## Recommended smoke test

1. Rebind **Raise / Lower Voting Hand** from U to another key and confirm the voting HUD shows the new binding.
2. Start Night. Confirm living player, dead player and Storyteller all receive the appropriate Night Chat HUD route.
3. Storyteller visits Player A; A accepts. Confirm both clients show their private-chat route.
4. Storyteller leaves A's house. Confirm the ST returns to `NIGHT CHAT - STORYTELLER` while A sees `PRIVATE CHAT - ST LEFT`.
5. Rebind **Leave Private Storyteller Chat** from J and confirm A's private-hold HUD updates to that new key.
6. Temporarily reconnect/restart A's Simple Voice Chat connection. Confirm A does **not** get placed into public Night Chat.
7. A uses the leave-private key and returns to shared Night Chat.
8. Repeat a private chat, then have A fully disconnect from Minecraft. Confirm the Storyteller is released and A rejoins the normal authoritative route after reconnecting.
9. Start Day and confirm ordinary shared-night users return to proximity voice while deliberate private chats/holds remain private until ended or reset.

## Build note

The project still targets Minecraft 26.3 / Java 25. The rewritten Night Chat manager passes the local stub compile used for A.2/A.3 validation; a full Loom build still requires the Java 25 toolchain and Minecraft/Fabric dependencies.
