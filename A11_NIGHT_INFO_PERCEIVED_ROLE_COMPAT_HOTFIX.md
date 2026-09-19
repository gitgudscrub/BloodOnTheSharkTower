# A.11 — Night Info / Perceived Role Compatibility Hotfix

The perceived-role patch replaced several shared state/network files with a
version that predated the Night Visit Info Automation 2 additions. That removed
Storyteller-only daily info fields and broadcast helpers while the daytime
managers still called them, causing the compile errors after the Drunk/Marionette
perceived-role work.

This hotfix merges both feature sets rather than rolling either one back.

Restored Night Visit automation pieces:
- last executed role, Demon-voted-today, Minion-nominated-today
- `resetDailyNightInfo()` lifecycle reset
- StorytellerNightInfo payload receive/send/broadcast helpers
- source-role reminder action used by Night Info Markers

Preserved perceived-role pieces:
- Drunk / Marionette perceived identities
- perceived-role validation and SEND ROLES safety checks
- Storyteller-only perceived-role Grimoire synchronization
- player-facing fake-role delivery
