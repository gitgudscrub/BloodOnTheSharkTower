# Changelog

Versions follow `gradle.properties`. The live community release is **1.0.1**;
`1.0.2-alpha.*` builds are development builds that have not been distributed.

## 1.0.2-alpha.11 — Storyteller lock and repository cleanup (unreleased)

- Storyteller control can be claimed by anyone during Setup and is locked from
  Send Roles until the Final Grimoire reveal. Server operators can override the
  lock (logged) to rescue a game whose Storyteller cannot return.
- No `/bots` command claims Storyteller control as a side effect any more. The
  41 commands that used to promote whoever ran them now require control, and 36
  state-changing commands that had no check (test seats/roles/deaths/phase,
  resets, nominations open/close, vote clock, executions, exile support, timer,
  night-chat routing, mass teleports) are Storyteller-only.
- New `storytellerClaimRegression` runs with `gradlew check`.
- One pack builder: `scripts/build_modrinth_pack.py` driven by
  `modrinth/pack-config.json`. The second builder, its separate performance-mod
  list and the one-off port rewrite script were removed. Both GitHub workflows
  now build the same `.mrpack`.
- Release-candidate era documents (`INSTALL_RC1.md`, the A.12 checklist, the
  A.13 design note) moved to `docs/history/release-candidate/`.
  `docs/PORT_STATUS.md` became `docs/STATUS.md` and was rewritten for the
  current baseline.

## 1.0.2-alpha.3 … alpha.10 — Live feedback (unreleased)

Per-alpha notes with their live-test checklists live under `docs/`:
`game-1-feedback.md` (alpha.3), `team-information.md` (alpha.4),
`house-voice.md` (alpha.5), `fresh-game-books.md` and `nomination-hands.md`
(alpha.6), `latest-feedback.md` (alpha.7 to alpha.10).

## 1.0.2-alpha.2 — Expanded live feedback (unreleased)

- Match bluff category colours, including custom roles.
- Package pinned Fabric 26.3 Sodium, Lithium and FerriteCore builds in both pack workflows.
- Release Storyteller control on Setup disconnect; grant scoped flight, spectator and `/tp <player>` controls without OP.
- Resolve private deaths immediately and publish them only through Reveal Deaths during Day; keep pending markers Storyteller-only.
- Clear all roles/perceived roles/personal snapshots on both resets and prevent checkpoint rollback from restoring them.
- Add death-visibility regressions and a loopback server startup check to CI.

## 1.0.2-alpha.1 — Session feedback and polish (unreleased)

- Refresh joining players' full state and everyone's seat/Grimoire/directory state at the end of the server tick. Disconnect cleanup also waits for the updated live player list.
- Fix Grimoire RMB Actions and Shift+RMB nomination shortcuts using Minecraft 26.3's SDL right-button constant.
- Add blue Good, red Evil and neutral grey borders to Grimoire role tokens. Storytellers see real current alignment; player borders use only their personal reads.
- Make personal Good/Evil reminders mutually exclusive and connect Read Good / Read Evil / Clear Read to the same notes. Removing the active reminder or clearing reminders restores a neutral border.
- Keep personal reads separate from server roles and Spy/Widow shares; clear the notebook when leaving a server.

## 1.0.1 — Live testing baseline

The current community testing release. Further feedback is collected for 1.0.2 before release.

## 1.0.0 — First playable Base 3 baseline

The staged-port systems below became the first numbered playable baseline.

## Before 1.0.0 — the `1.1.0-rc1` / A.12 release candidate

Before the project adopted release numbering, the final staged-port milestone was called `1.1.0-rc1` (A.12). It was renumbered **1.0.0** when it became the first playable Base 3 baseline; nothing was released under the `1.1.0` name. The notes below describe that milestone.

A.11 was feature-complete and became the baseline for release-candidate testing.

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
