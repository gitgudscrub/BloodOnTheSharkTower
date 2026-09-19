# Blood on the Sharktower 1.0.1-dev — Simple Voice Chat foundation

This milestone adds the first native Simple Voice Chat integration on Minecraft 26.3.

## Added

- Simple Voice Chat API `2.6.20` compile dependency.
- Simple Voice Chat Fabric `2.6.23+26.3` as a development runtime dependency.
- Fabric `voicechat` entrypoint: `SharktowerVoicechatPlugin`.
- Required `voicechat_api >= 2.6.20` dependency in `fabric.mod.json`.
- Lifecycle diagnostics for API initialization, server start/stop, and the local development client's voice connection.
- `/bots voice` and `/bots voice status` diagnostics.
- Voice Chat status line in `/bots status`.

## Deliberately not added yet

This release does **not** alter voice routing. Proximity chat and Simple Voice Chat groups continue to behave normally. It does not yet implement dusk/night isolation, Storyteller chat, spectator filtering, or reconnection recovery.

Those behaviours are intentionally reserved for the Night Chat milestone so a problem can be isolated to either the base Simple Voice Chat integration or the later routing logic.

## Development runtime

The Gradle runtime dependency points to CurseForge file `8886980`, which is the Fabric `2.6.23+26.3` build of Simple Voice Chat.

Production/server installs should still include the normal Simple Voice Chat Fabric jar alongside Blood on the Sharktower.

## Test

Run:

```text
/bots voice status
```

Expected in an integrated single-player dev world after Voice Chat connects:

```text
Simple Voice Chat mod: 2.6.23+26.3
BOTS plugin initialized: YES
Voice server lifecycle: ONLINE
Local client voice connection: CONNECTED
Audio routing: VANILLA SIMPLE VOICE CHAT (Night Chat not enabled yet)
```

The exact API metadata version displayed can differ from the compile target because the full Simple Voice Chat mod supplies its own nested API module.
