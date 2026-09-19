# 1.0.2-dev Night Chat revision

This build replaces the original per-seat Night Chat rooms with one shared night room plus temporary invitation-based private Storyteller rooms.

Recommended first test:

1. Seat two real players and claim one Storyteller.
2. Set phase to NIGHT.
3. Confirm the seated players can hear one another in shared Night Chat.
4. Storyteller runs `/bots private invite <seat>`.
5. Target clicks **[JOIN PRIVATE CHAT]**.
6. Confirm only that player and Storyteller hear one another.
7. Run `/bots private leave`; player should return to shared Night Chat.
8. Have a player run `/bots private request`; Storyteller clicks **[ACCEPT]**.
9. Test a voice reconnect during NIGHT; the player should return to shared Night Chat.
