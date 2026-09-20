# A.11 — Player Public-Info Books for Flowergirl / Town Crier

Adds a temporary player-facing memory aid for two public-information-heavy roles.

## Flowergirl
At Night, once the Flowergirl reaches their configured seat home, they receive a
written book listing every player who cast at least one locked YES vote during the
preceding Day. Repeated YES votes are shown as a count (`x2`, etc.).

## Town Crier
At Night, once the Town Crier reaches their configured seat home, they receive a
written book listing every player who made a nomination during the preceding Day.
Repeated nominations are shown as a count if the rules/setup allowed them.

## Important behavior
- Books contain **public history only**. They never reveal whether the Demon voted
  or whether a Minion nominated.
- The Storyteller's normal Night Visit popup still supplies the role result and
  Droisoned/Vortox warnings.
- A true Drunk/Marionette who believes they are Flowergirl/Town Crier receives the
  matching public-info book for the role they believe they are.
- Books are marked as Sharktower temporary items and are removed automatically at
  Dawn, Setup, endgame, or when the player reconnects outside Night.
- Public Day history resets at each new Dawn.
- The system reuses configured seat homes; no extra commands are required.
