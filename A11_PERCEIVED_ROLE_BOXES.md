# A.11 — Perceived Role Boxes (Drunk / Marionette)

Adds an authoritative "believed role" layer for characters that do not know their true identity.

## Storyteller Grimoire

When the true role is **Drunk** or **Marionette**, the Storyteller sees a second, smaller role box beside the true token.

- Empty believed-role box: `?`
- Click the secondary box to choose the role the player thinks they are.
- Click `Clear Believed Role` in that chooser to clear it.
- The true role remains the normal 32px token and is never replaced in the Storyteller Grimoire.

The secondary token is positioned tangentially beside the true token so it does not cover the player head or outward seat number.

## Rules enforced

- **Drunk** may be shown a Townsfolk character.
- **Marionette** may be shown a good Townsfolk or Outsider character.
- `SEND ROLES` is blocked if a Drunk/Marionette has no valid believed role, preventing the true role from accidentally leaking to that player.

## Player-facing role

During the game, the affected player's normal role packet and personal Grimoire only contain the believed role. The client is never sent the true Drunk/Marionette identity through those player-facing paths.

When the end-game reveal is active, the true role is sent/revealed normally.

## Server / reset behavior

The server keeps true roles and believed roles as separate authoritative maps.

Believed roles:
- travel with the true character when roles are shuffled/swapped;
- are cleared when the true role changes to a character that does not use one;
- are included in the start-of-game match snapshot;
- are restored by Reset for Next Game / snapshot restore;
- are cleaned up when a setup player leaves or is unseated.

Demon-bluff randomization also treats selected believed roles as already-used role identities, avoiding accidental automatic reuse.
