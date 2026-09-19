# Blood on the Sharktower 1.1.0-dev A.4 — Nomination & Execution Flow

A.4 turns the existing hand-voting foundation into a Storyteller-controlled BOTC nomination loop.

## Added

- Storyteller **Nomination Flow** screen, reachable from Storyteller Tools and the Grimoire.
- Select a **nominator** and **nominee** from seated players.
- Open/close nominations explicitly.
- Server-authoritative clockwise vote clock.
- Vote order starts with the seat after the nominee and ends on the nominee.
- Default clock speed: one seat per second (20 server ticks).
- Hands remain changeable until the clock reaches that player's seat, then that vote is locked.
- Normal majority is based on living non-Traveller players.
- Current highest vote / player on the block is tracked across nominations.
- Equal high votes clear the block as a tie.
- Dead players retain a single ghost vote; it is consumed only when a locked YES vote resolves.
- Storyteller can adjust the final vote count after the clock completes without changing which players physically voted.
- Storyteller controls for **Finish Vote**, **Cancel Vote**, **Cancel Nomination**, **Execute Marked**, and **No Execution**.
- Vote state/HUD now shows current clock seat, counted total, required total, completion state and current block.
- Public chat announcements for nominations, vote start, result and execution.

## Deliberate Storyteller authority

The mod does not attempt to automatically enforce every character-specific voting interaction. The Storyteller remains authoritative and can override the final count for effects such as unusual homebrew characters or rulings.

## Recommended multiplayer test

1. Seat 7+ players and mark at least one dead.
2. Open **Storyteller Tools -> Nomination Flow**.
3. Open nominations.
4. Pick a nominator and nominee, then **Submit Nomination**.
5. Have players raise/lower hands while waiting for the clock.
6. Press **Start Vote** and verify the clock begins at the seat after the nominee.
7. Verify each player's hand is locked only when their seat is reached, with the nominee counted last.
8. Let the clock complete, optionally use **Count -1 / +1**, then **Finish Vote**.
9. Verify the player with the current highest qualifying vote is shown as **ON THE BLOCK**.
10. Run another nomination with an equal total and verify the block clears on a tie.
11. Cast a dead player's ghost vote and verify it cannot be counted again later.
12. Press **Execute Marked** and verify the player dies and nominations close.
13. Repeat a day using **No Execution**.

## Verification performed in this environment

- Core `ElectionState` clockwise ordering and completion test: PASS.
- Java source-level dependency check reached the expected missing Minecraft/Fabric classes; no independent Java 25 / full Minecraft 26.3 Gradle toolchain is available in this sandbox for a complete mod build.
