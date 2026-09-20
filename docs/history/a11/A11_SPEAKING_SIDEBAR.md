# A.11 — Speaking Sidebar + Dynamic Names

Updates the right-hand player sidebar without changing the head size.

## Speaking highlight
- Uses Simple Voice Chat's public client API `isTalking(UUID)`.
- While a player is currently speaking, their existing 12px head gets a 1px
  white outline around it.
- The outline is outside the portrait, so the head itself never resizes.
- Speaking state is local to the viewer, which naturally respects voice routing:
  a player whose audio is not reaching this client should not be highlighted.

## Username fitting
- Short usernames stay at normal Minecraft font scale.
- If a name would run into the edge / voting-hand column, the complete name is
  scaled down just enough to fit.
- Names are no longer replaced by `...`.
- Seat number, head size, row height and voting hand size are unchanged.

Changed files:
- `PlayerSidebarHUD.java`
- `VoicechatIntegrationState.java`
- `SharktowerVoicechatPlugin.java`
