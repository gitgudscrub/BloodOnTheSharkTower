# Port notes — 0.3.1

## Restored from the original BOTB architecture

The original 1.21.1 mod's `SendScriptS2CPayload` transports a complete `Script` using `Script.PACKET_CODEC`. The original script codec serializes the script's `rawJson`, GZIP-compresses it, prefixes the compressed byte length, and reconstructs the script with `Script.fromJson` on receipt.

0.3.1 restores that design on Minecraft 26.2 rather than inventing a separate per-field script protocol.

## Why raw JSON transport is useful

This preserves custom role definitions and script metadata without maintaining a second network schema. It also means future script fields can continue travelling with the source JSON as long as the parser understands them.

## Safety limits retained

- Maximum compressed packet body: 1 MiB
- Maximum decompressed JSON: 16 MiB
- Maximum JSON nesting depth: 64
- Maximum extra almanacs: 32

## Intentionally deferred

This slice does **not** yet restore:

- `RequestScriptC2SPayload`
- `SendScriptC2SPayload` (Storyteller/client script upload)
- script-builder UI
- script persistence/config directories
- role assignment packets
- seating/grimoire packets

Those will be restored after the server-to-client Script codec is proven stable.
