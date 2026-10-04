# ST-controlled team information — 1.0.2-alpha.4

The Night Grim now has Minion Info and Demon Info buttons; the opening night bar opens the same screens. Preview uses committed server roles and seats, then Share Privately delivers exactly the reviewed messages to the eligible online players in chat. No ordinary client receives the ST preview or the actual role map. Sends are manual; nothing is sent automatically on Dusk.

- Minion Info: Demon candidates and fellow Minions, excluding the recipient and Marionette.
- Demon Info: Minion candidates and the selected bluff names. Normally identifies Marionette separately.
- Living Magician: add them to the Minions' Demon candidates and the Demon's Minion candidates without marking them as Magician; order all candidates by seat. Do not send them team info. Conceal Marionette identity while the Magician effect is active. Vizier disables Magician by default. The ST may disable the Magician effect for drunk/poisoned/no-ability rulings.
- Poppy Grower: withhold identities while it remains in the committed roles, including after death. Demon bluffs can still be shared. The ST explicitly chooses Allows Sharing after an eligible death; poisoned/drunk deaths must stay withheld. This is a ruling control, not an automated ability engine.
- Under seven non-Traveller players: refuse normal starting info; explicit ST override exists for special rules. Legion requires manual night visits. Lunatic is never sent the real team's information; use a manual visit for chosen false info. Other jinxes/registration/homebrew need ST judgment.
- Preview expires after two minutes, after changes to roles/seats/deaths/bluffs/night/day/reset generation, or after one send attempt. All recipients must be online or nothing is sent. Reset clears pending previews. Server verifies ST authority for preview and delivery.

## Live checks

1. Normal seven-player game: preview both buttons, cancel and confirm separately. Confirm only intended recipients receive their message; another player and another ST see no message.
2. Add Magician: Minions see Demon + Magician; Demon sees Minions + Magician. No recipient sees a Magician label or the real role map. Magician receives nothing.
3. Add Marionette: they receive no Minion Info and fellow Minions never learn them. Demon identifies them normally, but not with active Magician. Toggle Magician off for a drunk/poisoned ruling; repeat with Vizier.
4. Poppy Grower: identities withheld, Demon bluffs available. After death, verify still withheld until ST allows sharing; keep withheld for a drunk/poisoned death.
5. Edit a role/bluff or change phase after preview; Share must reject and require a new preview. Reset must invalidate it. Disconnect a recipient; nothing should be partially delivered. Ordinary players forging either action must fail.
6. Check page navigation and manual visit buttons at normal GUI scales, including multiple Minions and long names.

Automated plan tests cover recipients, self exclusion, ordering, Marionette/Magician/Vizier/Poppy Grower, dead Magician, Lunatic privacy, Legion refusal, Traveller/small-game counting and alignment overrides. Live chat delivery/UI still require multiplayer testing.
