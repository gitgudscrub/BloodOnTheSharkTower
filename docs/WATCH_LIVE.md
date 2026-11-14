# Watch live (Sharktower website)

The Sharktower website's **Watch live** page lets Discord members follow a game
without Minecraft: the table (seats, names, public deaths, day or night, who is
in which voice room), voice from any room, and a spectator-only text chat.

## What the server sends

`spectator/SpectatorBridge.java`, using the same
`config/blood_on_the_sharktower/site-link.json` as server updates:

- `POST /internal/minecraft/live` about twice a second while the server ticks
  (only when something changed, or every 2 s): the public table state. Built
  on the server thread from seats, public deaths (`DeathVisibility`, as
  players see them), phase, Storytellers and each person's voice room.
  **Never** roles, alignments, reminders or other Grimoire data.
- `POST /internal/minecraft/audio` every 100 ms while the site wants audio:
  raw Opus frames from Simple Voice Chat, tagged with the speaker and room. The
  server does not decode anything. The site answers both posts with
  `{"audio": true|false}`: true only while someone is listening and the owner
  has spectator voice switched on. While false, frames are dropped at the
  microphone hook.

Voice rooms (`NightChatManager.spectatorRoom`): `near` (proximity), `town`,
`zone:<name>`, `house:<seat>`, `st:<player uuid>` (a Storyteller private chat).
A player waiting alone on hold is in no room. Whispers are included.

Nothing comes back into the game: spectators can listen and talk among
themselves on the website only.

## Website side

Sharktower repo, `sharktower-bot/events/app/spectate.py` and
`UPGRADE-v13.32.md`. Members only; the owner can switch spectator voice off on
the Minecraft server page, and every spectator's visits, listens and chat are
kept in an owner-only spectator log.
