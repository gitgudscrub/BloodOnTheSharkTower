# A.11 — Smaller Perceived Role Token + Empty-Slot Scaling Fix

This combines the empty-slot scaling hotfix with a clearer Drunk / Marionette visual hierarchy.

- True role token remains **32px** in the Grimoire.
- Believed/perceived role token is reduced from **28px to 20px**.
- The true + perceived pair remains centred around the player's radial position.
- Floating world role icons use the same visual hierarchy: the true role stays full size while the perceived role is noticeably smaller.
- Includes the corrected empty-slot texture scaling, so the nested-border rendering bug is fixed too.

No role logic or hidden-information behaviour changes.
