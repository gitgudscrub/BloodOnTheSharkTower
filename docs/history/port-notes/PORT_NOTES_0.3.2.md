# Blood on the Sharktower 0.3.2 - Player Role Synchronisation

This milestone restores the next original Blood on the Blocktower networking slice:

- `PendingRoleAssignment` packet codec
- `SendRoleS2CPayload`
- client `myRole`, `myAssignment`, `myAlignment`
- active player / traveler counts
- per-player role delivery during current-state sync
- temporary role acknowledgement diagnostics
- `/sharktower testrole <roleId>`

The wire shape follows the original mod closely. Custom assignments send only the custom role id plus alignment override, then resolve that id against the already-synchronised Script on the client.

Presentation side effects from the original `ClientState.updatePlayerState` (role HUD visibility, sounds and assignment animation) are intentionally deferred until the UI/HUD port.

## Test

1. `./gradlew.bat runClient`
2. Join a world.
3. `/sharktower testscript`
4. `/sharktower testrole empath`
5. `/sharktower`
6. Repeat with `/sharktower testrole imp`
7. `/sharktower`

Expected role parity:

- Empath: server/client `Empath (good)` and `Role sync: ONLINE`
- Imp: server/client `Imp (evil)` and `Role sync: ONLINE`

You can clear the test assignment with `/sharktower testrole clear`.
