# Grimoire Renderer integration

Blood on the Sharktower can hand final game state to the standalone Grimoire Renderer service without exposing the Discord webhook to Minecraft.

## Lifecycle

- **Script load:** POST `/icons/preload` asynchronously so official/homebrew role art is ready before play.
- **SEND ROLES / game start:** POST `/icons/check` asynchronously as a lightweight readiness verification.
- **Good/Evil win:** POST `/render` asynchronously with the authoritative final server state.

Renderer failures never block script loading, game start, end-game reveal, or reset.

## Server configuration

By default the mod uses:

```
http://127.0.0.1:8767
```

Override it with either:

```
BOTS_RENDERER_URL=http://127.0.0.1:8767
```

or the JVM property:

```
-Dblood_on_the_sharktower.rendererUrl=http://127.0.0.1:8767
```

Set `BOTS_RENDERER_URL=off` to disable the integration.

If the renderer is configured with `RENDERER_TOKEN`, provide the matching token with:

```
BOTS_RENDERER_TOKEN=...
```

The token is sent as `X-Renderer-Token`.

## Final payload

The final render payload includes:

- script name and winning team
- Storyteller name(s)
- seating order
- Minecraft player names
- true role and role category
- final alignment separately from role category (so a good Imp / evil Empath renders correctly)
- alive/dead state
- Drunk/Marionette believed role
- Storyteller reminder text
- homebrew image URLs when present in script JSON

All requests use Java's asynchronous HTTP client and are fire-and-forget from the Minecraft server thread.
