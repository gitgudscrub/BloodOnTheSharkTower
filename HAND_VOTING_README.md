# Sharktower Hand Voting

Player intent is now represented by a synchronized hand rather than a redstone lever.

**Default key:** U — Raise / Lower Voting Hand

The binding is fully rebindable in Minecraft Controls, and the hand HUD displays the player's current binding rather than assuming U.

The server remains authoritative. The client only requests a toggle; the server validates seating, nomination state, Storyteller status, and ghost-vote availability before broadcasting the result.
