# A.11 — Speaking Sidebar 26.3 screen-accessor fix

Fixes the client compile error in `PlayerSidebarHUD.java`:

- old: `minecraft.screen`
- 26.3: `minecraft.gui.screen()`

This is a compile-only correction. The 12px heads, white speaking border,
seat ordering, vote-hand indicator and dynamically shrinking usernames are
unchanged.
