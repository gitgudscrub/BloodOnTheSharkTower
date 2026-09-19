# 1.1.0-dev A.8.2 — Minecraft 26.3 client API hotfix

Compilation-only hotfix discovered by the local multiplayer development harness.

- `KeyInputHandler`: settings opened from the keybind now uses no parent screen instead of the removed `Minecraft.screen` field.
- `ClockHandsRenderer`: `GameRenderer.getMainCamera()` updated to `mainCamera()` for the 26.2/26.3 API.
- `ClockHandsRenderer`: removed the old `LightTexture` dependency and uses `RenderTypes.FULL_BRIGHT_LIGHTMAP`.

No gameplay behaviour is intentionally changed.
