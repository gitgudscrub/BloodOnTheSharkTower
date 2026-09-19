# A.11 Storyteller Phase / Night-Order Bar patch

Apply this patch after the earlier A.11 Role Awareness patch.

Controls:
- Left Arrow: previous phase/night entry
- Right Arrow: next phase/night entry
- Up Arrow: activate selected entry
- N: show/hide the Storyteller phase bar

Expected test:
1. Use a small bag containing Chef plus another role that wakes every night (for example Empath).
2. Send roles. The Setup card should disappear and the Storyteller phase bar should appear with Dusk and the upcoming N1 wake order.
3. Press Up on Dusk to enter Night 1.
4. Select Chef and press Up. The Storyteller should teleport to the Chef's configured house and the Chef should receive the private-chat Join button.
5. Select Dawn and press Up.
6. On Day 1, the bar previews the upcoming N2 order. Chef should no longer appear; Empath should remain.
7. Use Dusk again to enter Night 2 and confirm the same order persists.

Scope note: regular scheduled first/other-night visits are automated. Triggered/death-only wakes such as Ravenkeeper/Barber/Sage remain manual until their trigger events are explicitly represented in synchronized state.
