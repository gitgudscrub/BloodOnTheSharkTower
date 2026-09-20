# A.11 phase presentation — Minecraft 26.3 clock compile fix 2

This follow-up removes the unavailable `ServerClockManager#getTotalTicks(...)` call.

The Fabric mappings used by the Sharktower 26.3 project expose `setTotalTicks(...)`, so the
phase presentation now sets the Overworld clock directly to the original BOTB presentation
points:

- Dawn: 0
- Nominations: 13000
- Dusk/Night: 18000

This intentionally behaves like the old `/time set` presentation. No phase messages, sounds,
nomination logic, night-order logic, or UI behaviour is changed.
