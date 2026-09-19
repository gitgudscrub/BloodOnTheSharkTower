# 1.1.0-dev A.2 — Automated Storyteller Night Chat

This update keeps the A.1 Dusk/Dawn lifecycle and changes the night voice flow to match the intended Storyteller workflow.

## Shared Night Chat

- At Night, every committed seated player joins the hidden isolated **BOTS Night Chat** room unless they are currently in a private room.
- Active Storytellers now join that same shared Night Chat automatically even when they are not seated.
- Storytellers therefore hear the public night conversation while moving between houses.
- Claiming Storyteller control during Night joins the shared Night Chat automatically.
- Reconnecting Simple Voice Chat during Night restores the Storyteller to the shared room just like a seated player.

## House private-chat automation

- `/bots teleportToSeat <seat>` during Night still sends that occupied seat a clickable private-chat invitation.
- House-generated invitations are marked as **house-bound**. Manual `/bots private invite <seat>` and player-requested private chats remain phase-independent and are not tied to house distance.
- If the Storyteller moves away before a house invitation is accepted, the stale invitation is cancelled.
- If the invitation is accepted, the Storyteller and player enter the private voice room as before.
- The Storyteller's configured seat-home position is treated as the centre of that house. Moving more than 8 blocks away automatically detaches the Storyteller from the private room.
- Teleporting directly to another configured house also detaches the Storyteller immediately rather than waiting for the movement check.

## Asymmetric private-room exit

This is the key behavioural change in A.2:

- **Storyteller leaves:** the Storyteller immediately rejoins shared Night Chat. The player stays isolated in the private room.
- **Player leaves:** the player rejoins shared Night Chat. If the Storyteller is still attached, the Storyteller is also returned to shared Night Chat because that private room has ended.
- A player left in a private hold sees `/bots nightchat whoami` report `PRIVATE HOLD` and can use `/bots private leave` whenever they are ready.
- This lets the Storyteller teleport rapidly from player to player without unexpectedly dropping the previous player into the public conversation.

## Reconnect behaviour

- A Storyteller voice disconnect while privately speaking to someone detaches only the Storyteller; the player remains private.
- A player in a private hold keeps that hold across a transient Simple Voice Chat reconnect when possible.
- If the player themselves disconnects while the Storyteller is still attached, the Storyteller is freed back to the normal route so the night cannot become stuck.

## Suggested multiplayer smoke test

1. Seat Players A and B, claim a separate Storyteller, then start Night.
2. Confirm A, B and the Storyteller all report `SHARED NIGHT CHAT` with `/bots nightchat whoami`.
3. Storyteller teleports to A's house. A accepts the invitation. A and the Storyteller should now be private; B remains public.
4. Storyteller teleports to B's house. The Storyteller should immediately rejoin public Night Chat and B should receive an invitation.
5. A should **remain isolated**, with `/bots nightchat whoami` reporting `PRIVATE HOLD`.
6. A runs `/bots private leave` and only then rejoins public Night Chat.
7. B accepts. Instead of teleporting, the Storyteller walks more than 8 blocks away from B's configured home position. The Storyteller should automatically rejoin public Night Chat while B remains private.
8. B leaves manually and rejoins Night Chat.
9. Start Day and confirm all ordinary shared-night participants return to proximity voice.

## Build note

The project still targets Minecraft 26.3 / Java 25.
