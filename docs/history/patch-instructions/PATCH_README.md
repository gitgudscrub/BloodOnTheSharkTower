# Blood on the Sharktower 1.1.0-dev A — Hand Voting Foundation

Apply this patch over the working **1.0.3-dev Private Chat Anytime** project.

## Adds
- Server-authoritative raised-hand state.
- **U** keybind to raise/lower your voting hand.
- `/bots hand`, `/bots hand up`, `/bots hand down`, `/bots hand status` test/accessibility commands.
- Grimoire hand markers beside raised players.
- Live **Hands Required**, **Hands Raised**, and (during a vote) **Votes Counted** displays.
- Dynamic hands-required threshold that respects the current player/Storyteller on the block.
- Dead-player ghost-vote validation.
- Existing logical vote backend seeded from raised hands when the Storyteller starts a vote.

Hands remain visible through the vote and clear when the vote resolves.

## Test
1. `./gradlew.bat clean runClient`
2. `/bots testscript`
3. `/bots testseats 7`
4. `/bots nominations open`
5. Press **U** or use `/bots hand`.
6. Open the Grimoire with **R** and check the hand marker + voting totals.
7. Nominate a seat and run `/bots runVote`.
8. Confirm **Votes Counted** reflects the raised hand(s), then `/bots resolveVote`.
