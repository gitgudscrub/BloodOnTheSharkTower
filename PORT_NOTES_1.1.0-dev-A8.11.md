# Blood on the Sharktower 1.1.0-dev A8.11

## Vote-speed command hotfix

Adds the missing Storyteller command:

`/bots setVoteSpeed <seconds>`

- Accepts 0.50 to 3.00 seconds per seat.
- Updates the same server-authoritative vote timing used by the Storyteller Settings UI.
- Broadcasts the new timing to clients so the world clock animation stays synchronized.
- Refuses changes while a vote is already running.
- `/bots runVote` now reports the configured seconds-per-seat instead of always saying one second.
