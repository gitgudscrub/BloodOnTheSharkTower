# A.11 — Event-driven night order

This patch adds the original-mod style triggered night-order queue to the Storyteller HUD.

## Storyteller death controls

During a running game, click a player's token in the Grimoire:

- Day: **Mark Dead**
- Night: **Mark Dead** or **Demon Kill**
- Dead player: **Revive Player**

`Mark Dead` during Night counts as a night death. `Demon Kill` additionally marks the death as caused by the Demon so Demon-specific abilities can trigger.

Executions use the existing execution backend and automatically feed the trigger system.

## Triggered roles

The Storyteller night bar now creates event-driven visits for:

- Poppy Grower — any death; identifies Minion/Demon players for the reveal reminder.
- Hatter — any death; creates visits for the Minion/Demon players.
- Barber — any death; creates visit(s) for Demon player(s).
- Sweetheart — any death.
- Plague Doctor — any death.
- Farmer — death at Night.
- Ravenkeeper — death at Night.
- Sage — killed by the Demon.
- Banshee — killed by the Demon.
- Scarlet Woman — a Demon dies while the pre-death alive count is at least five non-Travellers.
- Choirboy — the Demon kills the King.
- Grandmother — the Demon kills the player carrying the **Grandchild** reminder.
- Undertaker — appears on the following Night when an execution occurred that Day, using the existing execution state.

Triggered icons have a small gold `!`. Selecting one and pressing the normal Night HUD activate key shows the role's existing night instruction. Roles whose original night-order entry uses seat teleport still use the normal house/private-chat visit flow.

The trigger queue is Storyteller-only over the network. Ordinary players receive an empty trigger snapshot, so it does not expose death causes or hidden role information.

## Grandmother setup

The reminder picker now includes **Grandchild** so the Storyteller can mark the Grandmother's grandchild in the Grimoire.

## Suggested smoke test

1. Start a game and enter Night 1.
2. Give one player Ravenkeeper, use **Mark Dead** at Night, and confirm a gold-`!` Ravenkeeper visit appears in the current Night order.
3. Revive that player and confirm the unresolved trigger disappears.
4. Give one player Sage, use ordinary **Mark Dead** at Night and confirm Sage does not trigger; revive, then use **Demon Kill** and confirm it does.
5. During Day, execute a player and confirm Undertaker appears only after Dusk begins the next Night.
6. Test Barber/Hatter with a Demon and Minion in play; the triggered role should target the affected evil seats rather than the dead Barber/Hatter seat.
7. Put **Grandchild** on a player, kill them with **Demon Kill**, and confirm Grandmother is queued.
8. Dawn -> next Day -> next Dusk, and confirm old resolved-night triggers do not leak into the later Night.
