# Auto-updating install (Prism Launcher)

Players install once and never update by hand again: every time they press
**Launch**, the pack checks for a new version first and downloads only what
changed. Works the same on Windows and Ubuntu (and macOS).

How it works: [Prism Launcher](https://prismlauncher.org) runs
[packwiz-installer](https://github.com/packwiz/packwiz-installer) as the
instance's pre-launch command. It reads `pack.toml` from the `pack` branch of
this repo, installs or removes mods to match, and keeps the instance's
Minecraft and Fabric versions in line with the pack. Each release's
Sharktower JAR is a GitHub release asset (`pack-<version>`).

## For players

1. **Install Prism Launcher** from https://prismlauncher.org/download
   - Windows: the installer (or the portable zip).
   - Ubuntu: the Flatpak is easiest:
     `flatpak install flathub org.prismlauncher.PrismLauncher`
2. Open Prism, click **Accounts** (top right) and sign in with your Microsoft
   account.
3. **Download the Sharktower instance:**
   https://github.com/gitgudscrub/BloodOnTheSharkTower/releases/latest/download/Blood-on-the-Sharktower-Prism.zip
4. In Prism: **Add Instance > Import**, pick the zip, press **OK**.
5. Select **Blood on the Sharktower** and press **Launch**. If Prism offers to
   download Java, say yes. The first launch shows a small window while the
   mods download (about a minute). The Sharktower server is already in the
   Multiplayer list.

After that, just press **Launch**. Updates install themselves before the game
starts. If an update moves to a new Minecraft version, a box asks to update
the instance: choose **Update**, then press **Launch** again if asked.

Your own changes are kept: your server list, settings and any config files
you have edited are not overwritten by updates.

**"Your pack is out of date" when joining?** Close Minecraft and press
Launch again. **"The server has not updated yet"?** You are ahead of the
server; try again in a few minutes.

Players who prefer the Modrinth App can keep using the `.mrpack`; they just
need to press its update button when a new version comes out.

## For the server admin

### Publishing a release

Run the **Build / Publish Modrinth Pack** workflow (Actions tab) with:

- `version`: the new version (for example `1.0.2-alpha.48`)
- `publish_auto_update`: ticked
- `publish_to_modrinth`: ticked as well if Modrinth should get it too

The workflow builds the JAR, creates the `pack-<version>` GitHub release
(server JAR + Prism instance zip) and then points the `pack` branch at it.
Leaving `publish_auto_update` off is a dry run: everything is built and
uploaded as workflow artifacts, nothing is published.

Then **put the same JAR on the server** (download it from the release) and
restart it. Players are only let in when their version matches the server's,
so update the server straight after publishing.

GitHub can cache `pack.toml` for up to about five minutes, so a player who
launches within minutes of the publish may get the update on the next launch.

### Version check at login

The server refuses a player whose Sharktower version differs from its own,
with a message telling them what to do. It runs before the player enters the
world, so a refused player never takes a seat. Clients from before the check
(1.0.2-alpha.47 and older) or without the mod are refused with the "missing"
message.

To turn it off (for example while testing mixed builds), start the server
with `-Dsharktower.versionCheck=false`.

### Building the pack locally

```sh
python scripts/build_modrinth_pack.py --version 1.0.2-alpha.48 \
  --jar build/libs/blood-on-the-sharktower-1.0.2-alpha.48.jar \
  --output dist/pack.mrpack \
  --packwiz-out dist/packwiz --jar-url <JAR download URL> \
  --prism-out dist/Blood-on-the-Sharktower-Prism.zip \
  --pack-url <pack.toml URL> --bootstrap packwiz-installer-bootstrap.jar
```

To test without publishing, serve `dist/packwiz` (with the JAR beside it)
from `python -m http.server`, point both URLs at it, and run
`java -jar packwiz-installer-bootstrap.jar -g <pack.toml URL>` in an empty
folder: it installs the pack there exactly as Prism would.
