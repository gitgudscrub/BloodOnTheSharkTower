# 1.1.0-dev A.8 - Local Multiplayer Development Harness

A.8 begins the multiplayer reliability pass by adding repeatable local test identities.

- Added Loom runs for a loopback dev server, Storyteller and five dummy players.
- Each client uses an independent run directory.
- Clients automatically connect to `127.0.0.1:25565`.
- Dev server preparation writes a loopback-only, offline-mode configuration.
- The `Storyteller` test identity is automatically OP level 4.
- Added Windows helper launchers for a core ST + 2 player test and an extended ST + 5 player test.
- No gameplay rules were changed in this harness update.
