# A.11 — Night Visit Info Automation, Part 2

Expands the Storyteller night-visit popup using the same reminder-driven model
as the original Blood on the Blocktower mod.

## Newly supported visit information

- Washerwoman — source-role `Townsfolk` + `Wrong` markers identify the two players and the real Townsfolk character.
- Librarian — source-role `Outsider` + `Wrong` markers identify the two players and the real Outsider; automatically reports no Outsiders when appropriate.
- Investigator — source-role `Minion` + `Wrong` markers identify the two players and the real Minion character.
- Steward — `Steward: Know` identifies the good player.
- Knight — two `Knight: Know` markers identify the two non-Demon players.
- Noble — three `Noble: Know` markers identify the three shown players and validates that exactly one is truly evil.
- Undertaker — automatically reports the character that actually died by execution that day.
- Flowergirl — automatically reports whether a Demon cast a locked YES vote that day.
- Town Crier — automatically reports whether a Minion nominated that day.
- Godfather — automatically lists the Outsider characters currently in play.

Existing Chef, Empath, Fortune Teller, Grandmother, Clockmaker, Shugenja,
Oracle and triggered-visit information remains intact.

## Original-style role-specific reminder markers

The ordinary Reminder screen now has a `Night Info Markers` button for the
Storyteller. It opens a compact picker containing only supported information
roles currently in play.

These reminders retain their source role internally, so `Steward: Know`,
`Knight: Know` and `Noble: Know` do not conflict with one another even when
several of those characters are in the same game.

The Remove buttons now show the source role as well, e.g. `Knight — Know`.

## Daily tracking

Three Storyteller-only facts are synchronized separately from the public game
state:
- role that died by execution today
- whether a Demon voted today
- whether a Minion nominated today

They reset at Dawn / Setup / fresh-game reset and are never sent as hidden
information to ordinary player clients.

Undertaker now only appears in the night order when a player actually died by
execution. An execution that was survived no longer wakes the Undertaker merely
because `executionToday` was set.
