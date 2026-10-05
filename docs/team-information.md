# ST-controlled team information — 1.0.2-alpha.4

The Night Grim has Minion Info and Demon Info buttons; the opening night bar opens the same screens. For ordinary setups, Preview uses committed server roles and seats, then Share Privately delivers exactly the reviewed messages to the eligible online players in chat. No ordinary client receives the ST preview or the actual role map. Sends are manual; nothing is sent automatically on Dusk.

- Minion Info: Demon candidates and fellow Minions, excluding the recipient and Marionette. This remains the normal automatic Preview/Share workflow when a Lunatic is on the script or in play.
- Demon Info: Minion candidates and the selected real Demon bluff names. Normally identifies Marionette separately.
- Lunatic: if Lunatic is on the current script, Demon Info is deliberately manual. The automatic Demon Preview/Share path is disabled both in the UI and on the server so the mod never guesses false Minion information. The manual screen gives the ST visit buttons for the real Demon, Minions and Lunatic. If a Lunatic is actually in play, the ST can also choose a separate set of three fake Lunatic bluffs and explicitly send those bluffs to the Lunatic as `[Demon Info]`. The Lunatic set is stored separately from the real Demon bluffs and is synchronized only to Storytellers; it is never included in ordinary Grimoire state sent to the real Demon, Minions or other players.
- Living Magician: add them to the Minions' Demon candidates and the Demon's Minion candidates without marking them as Magician; order all candidates by seat. Do not send them team info. Conceal Marionette identity while the Magician effect is active. Vizier disables Magician by default. The ST may disable the Magician effect for drunk/poisoned/no-ability rulings.
- Poppy Grower: withhold identities while it remains in the committed roles, including after death. Demon bluffs can still be shared. The ST explicitly chooses Allows Sharing after an eligible death; poisoned/drunk deaths must stay withheld. This is a ruling control, not an automated ability engine.
- Under seven non-Traveller players: refuse normal starting info; explicit ST override exists for special rules. Legion requires manual night visits. Other jinxes/registration/homebrew need ST judgment.
- Preview expires after two minutes, after changes to roles/seats/deaths/bluffs/night/day/reset generation, or after one send attempt. All recipients must be online or nothing is sent. Reset clears pending previews. Server verifies ST authority for preview and delivery.

## Live checks

1. Normal seven-player game without Lunatic on the script: preview both buttons, cancel and confirm separately. Confirm only intended recipients receive their message; another player and another ST see no message.
2. Add Magician: Minions see Demon + Magician; Demon sees Minions + Magician. No recipient sees a Magician label or the real role map. Magician receives nothing.
3. Add Marionette: they receive no Minion Info and fellow Minions never learn them. Demon identifies them normally, but not with active Magician. Toggle Magician off for a drunk/poisoned ruling; repeat with Vizier.
4. Poppy Grower: identities withheld, Demon bluffs available. After death, verify still withheld until ST allows sharing; keep withheld for a drunk/poisoned death.
5. Put Lunatic on the script: Minion Info should remain Preview/Share, while Demon Info should show the manual visit workflow and the server should reject forged automatic Demon previews. With Lunatic actually in play, choose three fake bluffs, verify they remain distinct from the real Demon bluff set, then Send Lunatic Bluffs and confirm only the Lunatic receives the `[Demon Info]` bluff message. Reopen the screen/reconnect the ST and confirm the three-bluff tracking state returns. Remove/reset the Lunatic and confirm the fake set clears.
6. Edit a role/bluff or change phase after preview; Share must reject and require a new preview. Reset must invalidate it. Disconnect a recipient; nothing should be partially delivered. Ordinary players forging either action must fail.
7. Check page navigation and manual visit buttons at normal GUI scales, including multiple Minions and long names.

Automated plan tests cover recipients, self exclusion, ordering, Marionette/Magician/Vizier/Poppy Grower, dead Magician, Lunatic privacy, Legion refusal, Traveller/small-game counting and alignment overrides. Live chat delivery/UI still require multiplayer testing.
