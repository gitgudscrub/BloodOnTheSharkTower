# Changelog

Versions follow `gradle.properties`. The live community release is **1.0.1**;
`1.0.2-alpha.*` builds are development builds that have not been distributed.

## 1.0.2-alpha.21 — Readable reminder tokens (unreleased)

- Grimoire reminders are now labelled chips: the source character's icon
  followed by the reminder text ("Poisoned", "Red Herring", "Drunk 3").
  Two reminders from the same character, such as the Washerwoman's
  "Townsfolk" and "Wrong", no longer look the same.
- A coloured edge shows the kind of effect: red for harmful (Poisoned,
  Drunk, Dead, Cursed), green for protective (Safe, Cannot Die) and
  parchment for everything else. The words say it too.
- Chips stack beside the role token on the side away from the centre. If
  that side is blocked they try the other side, then above the token, then
  below its name. Three rows fit; with more reminders the last row becomes
  "+N", which opens the editor. Icon squares are only used when nothing
  else fits.
- Hovering a chip shows the character and the reminder, for example
  "Poisoner: Poisoned".

## 1.0.2-alpha.20 — Easier to read, easier to run (unreleased)

- New Keys & Help screen (H, or the button in Settings and Storyteller
  Tools). It lists every Sharktower key with its current binding, so
  rebinds show up there, and gives a short "Right now" hint for the current
  phase. Players and the Storyteller get different hints.
- The role count strip now uses words as well as colours:
  "5 Townsfolk · 2 Outsiders · 1 Minion · 1 Demon". It switches to a
  short form on narrow screens.
- Grimoire tokens show the role name underneath, so you don't need to hover
  to read them. There is a new Role Names setting.
- Alignment no longer relies on colour alone. A "G" or "E" badge sits beside
  the blue or red ring. There is a new G/E Letters setting.
- Storyteller Tools:
  - Every button has a tooltip.
  - The phase button that comes next is marked in gold.
  - Full Reset, End Game and Restore Previous are red.
  - Full Reset needs a second click to confirm.
- The Grimoire Controls text wraps instead of running off small windows.

## 1.0.2-alpha.19 — More role automation (unreleased)

- Witch: when a Cursed player nominates, the Storyteller gets
  [Kill <player>] and [No effect] buttons. The prompt says if the Witch is
  drunk or poisoned, or if only 3 players are alive. The nomination still
  goes ahead. Console: `/bots automation witch <seat>|dismiss`.
- Execute (dies) now warns once when the player should survive: a sober
  Sailor, a Devil's Advocate "Survives Execution", an unspent Fool (whose
  ability is then spent), a Tea Lady "Cannot Die", or a good player while
  a sober Pacifist lives. Press Execute (lives), or Execute (dies) again to
  kill them anyway.
- Minstrel: executing a Minion adds "Everyone Is Drunk" to a sober Minstrel.
  Automation treats every other player (except Travellers) as drunk until
  the following dusk, when the reminder is removed.
- Courtier: "Drunk 3" counts down to "Drunk 2" and "Drunk 1" at each dusk,
  then is removed. The Storyteller is told each step.
- New "Game over?" prompts: a day with no execution while a sober Vortox
  lives, and executing the good twin while the Evil Twin lives. With no
  Demon alive but both twins alive, the Storyteller is told good cannot win
  yet, with no Good prompt.
- Mastermind: executing the Demon while a sober Mastermind lives gives one
  more day. An execution that day prompts that the executed player's team
  loses.
- The Godfather's "Died Today" now clears at dawn, after the Godfather has
  acted, instead of at dusk.

## 1.0.2-alpha.18 — Lighter house voice routing (unreleased)

- House voice routing runs every server tick for every player. When a
  player is already in the right voice group (the usual case at Night) it
  now stops there, instead of listing every Simple Voice Chat group and
  rebuilding the house group each tick. Routing decisions are unchanged.
- Players measure the distance to their own house only; the Storyteller
  still checks every house. House group ids are computed once.

## 1.0.2-alpha.17 — Nicknames (unreleased)

