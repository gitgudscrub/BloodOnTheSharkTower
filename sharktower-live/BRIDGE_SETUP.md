# Connect the Minecraft server to Sharktower Live

This first milestone publishes **public game state only**: active seated players, publicly visible death states, current day/night and daytime conversations. Audio, Grimoire sharing and the Medium Traveller are **not** included.

## Website (Ubuntu host)

1. Update the website checkout and restart its systemd service:
   
   ```bash
   cd ~/sharktower-live-project
   git pull --ff-only origin feature/sharktower-live-foundation
   sudo systemctl restart sharktower-live
   sudo systemctl status sharktower-live --no-pager
   ```

2. Keep the website secret at `/etc/sharktower-live/sharktower-live.env`. The existing `BRIDGE_TOKEN` is reused; do not show or copy its value into Discord or GitHub.

## Minecraft server (AMP/Docker)

1. Use a Minecraft server build **from the same feature branch** including `SharktowerLiveBridge.java`. The website alone cannot collect Minecraft state until the Minecraft server JAR has the bridge code.
2. In your **Minecraft server instance's** config directory, create `sharktower-live-bridge.properties` using `minecraft-bridge.properties.example` as a template. Do **not** edit the GitHub file, and do not include your filled-in file in the modpack. The example contains no secrets.
3. Set `enabled=true` and use your actual Cloudflare HTTPS hostname (e.g. `https://spectator.YOUR_DOMAIN/api/bridge/state`). Docker containers generally cannot access the Ubuntu host via `127.0.0.1`, even when both are on one machine.
4. Set `token` to the exact value of the website's `BRIDGE_TOKEN`. The bridge starts automatically with Minecraft. It checks for public state changes approximately twice per second and sends changed state immediately; unchanged games send a keepalive snapshot every five seconds. A restart is needed after editing this configuration file.
5. Check Minecraft server logs for `Sharktower Live public-state bridge enabled`. Check `sudo journalctl -u sharktower-live -n 30 --no-pager` for website errors, then open the spectator website and sign in with Discord.

If AMP allows environment variables, these can be used instead of the properties file: `SHARKTOWER_LIVE_BRIDGE_ENABLED`, `SHARKTOWER_LIVE_BRIDGE_URL` and `SHARKTOWER_LIVE_BRIDGE_TOKEN`. The URL must use HTTPS (or HTTP to the same process' localhost) and end with `/api/bridge/state`.

## Privacy and operation

- The bridge is off unless explicitly enabled with a URL and token.
- Only Minecraft's server-side game state is transmitted, and the serializer explicitly constructs an allowlist. Storyteller-only chats and secret roles are never transmitted.
- Players whose deaths are still concealed are shown as alive, matching the normal public display.
- The website pushes changes to browsers immediately using authenticated Server-Sent Events (SSE), with browser reconnection and fallback polling. The spectator page does not need manual refreshing.
- If Minecraft does not publish for 15 seconds, the website pushes an offline state instead of showing stale data.
- The existing Simple Voice Chat routing remains untouched. No voice packets are forwarded.
- **Do not publish your completed Minecraft server bridge configuration**. Keep it with the instance's private configuration and restrict access to its operator account.

## Testing the connection without changing Minecraft

A one-off smoke test can be run on the Ubuntu host using a temporary, non-secret sample payload and the token read locally from the private configuration. The bridge's POST endpoint requires a bearer token; requests without it return HTTP 403. It does not expose the Grimoire or require an OAuth session for server-to-server updates. Avoid printing the token in the terminal output or shell history.

## Diagnosing an empty setup page

The Minecraft mod keeps setup seating in `StorytellerState.PENDING_SEAT_NUMBERS` until roles are sent. The bridge uses those pending seats during setup, then switches to the committed game seats. A seated, online player should therefore appear before game start, without exposing secret roles. On its first successful HTTP POST (or a successful reconnect), the Minecraft console prints `Sharktower Live bridge connected successfully (HTTP 200).` The website independently displays `Minecraft bridge connected` when an authenticated update arrived within 15 seconds; this is different from the browser's own `Live updates connected` SSE indication. Seeing `Minecraft bridge offline` means the backend has not received a recent POST, even if browser SSE is healthy.

## Experimental daytime voice for spectators

This feature is **opt-in** and intended for the initial single-player/room-switching test.

1. Update both the Minecraft server JAR and the Sharktower Live website from the same feature-branch build.
2. In the Minecraft instance's private \`config/sharktower-live-bridge.properties\` add **\`audioEnabled=true\`**, then restart the Minecraft server. Do not publish the bridge token. Server log will say \`Daytime spectator voice: ON\`.
3. Inform all players before starting a game that approved Discord members watching Sharktower Live can listen to daytime Town Square and private chat areas. This recording-free relay does not save audio.
4. Start a game and advance to day; in Chrome or Edge, sign in on the website, select **Listen: Town Square**, then speak in Minecraft while wearing headphones to avoid feedback. To test a private area, move in Minecraft and select the newly appearing room on the website.
5. If you want to disable the voice feed while retaining live player/room state, change to \`audioEnabled=false\` and restart Minecraft.

**Scope and limitations:** Uses Simple Voice Chat microphone Opus packets and a listen-only Server-Sent Events stream, decoded via browser WebCodecs; current Chrome or Edge is recommended. Other browsers may not support this codec path. There is no browser microphone uplink. Only seated non-Storyteller players speaking in the shared daytime group or configured daytime private zones are sent; ST private chats, houses, whispers, proximity/unrouted audio, night, and game-end audio are excluded. Rooms are determined by Minecraft's own voice routing, not by a spectator's position. Website access requires Discord OAuth with verified membership. Each listener selects one voice room at a time and audio is not persisted. A slow connection can cause delay or missing frames. This is a limited prototype rather than production-grade WebRTC/voice chat.

**Privacy reminder:** Server membership alone is not a substitute for telling players when their private chats may be heard by approved spectators. Keep the opt-in disabled until the group agrees to spectator listening.
