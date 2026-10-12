# Script Reference: Night Order and Jinxes

The existing **Script Reference** screen now resolves both reference tabs from the current script.

- **Night Order:** First Night and Other Nights are separate columns. The script's `_meta.firstNight` and `_meta.otherNight` arrays take precedence when populated; otherwise the local official night-sheet order is filtered to characters actually on that script. Characters defined in a homebrew JSON object use their numeric `firstNight` / `otherNight` wake priorities (zero means not in that night's list). Includes Dusk, Dawn and the Minion/Demon information steps.
- **Jinxes:** Shows only pairs for which both characters are on the script. Homebrew character `jinxes` arrays override the official jinx wording for the same pair. The list de-duplicates pairs and also retains the script's Bootlegger rules, under a separate heading.
- Both views have Prev/Next pagination and refresh when a different script is loaded. They are available offline; no request is made to the official app during play.

## Bundled data and attribution

The reference files under `src/main/resources/data/blood_on_the_sharktower/reference/` are from The Pandemonium Institute's public **Blood on the Clocktower App releases** toolmaker resources:

- `night_order.json`: https://github.com/ThePandemoniumInstitute/botc-release/blob/main/resources/data/nightsheet.json
- `jinxes.json`: https://github.com/ThePandemoniumInstitute/botc-release/blob/main/resources/data/jinxes.json

These resources are publicly offered to toolmakers, subject to the [Community Created Content Policy](https://bloodontheclocktower.com/pages/community-created-content-policy). Blood on the Clocktower and its original game materials are the property of The Pandemonium Institute.

## Manual checks

1. Load bundled Trouble Brewing. In Night Order, First Night should show Chef, Investigator, etc., while Other Nights should show Imp, Undertaker, etc. Jinxes should report none.
2. Load Bad Moon Rising. First and Other Nights should differ, with the Demon actions only appearing on relevant nights.
3. Load a script containing **Butler + Organ Grinder** or **Heretic + Baron**. Confirm the one corresponding jinx and its full description appears.
4. Load a script with an explicit `_meta.firstNight` / `_meta.otherNight`. Confirm those orders win over the default.
5. Load a homebrew script with numeric first/other-night positions and a `jinxes` entry. Confirm the custom role appears in the night sheet and its custom jinx appears only if both roles are on the script.
6. Add enough characters and jinxes to require several pages. Verify navigation, text wrapping and script switching.

**Scope:** These tabs are a script *reference*, not an automation of the live Storyteller night-visit bar. The latter retains its separate existing behaviour.
