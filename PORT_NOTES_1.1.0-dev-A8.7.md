# A.8.7 - Dev Voice Onboarding Hotfix

- Pre-completes Simple Voice Chat onboarding in every development client run directory.
- Forces `disabled=false` so dummy clients can establish the UDP voice connection needed by Night Chat.
- Preserves any existing microphone, speaker, volume, keybind, and HUD preferences in each dev client.
- Makes every named dev client run task depend on the harness preparation task, so individual client launchers receive the same setup as the core launcher.
- Development harness only; production/default client configuration is unchanged.
