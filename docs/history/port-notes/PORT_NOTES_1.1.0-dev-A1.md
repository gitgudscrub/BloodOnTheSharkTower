# 1.1.0-dev A.1 — Night Chat Lifecycle Integration

This hotfix keeps the 1.1.0-dev A hand-voting work intact while moving the existing 1.0.x Night Chat prototype onto the real game phase lifecycle.

## Real Dusk / Dawn path

- Added `/bots dusk` as the real transition into the next Night.
- Added `/bots dawn` as the real transition into Day.
- Storyteller Tools now has **Start Night** and **Start Day** buttons that call the same authoritative server path.
- `/bots testphase night|day|setup` now reuses that implementation instead of maintaining separate test-only phase logic.
- Dusk/Dawn clears stale daytime nomination/vote/mark/exile state while preserving ghost-vote history.

## Voice routing

- Dusk enables the hidden isolated shared **BOTS Night Chat** room for all seated non-Storytellers.
- Dawn releases shared-night players back to normal Simple Voice Chat proximity audio.
- If the Simple Voice Chat server starts/restarts while the game is already at Night, BOTS automatically reconciles the shared room.
- A player's Simple Voice Chat reconnect during Night restores them to shared Night Chat.
- Committing an edited live seat map during Night resyncs the shared room.
- Full/soft game reset still clears all BOTS voice routing and private sessions.

## Storyteller safety

- Claiming Storyteller control while Night Chat is active now explicitly removes that player from the shared player room.
- Releasing Storyteller control restores them to shared Night Chat only if they are also a committed seated player.
- Existing private Storyteller conversations are not disturbed by ordinary phase changes.
- The house-arrival flow remains: Storyteller arrival/invite -> temporary private room -> leaving returns the player to shared Night Chat.
- Teleporting the Storyteller directly to a different configured seat now ends the previous private room automatically, returns that player to shared Night Chat, then invites the new seat.

## Diagnostics

New command:

```text
/bots nightchat whoami
```

It reports whether the executing player is currently routed to proximity voice, shared Night Chat, a private Storyteller room, or is waiting for their Simple Voice Chat connection to come online.

## Suggested multiplayer smoke test

1. Seat at least two real players and claim a separate Storyteller.
2. Run `/bots dusk` or press **Start Night** in Storyteller Tools.
3. On both players run `/bots nightchat whoami`; both should report `SHARED NIGHT CHAT` and should hear one another from separate houses.
4. Storyteller runs `/bots teleportToSeat <seat>` and the target accepts the private chat invitation.
5. Target and Storyteller should hear one another privately; the other Night Chat player should not hear them.
6. Storyteller runs `/bots teleportToSeat <another-seat>`; the first target should immediately return to shared Night Chat and the new occupied seat should receive an invitation. `/bots private leave` remains available to either participant.
7. Disconnect/reconnect the target's Simple Voice Chat during Night; `/bots nightchat whoami` should return to `SHARED NIGHT CHAT`.
8. Run `/bots dawn` or press **Start Day**; shared-night players should return to normal proximity voice.
9. Run a soft/full reset during a private room and confirm every BOTS voice route is cleared.
