# A.11 — Spy / Widow True Grimoire Sharing

Adds a safe, read-only true-Grimoire view for official roles whose ability lets
that player see the Grimoire.

## Behaviour

- When the Storyteller activates a **Spy** night visit, a sober/healthy true Spy
  automatically receives the committed true Grimoire.
- When the Storyteller activates a **Widow** visit on Night 1, a sober/healthy
  true Widow receives the same view.
- The snapshot opens automatically as a read-only circular Grimoire with:
  - true role tokens
  - seat order
  - player names / heads
  - death state
  - current reminder tokens
- Demon bluffs and Sharktower-only Drunk/Marionette perceived-role helper boxes
  are not included, because those are not part of the physical true Grimoire.
- The player's personal deduction Grimoire is never overwritten.
- Closing the screen discards the snapshot. Re-activating the Spy/Widow night
  visit sends it again.
- The screen closes automatically at Dawn / outside Night.

## Droison safety

If the true Spy/Widow currently has a **Poisoned** or **Drunk** reminder, the mod
will **not** automatically send the true Grimoire. The Storyteller receives a
warning in their action result instead. This avoids accidentally giving correct
information when the role is droisoned.

A Drunk/Marionette who merely *believes* they are Spy/Widow is also never sent
this true snapshot, because eligibility is checked against the server's actual
role assignment.
