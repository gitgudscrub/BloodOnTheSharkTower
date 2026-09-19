# Blood on the Sharktower — 1.1.0-dev A.10

## End Game & Reset UX

A.10 separates **ending a game** from **resetting the map**.

### End Game
The Storyteller chooses GOOD or EVIL as the winner. This:
- sets the server to end-game reveal mode;
- stops shared/private Night Chat routing;
- stops the timer;
- closes active nomination/vote/exile presentation;
- preserves committed roles, deaths and exiled Travellers;
- broadcasts the final winner and reveal state to every connected client.

### Final Grimoire
While reveal mode is active, the normal Grimoire key opens a read-only Final Grimoire for all players. It shows:
- seat/player;
- real committed role;
- final good/evil alignment;
- dead status;
- exiled Traveller status.

The normal editable Storyteller Grimoire is not used for ordinary players during the reveal.

### Reset for Next Game
The Storyteller explicitly chooses Reset for Next Game when post-game discussion is finished. This calls the already-tested match snapshot restore path, returning both Sharktower state and the configured Overworld region to the clean start-of-game checkpoint.

### Commands
- `/bots endGame good`
- `/bots endGame evil`
- `/bots endGame cancel`
- `/bots resetForNextGame`

Legacy `/bots gameComplete` remains as the direct restore command.
