# 1.1.0-dev A.9 — Travellers & Exile Voting

## Player flow
- A seated player with a Traveller role is automatically recognised as an active Traveller.
- During Day, any active seated player may call for an active Traveller to be exiled.
- Player UI: click the Storyteller in the Grim, then choose `Call for Exile` and the Traveller.
- Exile is independent of whether ordinary nominations are open, but an ordinary nomination/election and an exile election cannot run simultaneously.

## Storyteller flow
- Storyteller Tools now includes `Traveller Exile`.
- Choose a caller and active Traveller, call the exile, start the physical exile clock, then resolve it.
- Count -1/+1 and clear-override controls are available once the clock completes.
- `Cancel Vote` stops the clock but keeps the exile call. `Cancel Exile` removes the whole call.

## Voting rules implemented
- Uses the same server-authoritative voting hand and clockwise physical clock as ordinary nominations.
- Uses `minute_hand_exile.png` for the exile sweep.
- All active seated players are counted by the exile clock, including dead players and Travellers.
- Previously exiled Travellers are excluded from later exile/election support, but remain social/voice participants.
- Threshold is half of active players, rounded up.
- Dead players may support exile regardless of ordinary ghost-vote state; exile never consumes the ghost vote.
- One clock tick sound is emitted for each player counted.

## Exiled Traveller state
- Exile does not set the player dead.
- Exiled Travellers receive a separate EXILED marker in the Grim.
- They cannot nominate, call another exile, or vote in later election clocks. They may still use shared Night Chat and private Storyteller chat.
- Exile state persists through later days/nights in the same match and is cleared by Full Reset/Game Complete.

## Commands
- `/bots callForExile <seat>` — player calls for that Traveller's exile.
- `/bots runExileSupport` — Storyteller starts the physical exile clock.
- `/bots resolveExile` — resolve after the clock reaches everyone.
- `/bots cancelExileVote` — stop the clock but keep the exile call.
- `/bots setExileCount <0-99>` — Storyteller override after clock completion.
- `/bots clearExileCount` — clear Storyteller override.
- `/bots exileStatus` — inspect active exile/support state.
- `/bots resetExile` — cancel the active exile call.
- `/bots exileVote <true|false>` remains as a direct testing/debug path.

## Suggested multiplayer test
1. Assign TestPlayer1 a normal role and TestPlayer2 a Traveller role such as THIEF.
2. Commit setup and start Day.
3. With ordinary nominations closed, have TestPlayer1 call for TestPlayer2's exile.
4. Raise/lower both players' hands, start the exile clock, and verify the alternate exile hand sweeps both seats.
5. Verify exactly one clock tick per counted player and forced seating during the sweep.
6. Resolve with enough support and confirm TestPlayer2 is EXILED rather than dead.
7. Verify TestPlayer2 is absent from a subsequent normal vote but still joins shared Night Chat and may use private Storyteller chat.
8. Repeat with TestPlayer1 dead and an already-spent ghost vote; they should still be allowed to vote on exile without changing ghost-vote state.
9. Full Reset and verify the Traveller is active again.

A.9 was source-level validated in the build sandbox; runtime validation should be performed with the Java 25 dev harness used for A.8.
