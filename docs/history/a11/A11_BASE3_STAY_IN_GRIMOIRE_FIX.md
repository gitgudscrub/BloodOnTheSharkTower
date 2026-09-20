# A.11 — Base 3 Stay in Grimoire Fix

Selecting Trouble Brewing, Bad Moon Rising, or Sects & Violets no longer drops
the Storyteller back to the world.

The Base 3 picker now:
1. sends the load request to the server,
2. returns to the Grimoire immediately,
3. refreshes/reopens the Grimoire once the authoritative script sync arrives.

This preserves the existing server-sync safety while avoiding the UI closing
between Script Builder and Grimoire.
