# A.11 — Persistent Map Configuration

This patch makes physical Sharktower map setup survive a complete Minecraft / server restart.

Automatically persisted:
- `/bots setSeatHome <seat>` positions
- `/bots setTownSquareSeat <seat>` positions
- voting clock centre
- voting clock hand scale
- all daytime private-chat names
- every private-chat entrance marker
- every private-chat exit marker

The data is saved immediately whenever one of those settings changes and is loaded automatically when the mod starts.

Storage file:
`config/blood-on-the-sharktower/map-setup.properties`

This is installation/server configuration rather than match state. The generated Simple Voice Chat groups do not need to be saved; Sharktower recreates them automatically from the saved private-chat definitions whenever Day Chat becomes active.

## Hard Reset

Hard Reset no longer deletes physical map setup.

It can still clear the current game/script/player seat assignments as before, but configured house positions, town-square positions, clock configuration, and private-chat gates remain available for the next game and after a server restart.

Explicit Day Chat configuration commands such as `clearZones` still permanently remove those saved areas, as expected.
