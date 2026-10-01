# Blood on the Sharktower — Modrinth releases

This repository can build a Modrinth `.mrpack` and, when explicitly requested, publish it to the private-group Modrinth project.

The important safety rule is that **publishing is manual-only**. Pull requests only build a dry-run pack. Merging development work never uploads a new player release automatically.

## Pack contents

The generated pack currently targets:

- Minecraft 26.3
- Fabric Loader 0.19.5
- Fabric API 0.161.0+26.3 (`bNnaTiuM`)
- Cloth Config API 26.3.158+fabric (`eZ3xIIuk`)
- Simple Voice Chat fabric-2.6.24+26.3 (`OLnMVWXy`)
- the Sharktower JAR built from the selected Git commit

Third-party mods are referenced from their pinned Modrinth CDN versions. The Sharktower JAR is embedded in `overrides/mods` inside the `.mrpack`.

Any future client config/resource files that should ship with every installation can be checked into `modrinth/overrides/` using their final Minecraft-instance paths, for example:

```text
modrinth/overrides/config/example.toml
modrinth/overrides/resourcepacks/example.zip
```

## One-time Modrinth setup

1. On Modrinth, create a **Modpack** project named `Blood on the Sharktower`.
2. Target Minecraft `26.3` and Fabric.
3. Keep the project private/draft while it is being configured and reviewed.
4. In the project's Moderation area, include the existing permission evidence for the original Blood on the Blocktower material.
5. Once approved, set the **project visibility to Unlisted** so it is not discoverable in normal browsing/search, but Discord members with the project link can install it.
6. Keep individual stable pack versions **Listed** inside that unlisted project. The workflow does this automatically.

Do not use a Modrinth `private` project for the normal player release unless every player has access to it through Modrinth. An unlisted project is the intended private-community distribution model here.

## GitHub Actions configuration

Create a Modrinth personal access token from your Modrinth account settings. The release workflow only needs to create versions, so grant `VERSION_CREATE`. If we later add automatic editing/archive operations, also grant `VERSION_WRITE`.

Never put the token in the repository or paste it into a chat/log.

In GitHub:

1. Open **Settings → Secrets and variables → Actions**.
2. Under **Secrets**, create:
   - `MODRINTH_TOKEN` = the Modrinth personal access token.
3. Under **Variables**, create:
   - `MODRINTH_PROJECT_ID` = the Modrinth project ID (the short base62 ID, not the full URL).

## Dry run

Before the first publish:

1. Open GitHub **Actions**.
2. Select **Build / Publish Modrinth Pack**.
3. Choose **Run workflow**.
4. Enter a release version such as `1.1.0`.
5. Leave **Publish after building** OFF.
6. Run it.
7. Download the resulting `blood-on-the-sharktower-<version>-mrpack` artifact.
8. Import that `.mrpack` into a fresh Modrinth instance and verify it launches and joins the server.

Pull requests that change the pack builder, pack manifest, or release workflow also perform this same dry-run build automatically using version `0.0.0-ci`.

## Publish a stable update

After the dry run has passed:

1. Open **Actions → Build / Publish Modrinth Pack → Run workflow** on `main`.
2. Enter the new player-facing modpack version.
3. Enter the changelog.
4. Choose `release` (or `beta`/`alpha` when intentionally testing with players).
5. Turn **Publish after building** ON.
6. Run the workflow.

The workflow will:

1. compile the mod on Java 25;
2. override the built JAR version with the player-facing release version;
3. fetch the pinned dependency metadata/hashes from Modrinth;
4. build and inspect the `.mrpack`;
5. keep a downloadable GitHub Actions artifact for 30 days;
6. upload the `.mrpack` as a new version of the configured Modrinth project.

## Player update flow

Players only need to make the switch once:

1. Give them the unlisted Modrinth project link.
2. They install Blood on the Sharktower from that project in the Modrinth App.
3. Future versions remain attached to the project, so they can use Modrinth's normal version/update controls rather than importing a new ZIP every release.

The dedicated AMP server is still updated separately; this workflow updates the client modpack distribution only.

## Version policy

The public-to-players pack version is independent from development milestone names.

Recommended convention:

- `1.0.x` — bug-fix-only releases for the original pack
- `1.1.0`, `1.2.0`, ... — player-facing feature releases
- `beta` / `alpha` Modrinth channels — opt-in group testing
- `1.x.x-dev-*` — GitHub/dev harness only; do not publish as the normal stable update
