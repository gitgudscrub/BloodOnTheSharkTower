# 1.1.0-dev A8.6 - Night Chat diagnostics + dev voice hardening

- Night Chat routing now catches Simple Voice Chat API failures per participant instead of letting Brigadier surface only `An unexpected error occurred...`.
- `/bots voice status` now reports server-side Simple Voice Chat connection information for every online Minecraft player.
- The misleading dedicated-server `Local client voice connection` diagnostic was replaced with per-player diagnostics.
- The last Night Chat routing exception is retained and displayed in `/bots voice status`.
- The local development harness now forces a loopback Simple Voice Chat server on UDP 24454 and explicitly enables voice groups on every dev-server launch.
- Existing Simple Voice Chat properties are preserved; only `port`, `bind_address`, `voice_host`, and `enable_groups` are forced for the local harness.
