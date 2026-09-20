# A.11 — Spy / Widow Storyteller-confirmed Grimoire Sharing

Spy and Widow now use the player's normal personal Grimoire instead of an
automatic separate read-only screen.

## Storyteller flow

When the Storyteller activates an eligible Spy/Widow night visit:

- the Grimoire is **not** shared automatically;
- the Storyteller receives a chat prompt with a clickable **[SHARE GRIMOIRE]** button;
- clicking the button re-checks the target's actual role, Night eligibility,
  death state and Droison state before any information is sent.

Spy is eligible on its normal Night visits. Widow is eligible for its Night 1
Grimoire view.

If the actual Spy/Widow is currently marked `Poisoned` or `Drunk`, true-Grim
sharing remains blocked and the Storyteller is warned to handle false
information manually.

## What the player receives

The share is sourced from the Storyteller's **current working Grimoire**:

- current Storyteller role assignments overwrite the player's local role guesses;
- current Storyteller reminder tokens are added as a separate shared layer;
- the player's own locally-created reminder tokens are preserved;
- refreshing/re-sharing replaces the previous Storyteller reminder layer without
  deleting the player's own notes;
- current Demon bluffs are shared too;
- the normal personal Grimoire opens immediately when the share arrives.

Shared reminders and personal reminders remain locally editable. **Clear All**
is an explicit player action and clears both local layers.

The server still does not expose this information through the ordinary public
Grimoire packet, so unrelated players receive no extra role/reminder/bluff data.


## Magician jinx

When a **Magician** is actually present in the Storyteller's current Grimoire,
the Spy/Widow share automatically applies the Magician jinx:

- the Magician's character token is blanked in the shared view;
- every in-play Demon character token is blanked in the shared view;
- the Storyteller's own Grimoire is not modified;
- seats, names, death state, reminder tokens and Demon bluffs are still shared;
- explicit blank role entries overwrite any local guesses the receiving
  Spy/Widow had for those players.

This applies to both Spy and eligible Widow Grimoire shares. If no Magician is
in play, the shared role map is unchanged.
