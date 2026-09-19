# Blood on the Sharktower 0.8.2 — BOTB 26.2 Baseline Freeze Candidate

0.8.2 is the final small polish pass before freezing the restored Blood on the Blocktower behaviour as the private Minecraft 26.2 baseline.

## Grimoire geometry polish

The player perimeter is now a slightly flattened ellipse rather than a perfect circle. Horizontal spacing is retained, while the top and bottom seats are pulled inward by roughly 36 px. This keeps seats 5–7 clear of the hotbar/lower controls and makes the player head/name markers sit closer to their role tokens.

The same geometry is used consistently for:
- role tokens
- player heads / synthetic seat markers
- seat-number labels
- reminder tokens

No gameplay/state behaviour changed in this release.

## Baseline freeze rule

If this build passes visual smoke testing at 10–15 seats, treat 0.8.2 as the private **BOTB-on-Minecraft-26.2 baseline**. Future releases should be Sharktower feature work rather than restoration work, except for regressions found against this baseline.
