# A.11 role-awareness patch

Apply this patch over the current A.11 development project after the seat-compaction, Role Bag -> Grimoire, and player-sidebar patches.

Changes:
- top-centre public role distribution HUD;
- floating role icons above players based on the receiving client's Grimoire knowledge;
- role-information privacy hardening for ordinary clients;
- Role Counts and World Roles settings toggles.

Suggested smoke test:
1. Run Storyteller + at least two player clients.
2. Assign different roles and SEND ROLES.
3. Enter Night or Day. Confirm the top-centre distribution appears.
4. On the Storyteller client, confirm role icons appear above both players.
5. On an ordinary player client, confirm another ordinary player's hidden role icon does not appear.
6. End the game and confirm all role icons become available after the end-game reveal.
