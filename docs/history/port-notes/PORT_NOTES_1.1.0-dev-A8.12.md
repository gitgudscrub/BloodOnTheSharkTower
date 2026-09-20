# Blood on the Sharktower 1.1.0-dev A8.12 — Snapshot World Restore Hotfix

## Fixed

- Snapshot corner selection is now treated as a horizontal map-area selection.
- The captured region automatically includes 16 blocks below and 48 blocks above the selected corner Y levels.
- This fixes the common case where both corners were selected while standing on the ground, which previously captured only a one-block-high air layer.
- World restore now explicitly clears each snapshot tile immediately before placing the saved structure tile back.
- Blocks placed after the snapshot are therefore removed as well as blocks destroyed after the snapshot being restored.

## Retest

After installing this hotfix, restart the dev server, set both snapshot corners again, and run Send Roles to create a new checkpoint. Old A8.11 snapshots keep their old thin region and should not be used for this test.
