# Blood on the Sharktower — 1.1.0-dev A.10.3

## Grimoire flow + final reveal polish

A.10.3 continues the A.10 end-game/UI pass with two Storyteller-facing improvements found during playtesting.

- **Role selection now explicitly returns to the Grimoire.** Choosing a player role or Demon bluff returns to the Grimoire instead of dropping the Storyteller back into the world. Escape/Back from the chooser also returns to the Grimoire.
- **Final Grimoire now matches the live Grimoire visual language.** The card/grid reveal from A.10.2 has been replaced by the cleaner radial Grimoire layout: role tokens on the outside, player heads/names on the inside, and seat numbers around the edge.
- **Winner information is moved into the centre of the circle.** This avoids the top-of-screen collision that the earlier two-player reveal had while preserving the normal 12/6-o'clock seat layout.
- **Final alignment remains visible.** Each role token gets a blue/red outline based on the player's actual final alignment, so alignment changes are not hidden by the role token's normal team colour.
- **Hover details keep the ring uncluttered.** Hovering a player shows seat/name, role, alignment, and alive/dead/exiled state in the centre without adding large permanent cards around the circle.
- The lower ring is pulled slightly inward so seat labels stay clear of **Reset for Next Game** / **Close**.

No end-game timing, winner logic, setup logic, role assignment rules, or reset behaviour changed.
