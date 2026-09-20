# Blood on the Sharktower 1.1.0-dev A8.10

## Vote cancellation + seat-synchronised clock audio

- Added the Storyteller command `/bots cancelVote`.
  - Cancels an active clockwise vote immediately.
  - The current nomination remains active so the Storyteller can restart the vote.
  - Physical vote-seat locking ends with the cancelled vote.
- Reworked clock audio so it is driven by the authoritative seat-count event.
  - Removed the continuous/repeating 8-second `clock_ticking` playback from the vote loop.
  - Added a one-shot `clock_tick` sound derived from the existing clock asset.
  - Exactly one tick plays when each seat is locked/counted.
  - A 2-player vote therefore ticks twice; a 10-player vote ticks ten times.
- The vote-start sound remains separate from the per-seat ticks.
