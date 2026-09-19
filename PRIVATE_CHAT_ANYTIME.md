# Private Chat Anytime

Private Storyteller conversations are not gated by Night Chat.

- Players may request a private chat from the centre Storyteller token during setup, day, or night.
- `/bots private request` works at any game phase.
- Storytellers may `/bots private invite <seat>` at any game phase.
- Accepted private rooms remain isolated temporary Simple Voice Chat groups.
- During Night, an active Storyteller normally sits in the shared Night Chat room with the players.
- If the **Storyteller** leaves a private room, the Storyteller returns to shared Night Chat while the player remains private until they choose to leave.
- If the **player** leaves, the player returns to shared Night Chat at Night or proximity voice during Day/Setup; an attached Storyteller is released at the same time.
- House-bound private chats created by `/bots teleportToSeat <seat>` automatically detach the Storyteller when they leave that configured house area.
- Manual private invites and player-requested private chats are not tied to house distance.
- Ordinary phase changes do not terminate an active private conversation or a player's private hold.
- Game reset clears all private sessions and pending requests.

- A rebindable **Leave Private Storyteller Chat** key (J by default) provides a player-friendly alternative to `/bots private leave`; the private-hold HUD shows the current binding.
- A temporary Simple Voice Chat dropout preserves a player's private hold. A full Minecraft disconnect cleans that player's private session and frees the Storyteller.
