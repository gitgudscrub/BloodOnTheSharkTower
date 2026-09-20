# Blood on the Sharktower 1.0.2-dev — Shared Night Chat + Private ST Rooms

This revision changes Night Chat to match the intended Clocktower flow.

## Shared Night Chat

- At NIGHT, every seated non-Storyteller joins one hidden isolated **BOTS Night Chat** group.
- Players can talk to each other from their houses all night.
- DAY, SETUP, soft reset and hard reset return everybody to normal Simple Voice Chat proximity audio.
- A seated player who reconnects to Simple Voice Chat during NIGHT is restored to the shared Night Chat group from server-authoritative state.

## Temporary private Storyteller conversations

Private voice rooms now exist only when a player and Storyteller explicitly agree to talk.

Storyteller -> player:

- `/bots private invite <seat>` sends that player a clickable **[JOIN PRIVATE CHAT]** message.
- `/bots teleportToSeat <seat>` also sends the invitation automatically when a claimed Storyteller arrives at a configured seat home during NIGHT.
- The player is not moved until they click the invitation.

Player -> Storyteller:

- `/bots private request` sends every online claimed Storyteller a clickable **[ACCEPT]** message.
- The first Storyteller to accept wins the request.

While private:

- Only the Storyteller and player are in the temporary isolated group.
- `/bots private leave` from either participant ends the conversation.
- The player returns to shared Night Chat; the Storyteller returns to normal voice.
- Invitations expire after 60 seconds.
- Disconnecting from voice chat ends a private session rather than restoring stale private state on reconnect.

Compatibility aliases remain:

- `/bots nightchat join <seat>` now sends a private invitation instead of forcibly joining a room.
- `/bots nightchat leave` behaves like `/bots private leave`.

## Diagnostics

`/bots nightchat status` now reports:

- shared Night Chat active/inactive
- active private session count
- pending invitation count
- BOTS-managed voice connections
