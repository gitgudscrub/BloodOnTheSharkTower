# A.11 Live Player Heads Hotfix

This patch sits on top of the A.11 GUI-scale hotfix.

It fixes stale/blank player heads caused by the Grimoire using an older `grimoireSeatNumbers` occupant after the live `playerSeatNumbers` map had changed. Seat occupancy is now reconciled by seat number, with the live occupant taking priority.

The screen also watches the seat layout while open and rebuilds its role/head widgets when the occupants change. Face rendering is attempted directly instead of being blocked by a potentially one-tick-stale `connectedPlayers` list.
