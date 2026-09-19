# A.11 — Reconnect Grimoire Sync Fix

Fixes a Setup reconnect edge case where a player could be auto-seated correctly
but appear in the Storyteller Grimoire as `Seat N` with no player head/name.

Cause: Fabric's JOIN callback can run before the joining/rejoining player is
present in `MinecraftServer#getPlayerList()`. The seat/grimoire broadcast therefore
contained the UUID, while the immediately-built player directory did not yet contain
the player's name or connected status.

The player-directory refresh is now deferred by one server task/tick, matching the
existing disconnect cleanup pattern. Once the joining player is fully in the live
player list, the directory is rebuilt and broadcast to all clients.

Regression case:
1. Setup: Player A joins -> Seat 1.
2. Player B joins -> Seat 2.
3. Player A disconnects -> Player B compacts to Seat 1.
4. Player A reconnects -> receives Seat 2.
5. Storyteller Grim should immediately show Player B at Seat 1 and Player A at
   Seat 2, with both correct names and heads.
