# 1.1.0-dev A — Hand Voting Foundation

This milestone replaces the old physical/redstone vote intent with a server-authoritative raised-hand state while keeping the existing BOTB election backend intact.

## Behaviour

- Seated players can raise/lower a voting hand while nominations are open.
- Default key: **U** (chosen to avoid Simple Voice Chat's default H = Hide Voice Chat Icons binding).
- Raised hands are synchronized to every client.
- The Grimoire draws a small hand marker beside each raised player's head/name.
- The daytime HUD and Grimoire show:
  - **Hands Required**
  - **Hands Raised**
  - **Votes Counted** while a logical vote is running.
- Hands Required is dynamic:
  - no current block: half the living non-Traveler players, rounded up;
  - somebody already on the block: max(base threshold, current high vote + 1).
- Dead players may raise a hand only while their ghost vote is still available.
- Starting a vote seeds the existing logical vote map from the raised hands.
- Hands stay visible through the vote and clear when the vote resolves, ready for the next nomination.

## Deliberately deferred to 1.1.0-dev B

- Clockwise/seat-by-seat vote sweep.
- Animated/current voter indicator.
- Automatic lock-in as the vote reaches each seat.
- Final Storyteller vote controls/presentation polish.
- Vote-item interaction, if still desired after keybind testing.

## Suggested test

1. `/bots testscript`
2. `/bots testseat 1`
3. `/bots testseats 7`
4. `/bots nominations open`
5. Press **U** and confirm your hand marker and totals update.
6. Lower/raise several times and ensure totals remain synchronized.
7. Nominate a seat, start the vote, and confirm the raised hand is seeded into the logical vote count.
8. Resolve the vote and confirm raised hands clear.
