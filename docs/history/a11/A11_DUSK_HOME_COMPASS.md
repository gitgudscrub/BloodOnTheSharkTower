# A.11 Dusk Home Compass

Apply this after the current A.11 patches (including frozen phase time and Day private-chat HUD).

Behavior:
- When the game enters Night/Dusk, every connected seated player except Storytellers receives a temporary **Home Compass** if their seat has a configured home.
- The compass points to that seat's existing `/bots setSeatHome` position. No additional house setup is required.
- Dead players still receive it; Storytellers do not.
- At roughly 4 blocks from the configured home, the temporary compass is removed and the player is marked as arrived for that Night.
- The compass is not re-issued after the player has arrived, even if they later walk away during the same Night.
- Players reconnecting before reaching home are reconciled automatically.
- Dawn, Setup, and end-game state remove any remaining Sharktower Home Compasses. A player who reconnects during Day is cleaned up automatically.
- The item has a Sharktower `custom_data` marker, so cleanup only removes the mod's own Home Compass and never an ordinary compass.
- The compass is placed into an empty hotbar slot when possible, otherwise another empty main-inventory slot. Existing items are never overwritten or dropped.

Suggested smoke test:
1. Configure homes for two seats with `/bots setSeatHome <seat>`.
2. Start from Day at the town square.
3. Enter Dusk/Night.
4. Confirm both players receive `Home Compass` and each compass points toward their own house.
5. Walk one player within ~4 blocks of their home; their compass should disappear and `HOME REACHED` should appear in green chat.
6. Open Dawn; any remaining Home Compasses should be removed.
