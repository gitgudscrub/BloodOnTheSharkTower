# A.11 — Bottom Voice HUD + Game End Controls

This patch makes two UI follow-up changes:

## 1) Voice route HUD moved to bottom-centre

The Simple Voice Chat route indicator now renders as a small text line above the
hotbar instead of as a large top-of-screen panel.

That means it no longer overlaps the Storyteller's Night / phase HUD.

Supported labels:
- `NIGHT CHAT`
- `NIGHT CHAT - STORYTELLER`
- `PRIVATE CHAT`
- `PRIVATE CHAT - STORYTELLER`
- `PRIVATE HOLD - [J] TO LEAVE`
- `VOICE CHAT CONNECTING`
- `PRIVATE CHAT: Graveyard` (or any daytime private area name via `DAY_ZONE:<name>`)

Public Day Chat / proximity voice stay hidden, just like the small nominations
helper.

## 2) Game End button added to the Grimoire controls

A new `Game End` button is added on the right-hand Storyteller control column,
just below `Send Home`.

That opens the end-game control screen where the Storyteller can:
- declare `GOOD WINS`
- declare `EVIL WINS`
- view `Final Grimoire` after reveal starts
- `Reset for Next Game`
- `Cancel Reveal`
- use a new `Hard Reset` button at any time

`Hard Reset` sends the existing `reset_hard` Storyteller action.
