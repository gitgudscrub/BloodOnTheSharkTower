# Blood on the Sharktower 1.1.0-dev A8.4

## Local dev harness whitelist hotfix

The A.8 development server now explicitly writes:

- `white-list=false`
- `enforce-whitelist=false`
- an empty `run/dev-server/whitelist.json`

on every dev-server launch.

This prevents a vanilla whitelist setting from rejecting `TestPlayer1`–`TestPlayer5` while keeping the harness bound to `127.0.0.1` and `online-mode=false` for local-only testing.
