# A.11 — Day Private Chat HUD

Adds a small local-only status line in the same bottom-centre HUD slot used by
`NOMINATIONS OPEN`.

During Day before nominations:
- Public Day Chat: no extra label.
- Inside an automatic private-chat area: `PRIVATE CHAT: Graveyard` (green).
- Leaving through that area's exit immediately clears the label when the player
  is routed back to Day Chat.

When nominations open, private areas are disabled as before and the same slot
shows `NOMINATIONS OPEN`.

This reuses the existing local VoiceRoute packet. The server sends only the
current player's own area name; it does not reveal where other players are.
