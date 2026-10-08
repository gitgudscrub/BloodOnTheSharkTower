# Sharktower Live (development)

External, **listen-only** spectator companion to Blood on the Sharktower. This isolated Node.js proof of concept does not alter the Minecraft mod or expose the Grimoire.

## Running locally

1. Install Node.js 20 or newer.
2. Create `/etc/sharktower-live` on the Linux host. Copy `.env.example` to `/etc/sharktower-live/sharktower-live.env` and fill in the required variables. Keep this file outside the repository and never commit it.
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

- Persisted user sessions and production deployment hardening (authenticated SSE already implemented).
- Fabric server event adapter for public game state.
- Controlled browser audio bridge (permissioned, no ST private information).
- ST-approved Grimoire access, exposure audit and Medium eligibility safeguards.

## Keeping secrets independent of GitHub

`start.js` looks for `/etc/sharktower-live/sharktower-live.env` by default. Create the folder using `sudo install -d -m 700 /etc/sharktower-live` and securely transfer your existing `.env` to `sharktower-live.env` (permissions 600). Limit file access to the server account. GitHub updates to the source tree do not replace this file. `.env` files and `node_modules` are ignored within `sharktower-live`, but never put real secrets in the repository.

### Linux server setup

Run Node.js 24+ on the Linux host. Keep secrets outside the Git checkout at `/etc/sharktower-live/sharktower-live.env`; restrict directory and file permissions to the user running the service. Use `node start.js` from the `sharktower-live` directory. For custom locations export `SHARKTOWER_ENV_FILE=/absolute/path/to/file` before launch. A systemd service can run this process independently alongside Minecraft; configure it after identifying your Linux distribution, account and checkout location. Do not copy secret contents into GitHub or shell history.

## Local-only HTTP listener

Sharktower Live binds only to `127.0.0.1:3000` by default. It is **not** accessible on the host's LAN/public interfaces. Run `cloudflared` on the same Linux host and route your Cloudflare hostname to `http://127.0.0.1:3000` (HTTPS is served by Cloudflare). After updating the source, restart the Sharktower Live process and check `ss -ltnp | grep ':3000'`. This restriction limits direct network access; Discord login and server-side authorization are still required.

## Near-real-time spectator updates

The Fabric publisher checks public game state about every 0.5 seconds while enabled and publishes changes promptly, with a five-second heartbeat when unchanged. The website then broadcasts each change to signed-in browsers over an authenticated Server-Sent Events stream at `/api/events`. Browsers reconnect automatically, with polling fallback when the stream is unavailable. The stream rechecks membership and session validity; hidden game information never enters the public-state payload. When Minecraft updates stop for 15 seconds the site shows the game as offline. Both the Minecraft server JAR and website code must be updated to enable the faster path. SSE connections must not be buffered by an intermediate proxy; the response sets `X-Accel-Buffering: no`.
