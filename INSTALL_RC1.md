# Blood on the Sharktower 1.1.0-rc1 — Installation

This is the A.12 release-candidate build for private Sharktower testing.

## Required platform

- Minecraft **26.3**
- Java **25**
- Fabric Loader **0.19.5** or newer compatible 0.19.x build
- Fabric API **0.160.6+26.3**
- Simple Voice Chat **2.6.23+26.3**
- Blood on the Sharktower **1.1.0-rc1**

## Client installation

Put these mods in the client's `mods` folder:

1. Fabric API for Minecraft 26.3.
2. Simple Voice Chat 2.6.23+26.3.
3. `blood-on-the-sharktower-1.1.0-rc1.jar`.

Use Java 25 to launch Minecraft.

## Server installation

Put the same three mods in the dedicated server's `mods` folder:

1. Fabric API for Minecraft 26.3.
2. Simple Voice Chat 2.6.23+26.3.
3. `blood-on-the-sharktower-1.1.0-rc1.jar`.

Run the server using Java 25.

The same Sharktower JAR is used on client and server.

## Updating from the development build

1. Stop the Minecraft server completely.
2. Back up the world and server configuration.
3. Remove the previous Blood on the Sharktower development JAR from the server `mods` folder.
4. Add `blood-on-the-sharktower-1.1.0-rc1.jar`.
5. Replace the old Sharktower JAR in every player's client `mods` folder with the same RC1 JAR.
6. Confirm Fabric API and Simple Voice Chat match the required Minecraft 26.3 versions above.
7. Start the server.
8. Join with the Storyteller and at least two normal clients.
9. Run a short smoke test before using RC1 for a full game.

Do not leave two different Blood on the Sharktower JARs in the same `mods` folder.

## Recommended smoke test

Before a real game:

1. Claim Storyteller.
2. Confirm players auto-seat.
3. Open the Grimoire.
4. Load a Base 3 script.
5. Fill/distribute the Role Bag and send roles.
6. Enter Night and verify Simple Voice Chat routing.
7. Return to Day.
8. Run one nomination and vote.
9. Verify the 3/2/1 countdown and world vote markers.
10. End/reset the test game.

If this passes, proceed to the full A.12 soak-test checklist in `A12_RELEASE_CANDIDATE_CHECKLIST.md`.

## Build provenance

RC1 is built by the repository's GitHub Actions workflow using:

- Ubuntu clean runner;
- Eclipse Temurin Java 25;
- the committed Gradle wrapper;
- `./gradlew clean build --stacktrace`.

The workflow uploads the remapped release JAR as a private test artifact for this project workflow.

## Distribution

This build is for private use under the permissions described in `PRIVATE_USE_NOTICE.md`. Do not publicly redistribute the JAR.
