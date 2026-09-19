# A.11 — Droisoned Night Visit HUD

Small Storyteller night-visit popup follow-up.

The popup now combines the separate `Drunk` and `Poisoned` display lines into a
single mechanical status:

- `Droisoned: NO` — green
- `Droisoned: YES` — red

The underlying checks remain separate internally: a player is shown as
Droisoned if either their role/reminders mark them Drunk or they have the
Poisoned reminder. This is presentation-only and does not merge the underlying
sources mechanically.
