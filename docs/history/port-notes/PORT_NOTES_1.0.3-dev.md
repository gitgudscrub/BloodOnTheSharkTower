# 1.0.3-dev — Storyteller Centre

- Renders claimed Storyteller(s) in the centre of the Grimoire using their Minecraft head/name.
- Moves Players/Storytellers counters below the central Storyteller area.
- Clicking a Storyteller opens player-safe actions that do not claim Storyteller control.
- Request Private Chat targets the selected Storyteller and reuses the 1.0.2 temporary private voice-room flow.
- If Atheist is on the current script, the menu exposes Nominate Storyteller.
- Storyteller nomination is server-authoritative and uses the normal vote flow; the UI does not reveal whether Atheist is actually in play.
- Supports up to three claimed Storytellers in a compact centre row.
- Hotfix: private Storyteller chat requests/invites/acceptance now work during SETUP, DAY, and NIGHT.
- Private sessions no longer get torn down simply because the game changes phase.
- Leaving a private room returns a player to shared Night Chat only when Night Chat is active; otherwise they return to proximity voice.
- Full/soft game reset still forcibly clears all private rooms, invites, and BOTS-managed voice routing.