- New `/nick <name>` lets a player choose the name everyone sees: in chat,
  on their name tag, in the tab list, the seat sidebar, the Grimoire and
  every Sharktower message. `/nick clear` goes back to the username, and
  `/nick` shows the current one. Operators can remove anyone's nickname
  with `/nick reset <player>`.
- The real username is never hidden: the tab list shows
  "Nickname (username)", every change is announced to everyone, and
  permissions, commands and logs keep using the username.
- Nicknames are 2 to 16 characters of Latin letters (accents allowed),
  digits, spaces and `_ - ' .`. They cannot match another player's username
  or nickname (ignoring case, accents, spaces and punctuation), and cannot
  look like the Storyteller, the server, a game term or a "Seat 3" label.
  A player can change their nickname once a minute.
- Stored by UUID next to pronouns in
  `config/blood_on_the_sharktower/player-profiles.json`, which now also
  remembers each player's last username so offline players cannot be
  copied. If a new player joins with a username that matches someone's
  nickname, that nickname is removed.
- `/slay` and other name lookups accept a nickname as well as a username.

## 1.0.2-alpha.16 — More role automation (unreleased)

- **Imp star-pass:** when the Imp is Demon-Killed (killed themselves) and no
  sober Scarlet Woman catches it, the Storyteller gets one button per living
  Minion to choose the new Imp. Their role card and the Grimoire update.
  Fixed: this used to raise a "Good should win" prompt instead.
- **Reminder expiry:** reminders whose effect has run out are cleared and
  listed in Storyteller chat. At dusk: Poisoner "Poisoned", Innkeeper
  "Safe"/"Drunk", Sailor "Drunk", Minstrel "Everyone Is Drunk", Devil's
  Advocate "Survives Execution", Witch "Cursed", Godfather "Died Today".
  At dawn: Monk "Safe". Only reminders placed with that source role are
  touched; hand-written reminders stay.
- **Mayor:** the first Demon Kill on a sober Mayor each night is held back
  with a reminder that another player may die instead. Demon Kill the Mayor
  again to kill them.
- Night helper: the Empath and Chef answers note when a Recluse or Spy may
  register differently.
- New `/bots reminder role <seat> <role> <text>` (source-role reminder) and
  `/bots automation starpass <seat>`.

## 1.0.2-alpha.15 — Virgin needs Storyteller approval (unreleased)

