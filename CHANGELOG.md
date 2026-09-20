# Changelog

## 1.1.0-rc1 — A.12 Release Candidate

A.11 is feature-complete and becomes the baseline for release-candidate testing.

### Major completed systems

- Complete setup, seating, role distribution and Grimoire workflow.
- Base 3 and custom script support.
- Full Day nomination/voting/execution loop.
- Ghost votes and dead-player world/Grimoire presentation.
- Night-order Storyteller workflow and private voice routing.
- Persistent Day private-chat rooms.
- Source-role reminder tokens and Demon kill markers.
- Three-role Demon bluff multi-select with in-play filtering.
- Storyteller-confirmed Spy/Widow Grimoire sharing, including Demon bluffs and Magician jinx handling.
- End-game cinematic, Final Grimoire and Reset for Next Game.
- Local multiplayer Storyteller + dummy-player development harness.

### A.11 final regression fixes

- Dawn cleanly terminates Night-origin private ST chat before Day Chat routing.
- Sprinting through Day private-chat doorways no longer immediately bounces players back to Day Chat.
- Large green/blue/red world vote markers and visible 3/2/1 countdown.
- Execute — Dies and Execute — Lives split into explicit outcomes.
- Vote result clears after execution/no-execution.
- Dead players use a high-contrast Grimoire shroud.
- Grimoire portraits and role tokens share direct mouse interactions.
- Good/Evil reminders use thumbs-up/thumbs-down markers.
- Demon kill reminder selection reads actual in-play Demons from the Storyteller Grim.
- In-play and believed roles are excluded from Demon bluff choices.
- All three Demon bluffs can be selected in one multi-select flow.

## Historical milestones

Earlier staged-port notes are archived under `docs/history/port-notes/`. One-off patch/application instructions and A.11 implementation notes are preserved under `docs/history/`.
