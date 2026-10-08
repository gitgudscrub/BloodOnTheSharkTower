# Sharktower Live (development)

External, **listen-only** spectator companion to Blood on the Sharktower. This isolated Node.js proof of concept does not alter the Minecraft mod or expose the Grimoire.

## Running locally

1. Install Node.js 20 or newer.
2. Create `C:\\Sharktower\\config` on the server PC. Copy `.env.example` to `C:\\Sharktower\\config\\sharktower-live.env` and fill in the required variables. Keep this file outside the repository and never commit it.
3. In the Discord Developer Portal create an OAuth2 application, add the callback URL and use the same value for DISCORD_REDIRECT_URI.
4. Start using `node start.js` or `npm.cmd start` inside this folder. The launcher automatically reads the external configuration. Override the path with the `SHARKTOWER_ENV_FILE` environment variable if desired.
5. Visit http://localhost:3000. The legacy command `node --env-file=.env server.js` continues to work, but bypasses the external configuration loader.

Users sign in through Discord OAuth2. The backend checks membership of the configured Discord guild with the `guilds.members.read` scope and denies access if not a member. Sessions are stored **in memory** and are lost on restart; do not use multiple replicas. The app requires a nontrivial SESSION_SECRET and uses HTTP-only cookies. Run behind HTTPS and set COOKIE_SECURE=1 in production. Set request rate limits at the reverse proxy. Never expose client secrets or bridge tokens to browsers.

## Live bridge contract (initial)

A future authenticated Minecraft server-side bridge can POST JSON to `/api/bridge/state` with header `Authorization: Bearer <BRIDGE_TOKEN>`.

Example:

```json
{"gameId":"demo","phase":"day","day":1,"players":[{"id":"uuid","name":"Example","alive":true,"chatGroup":null}],"conversations":[]}
```

Public spectator responses intentionally expose only names, living status, phase and public conversations. **Do not send roles, alignment, reminders, Demon bluffs, or hidden Storyteller interactions to this endpoint.** Live audio is NOT implemented. The server-side voice bridge must be investigated separately and opt-in to spectator listening made clear to all game participants.

## Future work

- WebSocket/SSE updates, persistence and deployment.
- Fabric server event adapter for public game state.
- Controlled browser audio bridge (permissioned, no ST private information).
- ST-approved Grimoire access, exposure audit and Medium eligibility safeguards.

## Keeping secrets independent of GitHub

`start.js` looks for `C:\\Sharktower\\config\\sharktower-live.env` by default. Create the folder with `New-Item -ItemType Directory -Force C:\\Sharktower\\config` in PowerShell, then copy your existing `.env` to the new name there. Limit file access to the server account. GitHub updates to the source tree do not replace this file. `.env` files and `node_modules` are ignored within `sharktower-live`, but never put real secrets in the repository.
