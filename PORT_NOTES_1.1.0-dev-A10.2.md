# Blood on the Sharktower — 1.1.0-dev A.10.2

## End-game UI hotfix

A.10.2 fixes the two layout problems found while testing A.10.1:

- **Setup HUD no longer overlaps the end-game banner.** If a test game is ended while the phase is still SETUP, the orange setup card is suppressed for the whole end-game reveal period. It returns normally if the reveal is cancelled/reset.
- **Final Grimoire no longer collides with its header/footer.** Player reveals use a responsive centred grid with a protected title area and protected button area. Two-player tests now render side-by-side instead of at 12/6 o'clock, and larger games automatically use additional rows/columns.
- Final Grimoire entries now have a subtle dark card behind them so names/roles remain readable over the blurred world.

No game logic, end-game timing, role reveal data, or reset behaviour was changed.
