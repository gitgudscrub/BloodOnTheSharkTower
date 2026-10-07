# Game 1 playtest patch — 1.0.2-alpha.3

Tracks GitHub issue #8. Builds remain on the 1.0.2 draft PR until the multiplayer playtest passes.

## Implemented

- Script reference role grid, night order and special rules fit the available screen instead of silently dropping later entries.
- Grim/sidebar names use the character/team colour known to that client. This does not expose unknown roles.
- Empty world role slots, including cleared Drunk/Marionette believed roles, no longer draw placeholder boxes.
- Floating role and vote icons use interpolated positions rather than whole server-tick positions.
- Vanilla player nametags and below-name score text are hidden at Night.
- First-night Minion Info and Demon Info entries appear on the ST night bar; activating opens the corresponding visit list. Explicit script order positions take priority.
- Middle-click a role, believed role or bluff in the Grim to read its character details; Back returns to the same Grim.
- Live Grim widgets refresh in place after roles/reminders/seats change, without closing/replacing the screen or restarting its reveal.
- Death shrouds draw over portraits rather than character tokens, including the final reveal.
- Sidebar portraits sit on the right, with hand queue icons and queue positions beside them.
- The final Grim draws ST reminders with their character icons and shows labels when hovering that player/reminder.
- Minions get the same three-bluff display as Demons. A hidden Marionette does not get bluffs through its actual Minion role.
- Outside an active nomination/exile, U controls an independent general hand queue. Lowering removes you; re-raising goes to the back. Nomination YES/NO and ghost-vote rules remain separate.
- Every new nomination clears previous live/locked voting state and result presentation. Cancellation refunds the current nomination and makes its target eligible again; earlier ghost-vote history and other nomination budgets survive.
- Whispers are restricted to adjacent occupied seats, including the wraparound pair. ST senders/receivers are exempt. Dead players retain their seat for adjacency.
- Entering a daytime private area containing another player requires adjacency to that player; ST entry is exempt. Manual ST conversations remain available.
- During Night, approaching your own saved home within the existing eight-block house radius joins that house's isolated SVC group. ST entering the same home joins it; leaving restores shared Night Chat. Manual accepted ST private conversations take precedence until they end.
- The sidebar's speaking outline uses public speaking activity, independent of private-room audio. Its packet contains only player IDs and activity flags, with no audio or room membership.
- ST voice has no proximity attenuation. Setup/proximity speech reaches the ST through a separate static packet; shared/group voice already has no distance attenuation. Private-room boundaries still apply.
- Starting Night makes the ST visible by leaving Spectator while retaining flight. Explicit Spectator mode remains available through its existing toggle.
- B opens a multiline private notebook (8,192 characters). Saves are bound to the authenticated owner; no shareable inventory item exists. Notes persist across day/night, reconnects and server restarts, and clear on the normal full game reset. Old-game saves are rejected by reset generation.

## Verification

`gameOneRegression` covers attention ordering/separation, neighbour/private-area access, bluff visibility and cancellation/refund behaviour. The existing death visibility and reminder catalog suites still run from Gradle `check`. CI compiles common and client sources, packages the client, and boots the dedicated server.

## Multiplayer retest

1. ST + at least four seated players: raise general hands in order, lower/re-raise one, then start two nominations. Check queue positions, separate vote markers, fresh voting hands, and cancellation allowing the same target again.
2. Confirm Minion bluffs and that an unrevealed Marionette's Grim has none. Good players only see their own/noted character colours.
3. Open player Grims while ST adds/removes reminders. Screens stay open; ST widgets update. Middle-click true/believed/bluff tokens and return with Back. Clear world role notes and walk around to inspect smooth motion.
4. Try C at multiple window/GUI sizes. Every script group and order entry remains available. Check dead shrouds on portraits and final reminder icons/labels after Reveal Grim.
5. At Night, enter separate saved homes and verify audio isolation, automatic ST entry/exit, and talking outlines. Check nametags stay hidden and ST body is visible after Dusk.
6. Try whispered speech and private-area entry with adjacent, non-adjacent and wraparound seats, then ST. Confirm private content never reaches another room.
7. Write notebook notes, change phases and reconnect. Verify another player has their own blank/different notes. Full reset clears all notebooks, including one left open during reset.

Client rendering and live multi-client audio still require this retest; compilation/server startup do not establish those visual/audio outcomes.
