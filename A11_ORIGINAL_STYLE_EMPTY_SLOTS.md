# A.11 — Original-Style Empty Token Slots

Replaces the plain white empty placeholders with a framed empty-slot style that better matches the original mod's visual language.

## What changes

- Empty **main role** slots now render as a dark framed token slot instead of a flat white square.
- Empty **perceived role** slots use the same framed token slot.
- Empty **demon bluff** slots use the same framed token slot.
- Empty **reminder** slots render with a matching smaller framed slot.
- Floating in-world role note icons now use the framed empty slot when the client knows a seat exists but no role has been assigned/noted yet.
- Drunk / Marionette perceived-role overlays keep showing a second slot even when the believed role is still unset, but the slot now uses the new framed style.

## Notes

This patch is visual only. It does not alter any gameplay logic, role assignment logic, reminder logic, or seat logic.
