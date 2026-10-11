# Wraith physical-night playtest (experimental)

This is a **development-branch implementation**. Do not merge to main before a real two-client / Simple Voice Chat test.

## Game setup

- Include Wraith on the selected script. The Storyteller's normal Up Arrow role visits change to **house navigation**, even if no Wraith is actually assigned.
- Configure every relevant seat home with `/bots setSeatHome <seat>`.
- Send roles. All connected seated players receive a **Soul Lantern** prop with a server-authoritative one-use-per-match charge.
- Press **L** with the Soul Lantern in either hand at the *start of a night* to light it. Once the Storyteller selects the first ordinary night visit, this night's decision locks. The lantern remains lit until Dawn and does not recharge.

## Storyteller night order

1. Dusk: all house voice privacy remains in place; lantern choices open.
2. **First Wraith icon**: Up teleports Storyteller *only* to the living Wraith's house and releases Wraith in Spectator Mode (if the ability is available).
3. All other role icons: Up instead chooses the next house. A **NIGHT VISIT / arrow / distance** HUD marker shows the direction. Storyteller flies to the house; house voice chat joins automatically by position.
4. When Storyteller actually reaches a selected evil player's house, an active Wraith teleports to that house and joins the house voice group.
5. **Last Wraith icon**: Up recalls Wraith to the configured house, switches them to Adventure Mode, and clears their voice override. It does **not** move the Storyteller.
6. Dawn automatically recalls Wraith if ST skipped the final icon. Reset/end-game cleanup also recalls.

## Wraith gameplay

- While released, Wraith can fly freely in Spectator Mode.
- The **WRAITH / arrow / Storyteller** compass targets the Storyteller's live server position.
- Within 20 blocks, the Storyteller is client-highlighted for Wraith only.
- Flying into a house silently does not itself join voice chat.
- Approaching a **door block** inside that configured house enables house voice routing (first playtest approximation of entering through the door).
- Players with a lit lantern can learn **anonymous presence** on Wraith entering a house. Only **one anonymous presence message across town each night**.
- A lit lantern always reveals Wraith's name when they join that owner's house voice room. Identity reveals have no nightly global cap.
- Wraith's lantern cannot be used to repeat detection after its once-per-game charge is spent.

## Must-test checklist

- [ ] Wraith on script but not in play: no standard night-order teleports; no identity leak
- [ ] Release only when alive and not blocked; recall before collidable Adventure Mode
- [ ] Flying through the wall of a house never enables voice
- [ ] Entering through different house door shapes enables voice; leaving removes it
- [ ] SVC player/group UI does NOT expose Wraith's identity before lantern detection (must verify; plugin may still expose participant names)
- [ ] Anonymous presence reaches only the first eligible house each night
- [ ] Identity reveals reach every lit house joined, but never an unlit one
- [ ] Lantern cannot be turned on after ST selects a visit, or more than once per game
- [ ] Evil-player visit teleports Wraith into voice, good-player visit does not
- [ ] Dawn, death, role loss, poison/drunk reminders, reset, disconnect/rejoin
- [ ] Storyteller command teleports continue to work for emergency recovery
- [ ] Two actual clients test the compass and client-only outline, including long range

## Known limitations before playtest

- The Soul Lantern currently uses the **L keybind while held** rather than right-click-on-item; its spent state is server-owned.
- The Storyteller's destination is a **HUD directional marker**, not yet a glowing world waypoint at the house entrance.
- Door detection checks nearby door blocks, not a predefined doorway crossing plane; proximity to a door inside a house can trigger voice.
- The stock Simple Voice Chat participant UI could disclose a Wraith in the voice group regardless of our detection messages. Test this before public use; if confirmed, an anonymous audio relay/voice roster filter is needed.
- Drunkenness/poisoning is checked using matching Storyteller reminders; indirect ability blocking needs more extensive coverage.
- No local Gradle build is possible from this tool environment without Maven/GitHub network access. Use the repository's PR build workflow and smoke test before merging.
