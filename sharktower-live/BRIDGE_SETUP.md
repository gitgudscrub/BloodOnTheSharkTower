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
4. Set `token` to the exact value of the website's `BRIDGE_TOKEN`. The bridge starts automatically with Minecraft and posts a status update roughly every five seconds. A restart is needed after editing this configuration file.
5. Check Minecraft server logs for `Sharktower Live public-state bridge enabled`. Check `sudo journalctl -u sharktower-live -n 30 --no-pager` for website errors, then open the spectator website and sign in with Discord.

If AMP allows environment variables, these can be used instead of the properties file: `SHARKTOWER_LIVE_BRIDGE_ENABLED`, `SHARKTOWER_LIVE_BRIDGE_URL` and `SHARKTOWER_LIVE_BRIDGE_TOKEN`. The URL must use HTTPS (or HTTP to the same process' localhost) and end with `/api/bridge/state`.

## Privacy and operation

- The bridge is off unless explicitly enabled with a URL and token.
- Only Minecraft's server-side game state is transmitted, and the serializer explicitly constructs an allowlist. Storyteller-only chats and secret roles are never transmitted.
- Players whose deaths are still concealed are shown as alive, matching the normal public display.
- If Minecraft does not publish for 15 seconds, the website displays **No active game** instead of stale data.
- The existing Simple Voice Chat routing remains untouched. No voice packets are forwarded.
- **Do not publish your completed Minecraft server bridge configuration**. Keep it with the instance's private configuration and restrict access to its operator account.

## Testing the connection without changing Minecraft

A one-off smoke test can be run on the Ubuntu host using a temporary, non-secret sample payload and the token read locally from the private configuration. The bridge's POST endpoint requires a bearer token; requests without it return HTTP 403. It does not expose the Grimoire or require an OAuth session for server-to-server updates. Avoid printing the token in the terminal output or shell history.
