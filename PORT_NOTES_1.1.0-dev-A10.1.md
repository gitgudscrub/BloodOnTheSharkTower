# Blood on the Sharktower — 1.1.0-dev A.10.1

## Original-style game-end cinematic

A.10.1 restores the presentation style of BOTB's original `GameEndAnimationHUD` / `GameEndHoldingScreen` rather than ending on only a small persistent banner.

When the Storyteller declares Good or Evil the clients now:

1. fade the world to black;
2. show **VICTORY** or **DEFEAT** from the local player's final alignment;
3. show **THE GOOD TEAM WINS** or **THE EVIL TEAM WINS**;
4. reveal every seated player in seat order, one per second, with head, role token, final alignment and dead/exiled state;
5. hold on the complete cast;
6. fade back to the world and continue into A.10's persistent reveal mode / Final Grimoire.

The existing original `game_end.ogg` asset is now registered and played once when reveal mode begins. Changing the declared winner during reveal does not replay the sound or restart the cinematic.

`Cancel Reveal` and `Reset for Next Game` immediately cancel any in-progress cinematic.