- The Virgin no longer executes anyone by itself. Her first nomination
  still spends the ability, but the Storyteller now gets a recommendation
  ("Seat 2 is a Townsfolk, so they should be executed", "The Spy may
  register as a Townsfolk", a custom-role or drunk/poisoned note) with
  [Execute] and [No effect] buttons, and decides before starting the vote.
  Execute ends the nomination with no vote; No effect lets it continue.
  The same prompt covers a Townsfolk, the Spy and every other nominator.
- New `/bots automation virgin dismiss` twin for the No effect button.
  Buttons only work for the pending decision on the same day.

## 1.0.2-alpha.14 — Role automation (unreleased)

- The server now applies the rules that used to be bookkeeping for the
  Storyteller. The Storyteller keeps the final say: every automatic step is
  announced in the Storyteller chat and can be overridden.
  - **Virgin:** when a sober Virgin is nominated for the first time, a
    Townsfolk nominator is executed on the spot and the nomination ends with
    no vote. A non-Townsfolk nominator spends the ability with nothing
    happening; if the nominator is the Spy the Storyteller gets an
    "Execute the Spy now" button.
  - **Slayer:** new `/slay <player or seat>` command for any living player
    during the day. The claim is public; the target dies only if the shooter
    really is the unspent, sober Slayer and the target is the Demon.
    Otherwise "Nothing happens." and the Storyteller is told why.
  - **Scarlet Woman:** when the Demon dies with five or more players alive,
    a sober Scarlet Woman becomes that Demon: the Grimoire and their role
    card update and an "Is Demon" reminder is added.
  - **Protection:** Demon Kill refuses a target protected by a Safe/Protected
    reminder, a sober Soldier, a sober Sailor, a Tea Lady's "Cannot Die", or
    the Fool's first death (which spends the ability). Mark Dead still
    overrides.
  - **Win prompts:** when no Demon is alive, when two or fewer non-Travellers
    remain, when a sober Saint is executed, or when the day ends without an
    execution with three alive and a sober Mayor, the Storyteller gets a
    "Game over?" message with [Good wins] / [Evil wins] buttons. Nothing ends
    on its own.
- New Storyteller console twins for testing: `/bots markDead <seat>`,
  `/bots demonKill <seat>`, `/bots nominatePair <nominator> <nominee>` and
  `/bots automation virgin <seat>`.

## 1.0.2-alpha.13 — UI pass (unreleased)
- Every Sharktower screen now uses one themed button (dark panel, soft border,
  gold hover ring, muted when inactive, long labels trimmed with an ellipsis)
  in place of the vanilla stone button. Label colours follow one rule: white
  for navigation and toggles, gold for the action that advances the game,
  green to confirm or resolve, red for irreversible or evil-team actions.
- Script Builder and Role Catalog share a new team-grouped token grid: a
  coloured header per team, the ability text on hover, a search box that
  matches names and ability text, and mouse-wheel scrolling with a scrollbar
  instead of pages. The builder marks chosen roles with a gold ring and tick.
- The Storyteller night-order bar now names the selected entry under the
  icons with its position ("4/7  Fortune Teller · Alice, seat 3"), so the
  white ring no longer has to be decoded from the icon alone.
- Fixed: the vanilla Friends List (bound to O since 26.x) opened on top of
  Sharktower Settings. The first time the mod runs it unbinds that key if it
  is still on its default; rebind it in Controls if you want it back.
- Fixed: Storyteller Tools buttons ran into the footer on short windows.
- Fixed: HUD panels (role card, sidebar, timer) drew through open screens.
- Fixed: the Settings screen's hand-scale readout and rebind hint overlapped
  on short windows; the scale buttons now fit their labels.

## 1.0.2-alpha.12 — Pronouns (unreleased)

- New `/pronouns` command. `/pronouns they/them` (presets are suggested; custom
  text such as `xe/xem` is accepted) shows the pronouns in grey under the
  player's name tag, beside their name in the player sidebar when the row has
  room, and in the Grimoire hover text. `/pronouns clear` removes them. Text is
  limited to 24 letters, spaces, slashes, hyphens and apostrophes; game and
  server terms such as "demon" or "storyteller" are refused. Stored by UUID in
  `config/blood_on_the_sharktower/player-profiles.json`, so it survives world
  resets and restarts. Pronouns are cosmetic only and visible to everyone.
- Above-head layout restacked so nothing overlaps: name tag and pronoun line
  (hidden at Night as before), role token now centred at +1.25 instead of
  +0.72 (it used to cover the name tag), vote marker at +2.10 instead of +1.60.
- New client toggle **Pronoun Tags** in the Sharktower settings screen.
- New `pronounsRegression` runs with `gradlew check`.

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
- Dev harness: clients now connect with `--quickPlayMultiplayer` (Minecraft
  26.x ignored the old `--server`/`--port` flags and sat on the title screen),
  and the prepare task seeds each client's `options.txt` to skip the first-run
  welcome screen, mute sound and keep running without focus. Verified live:
  Setup claim by a non-operator, lock after Send Roles, non-Storyteller
  `/bots dusk` refused, operator override logged, claim allowed after the
  Final Grimoire reveal.
- Client pack adds Entity Culling, ImmediatelyFast, Dynamic FPS, and Mod Menu
  (with Text Placeholder API) alongside the existing Sodium, Lithium and
  FerriteCore. All are client-only; the server install is unchanged. The pack
  builder now rejects a mod whose required dependencies are not in the pack.
- Storyteller Grimoire clicks and player actions now send the game-state
  payloads only. The compressed script JSON, private notebooks, social state
  and the diagnostics probe are sent on join, reset and script load, where they
  can actually change. Previously every click re-sent all of them to every
  player.
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
