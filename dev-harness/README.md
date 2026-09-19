# A.8 Local Multiplayer Development Harness

This harness runs several independent Minecraft development clients against one local-only Fabric server.

## Recommended quick test

Double-click `start-core-test.bat`.

1. A server terminal opens.
2. Wait for the server terminal to report `Done`.
3. Press any key in the launcher window.
4. Three clients launch and automatically connect:
   - `Storyteller`
   - `TestPlayer1`
   - `TestPlayer2`

The Storyteller account is automatically operator level 4.

## Extended test

`start-extended-test.bat` launches the Storyteller plus five dummy players. Use this for clock/vote-circle tests if the PC has enough RAM/CPU.

## Individual Gradle tasks

- `gradlew.bat runDevServer`
- `gradlew.bat runStoryteller`
- `gradlew.bat runPlayer1`
- `gradlew.bat runPlayer2`
- `gradlew.bat runPlayer3`
- `gradlew.bat runPlayer4`
- `gradlew.bat runPlayer5`

Every client has a separate directory under `run/`, so keybinds, Minecraft options, Simple Voice Chat state, screenshots and logs stay separate.

## Security

The test server deliberately uses `online-mode=false` so one Minecraft installation can create multiple development identities. It is also bound to `127.0.0.1`, so it is intended to be reachable only from this computer. Do not change `server-ip` to a public/LAN address while using this offline-mode configuration.

## Voice chat

Mute the microphones on dummy clients unless you explicitly need them. Several clients on the same speakers/microphone can create severe feedback.
