# A.11 Phase Presentation — Minecraft 26.3 compile fix

Apply after the Original Phase Presentation patch.

Fixes the four compile errors introduced by Minecraft 26.3 API naming changes:

- `ServerLevel#setDayTime(long)` -> `ServerLevel#setTimeOfDay(long)`
- `ServerPlayer#playNotifySound(...)` -> `ServerPlayer#playSoundToPlayer(...)`

No gameplay or presentation behaviour is intentionally changed.
