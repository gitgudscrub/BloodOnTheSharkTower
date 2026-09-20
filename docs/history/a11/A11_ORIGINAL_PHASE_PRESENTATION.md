# A.11 original phase presentation patch

This patch restores the original BOTB 1.3.0 phase presentation on top of the current Sharktower A.11 dev build.

## Behaviour
- Dusk sets world time to 18000, broadcasts `Night falls...` and `--- Night N ---`, and plays the original `dusk.ogg`.
- Dawn sets world time to 0, broadcasts `Dawn breaks!` and `--- Day N ---`, and plays the original `dawn.ogg`.
- Opening nominations sets world time to 13000, broadcasts `Nominations are open!`, and plays the original `call_back.ogg`.
- An accepted nomination also plays the original `nomination.ogg` sting.
- Phase sounds are sent directly to every connected player, so house distance does not mute them.

The existing Sharktower assets already contain these original sound files; no extra asset copy is required.
