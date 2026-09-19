# 1.1.0-dev A8.8 — Simple Voice Chat group lookup hotfix

Fixes Night Chat routing against Simple Voice Chat 2.6.x.

## Fixed
- Avoids `VoicechatServerApi#getGroup(UUID)` when checking whether a BOTS voice group already exists.
- SVC 2.6.x can return a non-null `GroupImpl` wrapper whose internal group is null for a missing UUID; passing that wrapper to `VoicechatConnection#setGroup` throws a `NullPointerException`.
- Sharktower now searches `api.getGroups()` for genuinely registered groups before creating shared Night Chat or private rooms.
- Shared Night Chat creation remains persistent/hidden/isolated.
- Private chat creation remains temporary/hidden/isolated.

## Test
After restarting the dev harness:
1. `/bots voice status` — all three clients should report `connected=true`.
2. `/bots dusk`
3. `/bots voice status` — `managedConnections` should become 3 and each participant should show group `BOTS Night Chat`.
4. `/bots nightchat whoami` on all three clients.
