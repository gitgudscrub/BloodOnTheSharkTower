# Sharktower Live (development)

External, **listen-only** spectator companion to Blood on the Sharktower. This isolated Node.js proof of concept does not alter the Minecraft mod or expose the Grimoire.

## Running locally

1. Install Node.js 20 or newer.
2. Configure the environment values from `.env.example` in your shell or hosting platform (the app does not automatically load .env files).
3. In the Discord Developer Portal create an OAuth2 application, add the callback URL and use the same value for DISCORD_REDIRECT_URI.
4. Start using `npm start` inside this folder.
5. Visit http://localhost:3000.

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
