# 0.6.0 — Presentation bulk port

This is the first large visible-client batch of the private BOTB -> BOTS 26.2 port.

Restored in this milestone:

- Fabric 26.2 HUD registration via `HudElementRegistry`
- persistent role HUD using original role textures
- phase/script setup HUD
- nominations/voting/marked-player election HUD
- synchronized timer HUD
- visual grimoire / Assign Roles screen
- script reference screen
- role catalog
- character details screen
- storyteller tools launcher
- timer screen
- original-style keybind family under the Sharktower namespace

Default presentation keys retained from BOTB where practical:

- `Z` — toggle role HUD
- `R` — open grimoire
- `K` — role catalog
- `C` — current script
- `X` — current role details
- `Y` — timer
- `I` — storyteller tools
- `B` — toggle all BOTS HUD

The screens in 0.6.0 are the 26.2 presentation foundation. The original drag/drop grimoire editing, night-order interaction, remote custom-role textures, sounds, world clock hands and mixin-based presentation hooks remain for the integration batch.
