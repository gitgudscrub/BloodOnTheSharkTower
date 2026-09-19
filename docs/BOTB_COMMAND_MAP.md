# BOTB -> BOTS command namespace

Blood on the Sharktower uses `/bots` as the command root.

The original private BOTB 1.3.0 command root was `/botb`. During the bulk port,
command behavior should be preserved but moved under `/bots`.

Examples:

- `/botb setup` -> `/bots setup`
- `/botb setup help` -> `/bots setup help`
- `/botb setup skip` -> `/bots setup skip`
- `/botb setup back` -> `/bots setup back`
- `/botb setup finish` -> `/bots setup finish`
- `/botb setSeatHome ...` -> `/bots setSeatHome ...`
- `/botb teleportToSeat ...` -> `/bots teleportToSeat ...`
- `/botb setTownSquare ...` -> `/bots setTownSquare ...`
- `/botb setDuskCommand ...` -> `/bots setDuskCommand ...`
- `/botb setDawnCommand ...` -> `/bots setDawnCommand ...`
- `/botb setExecutionPosition ...` -> `/bots setExecutionPosition ...`
- `/botb resetGame` -> `/bots resetGame`
- `/botb resetGameHard` -> `/bots resetGameHard`

0.4.0 currently registers the `/bots` root and its port diagnostics. The
remaining original subcommands will be attached directly to this root as their
handlers are bulk-ported; they will not be recreated under a second command
namespace.
