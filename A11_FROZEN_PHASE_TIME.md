# A.11 Frozen phase time

Apply this patch after the current A.11 phase-presentation fixes.

Changes:
- Setup/default time is fixed at midday (6000).
- Day is fixed at midday (6000).
- Nominations are fixed at evening (13000).
- Night/Dusk is fixed at 18000.
- The active phase time is re-applied every server tick, preventing vanilla daylight progression from drifting the town lighting.

No phase messages, phase sounds, nomination logic, or night-order controls are changed.
