# Blood on the Sharktower 1.1.0-dev A9.1 — Exiled Traveller Voice Correction

## Fix

Exile now affects election participation only. An exiled Traveller is still a seated social participant and therefore:

- joins shared Night Chat at night,
- may receive Storyteller house/private-chat invitations,
- may request a private Storyteller conversation,
- returns to public Night Chat when private chat ends.

They remain excluded from later nomination/exile voting clocks and cannot raise a counted voting hand. Their `EXILED` marker remains separate from death/ghost-vote state.

## Regression test

1. Exile a Traveller.
2. Start Night with `/bots dusk`.
3. Confirm Storyteller, non-Traveller player, and the exiled Traveller all report `group=BOTS Night Chat` in `/bots voice status`.
4. Teleport the Storyteller to the exiled Traveller's configured house and confirm the normal private-chat invitation works.
5. Start a later election and confirm the exiled Traveller is still excluded from the physical voting clock.
