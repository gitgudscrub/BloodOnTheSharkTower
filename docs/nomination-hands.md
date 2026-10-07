# Nomination-only speaking queue — 1.0.2-alpha.6

Hand UI/key input is hidden and disabled during Setup, Night, ordinary Day and the end-game reveal. During open nominations with no active nomination, U joins/leaves an ordered speaking queue. A marked execution target does not disable speaking between nominations. An active nomination uses the existing voting hands, including the discussion before the vote clock. Traveller exile support retains its existing daytime voting controls.

The same mode gate protects the server handler, player HUD, sidebar, Grim and world hand indicators. Server commands/forged player actions cannot raise speaking hands outside the nomination window. Speaking order is retained while a nomination is active, independently of votes, and returns when the nomination ends. Closing nominations or changing day/night clears it; reopening starts empty.

Live checks: confirm no hand panel/icons/input in Setup/Night/Day-before-nominations. Open nominations: queue two players in order. Nominate: voting UI replaces queue without recording queue members as Yes votes. Finish/cancel: queue reappears, including when a player is marked. Close/reopen nominations: empty queue. Repeat with dead players whose ghost vote is spent, and with exile support.
