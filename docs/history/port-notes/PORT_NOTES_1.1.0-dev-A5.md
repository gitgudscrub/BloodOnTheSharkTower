# Blood on the Sharktower 1.1.0-dev A.5
## World Clock Hands + enforced vote seating

A.5 restores the physical town-square presentation around the A.4 nomination system. It does not create a second vote engine: the renderer follows the existing server-authoritative `ElectionState`, so the HUD, hand locking, ghost votes and world clock remain synchronized.

### Nomination pointer

- Once a nomination is accepted, the red minute hand appears in the configured town-square centre and points toward the nominee.
- The target is followed from the player's live X/Z position, so a nominee may pace or fidget during their defence without losing the pointer. Vertical movement is deliberately ignored, so jumping does not make the hand bob.
- The shorter blue hour hand points toward the nominator before the vote.
- If a target disconnects, the renderer falls back to their configured town-square seat.

### Running the vote

- **Start Vote** still uses the A.4 clockwise order: the seat after the nominee is first and the nominee is last.
- The red hand smoothly sweeps from seat to seat over the same one-second interval used by the server vote clock.
- The shorter blue hand points at the nominee while the vote is running.
- The imported `vote_start` and `clock_ticking` sounds play from the town-square centre. Longer votes refresh the finite ticking clip.
- When the last seat is counted the physical seat lock ends immediately; the Storyteller may then adjust/finish the result normally.

### Vote seating

- When the vote starts, every seated player is returned to their configured town-square seat. Storytellers are not included in the player-seat map and are therefore not locked.
- If a vanilla Minecraft 26.3 Cushion is present at that seat, Sharktower mounts the player onto it so they visibly sit. If a map uses another chair build, the configured seat position is used as a positional fallback.
- Attempts to dismount or walk away while the clock is running are corrected on the server. Players may still look around, talk, and change their voting hand until their seat is counted.
- Living and dead seated players use the same physical lock; dead players retain the A.4 ghost-vote rules.
- A player reconnecting during the active sweep is returned to their seat on the next server tick.
- Cancel Vote, Full Reset, or natural completion of the clock removes enforcement. Players are not forcibly ejected from cushions afterward; they can stand when they choose.

### Clock setup

The clock centre defaults to the average of configured town-square seat positions. This means existing circles normally work without another setup step. If the visual centre needs nudging, stand where the hands should pivot and run:

```text
/bots setClockCenter
```

The default hand scale is `4.0`. It can be adjusted with:

```text
/bots setClockHandScale 4.0
```

Accepted range: `0.5` to `12.0`. Full Reset clears the explicit centre and restores scale `4.0`.

### Recommended multiplayer test

1. Use a configured town square with at least 5 occupied seats and vanilla cushions.
2. Open nominations and nominate a player. Walk/jump the nominee around before starting the vote: the red hand should follow them horizontally and the blue hand should remain on the nominator.
3. Press **Start Vote**. All seated players should return to/mount their cushions and be unable to leave during the sweep.
4. Toggle hands before each player's seat is reached and verify the existing A.4 count locks at the same moment the red hand reaches them.
5. Try Shift/dismount during the active sweep; the server should reseat the player.
6. Allow the final (nominee) seat to be counted. Players should immediately regain the ability to stand even before the Storyteller presses **Finish Vote**.
7. Repeat once with a dead player using their ghost vote.
8. Cancel a second vote midway and verify players are immediately free.

### Build note

This source targets Minecraft 26.3 / Java 25. The project still requires the normal local Java 25 Fabric build environment for a full compile/run smoke test.
