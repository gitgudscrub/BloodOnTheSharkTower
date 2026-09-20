# A.12 — Release Candidate Checklist

A.12 is a **stabilisation milestone**. The feature set is frozen unless a new change is required to fix an existing broken flow.

## 1. Repository and build

- [x] A.11 regression branch merged to `main`.
- [x] Current documentation updated for release-candidate status.
- [x] Historical patch/port notes archived out of the repository root.
- [x] Version changed to `1.1.0-rc1`.
- [ ] Clean checkout builds successfully with `gradlew.bat build`.
- [ ] Release JAR launches in a clean client instance.
- [ ] Release JAR launches on a clean dedicated server.

## 2. Full-game multiplayer soak test

Run at least one complete real-player game at a normal player count.

- [ ] Setup and automatic seating.
- [ ] Load Base 3/custom script.
- [ ] Fill/distribute Role Bag.
- [ ] Send roles.
- [ ] Dusk / first Night.
- [ ] Night-order visits and private ST chats.
- [ ] Dawn / Day Chat.
- [ ] Day private-chat rooms.
- [ ] Nominations from the Grim.
- [ ] Raised hands / vote markers / 3-2-1 countdown.
- [ ] Ghost vote.
- [ ] Execute — Dies.
- [ ] Execute — Lives.
- [ ] Later-night event/trigger flow.
- [ ] End Game.
- [ ] Final Grimoire.
- [ ] Reset for Next Game.

## 3. Reconnect and phase-transition testing

- [ ] Disconnect/reconnect during Setup.
- [ ] Disconnect/reconnect during Day.
- [ ] Disconnect/reconnect during Night.
- [ ] Disconnect/reconnect while in private ST chat.
- [ ] Dawn while a Night-origin private ST chat is active.
- [ ] Reconnect while dead.
- [ ] Reconnect during Final Grimoire reveal.

## 4. Voice chat

- [ ] Shared Night Chat is stable for a complete Night.
- [ ] Dead players remain in Night Chat.
- [ ] Storyteller private chats work during Day and Night.
- [ ] Sprinting through 1.5-block Day room entrances/exits routes correctly.
- [ ] No visible route/HUD bouncing.
- [ ] Reconnect restores the correct voice route.

## 5. Hidden-information boundary

Test with separate Storyteller and ordinary-player clients.

- [ ] Ordinary players cannot inspect unrevealed roles from their Grim.
- [ ] Ordinary players cannot receive Storyteller reminder state accidentally.
- [ ] Ordinary players cannot receive Demon bluffs unless entitled.
- [ ] World role icons only show locally-known information.
- [ ] Final reveal intentionally exposes all roles only after game end.
- [ ] Spy/Widow share happens only after Storyteller confirmation.
- [ ] Spy/Widow personal reminders survive a Storyteller share.
- [ ] Spy/Widow receive Demon bluffs.
- [ ] Droisoned Spy/Widow true-Grim sharing is blocked.
- [ ] Magician/Spy and Magician/Widow jinx blanks Magician + Demon character tokens only in the shared copy.

## 6. Grimoire and UI readability

- [ ] 7-player layout.
- [ ] 10-player layout.
- [ ] 15-player layout if practical.
- [ ] Role/reminder tokens do not overlap important controls.
- [ ] Death shroud is visible over both blue and red role tokens.
- [ ] Vote ticks/crosses are readable across the town square.
- [ ] Good/Evil thumbs markers are readable at reminder-token scale.
- [ ] Three-bluff multi-select is usable across multiple pages.

## 7. Packaging into the real pack

- [ ] Build release-candidate JAR.
- [ ] Replace the development JAR in the real client pack.
- [ ] Replace the development JAR on the real server.
- [ ] Confirm required Simple Voice Chat version.
- [ ] Start server from a clean reboot.
- [ ] Join with at least two normal client installs.
- [ ] Run a short smoke test before the next scheduled game.

## 8. Release criteria

1. No known crash or data-loss bug in the standard game loop.
2. No known hidden-information leak to ordinary players.
3. No known voice-routing bug that repeatedly moves players between groups.
4. Setup → game → end-game → reset works without manual recovery commands.
5. A real multiplayer game completes successfully on the packaged JAR.

When all release criteria are met, promote `1.1.0-rc1` to the private stable `1.1.0` build.

## After 1.1.0

Future gameplay automation, extra jinxes, deeper role-specific logic and presentation upgrades should become a new post-release milestone rather than extending A.12.
