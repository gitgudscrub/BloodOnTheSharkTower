# 0.6.1 — Original UI Parity

This milestone replaces the temporary 0.6.0 presentation style with a direct visual port of the original Blood on the Blocktower UI conventions.

## What changed

- Restored the original circular Grimoire geometry:
  - 32px role tokens on the outer ring
  - 24px player/seat markers on the inner ring
  - original radius/padding values and top-first circular placement
  - reminder pips, seat numbers, death marking and demon-bluff footer
- Restored the Role Catalog geometry:
  - 375px logical content width
  - five roles per row
  - 75px cells / 70px rows
  - 40px team-coloured token backplates with 38px role art
  - Main/Extra role split and hover ability text
- Fixed role artwork rendering on Minecraft 26.2 by sampling the entire role texture instead of only the upper-left region.
- Restored the tabbed Script Reference structure (Roles / Night Order / Jinxes) with BOTB-style role-token grids.
- Reworked Character Details around BOTB's large top token + centered text hierarchy.
- Reworked Storyteller Tools into the original compact category/button visual language.
- Reworked the Timer screen around the original two-row preset layout.
- Moved the role HUD toward BOTB's original x=10/y=50 full-role-box presentation.
- Replaced the permanent 0.6.0 top-right diagnostic HUD with a contextual centered Setup HUD.

## Intentional limits

This is a UI-parity milestone, not the final integration milestone. Player skin heads, fully interactive Grimoire drag/drop, reminder editing, timer C2S controls, full Storyteller tool actions, physical voting/world presentation, sounds, mixins, and Simple Voice Chat remain for 0.7.0.

The timer preset buttons currently display the corresponding `/bots timer ...` command rather than sending the control packet directly; this avoids inventing a second control path before the original TimerControl packet is restored.

## Private use

The original BOTB code/assets are included under the user's private-use permission. Do not publicly distribute this source tree, assets, or compiled builds.
