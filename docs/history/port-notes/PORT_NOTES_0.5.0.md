# 0.5.0 — Fast-track game flow

This batch ports the original BOTB daytime architecture at subsystem scale.
The goal is to restore the authoritative rules/state first; the old physical
lever/piston vote presentation and HUD rendering will be connected in 0.6.x.

## Ported architecture

### Daytime state

`DaytimeState` now tracks the original families of state:

- who can nominate / be nominated
- nominations remaining
- ghost votes
- current nominator / nominee
- marked-for-execution player + vote count
- storyteller marked-for-execution fields
- nomination open/closed state
- current/locked votes and lever-state model
- Banshee double-vote state
- Voudon state
- Bureaucrat/Thief vote multipliers
- Traveler exile eligibility
- exile caller/target/support state

### Election/game-flow classes

- `ElectionType`
- `ElectionConfig`
- `ElectionState`
- `ElectionManager` logical coordinator
- `NominationManager`
- `VotingManager`
- `ExecutionManager`
- `ExileManager`
- `ExileSupportManager`

### Networking

- `SyncDaytimeStateS2CPayload`
- `VoteStateUpdateS2CPayload`
- `TimerManager`, `ClientTimerState`, `TimerStateS2CPayload`
- initial join/current-state sync includes daytime/vote/timer state
- client state now reconstructs `GamePhase` from synced daytime state
- the existing diagnostic probe is sent after the new state packets, so
  `/bots status` can verify server/client phase parity

## BOTS commands in this batch

```text
/bots nominations open
/bots nominations close
/bots nominate <seat>
/bots runVote
/bots vote <true|false>
/bots resolveVote
/bots resetVote
/bots execute
/bots executionFail
/bots callForExile <seat>
/bots runExileSupport
/bots exileVote <true|false>
/bots resolveExile
/bots resetExile
/bots gameflowdemo
/bots timer start <seconds>
/bots timer pause
/bots timer resume
/bots timer stop
```

The `vote` and `exileVote` commands are temporary test/input surfaces. The
original mod used physical world controls for much of this interaction; 0.6.x
will reconnect the visual/world interaction layer, and the later Sharktower
voting overhaul can then replace it cleanly.

## Fast smoke test

`/bots gameflowdemo` performs a one-player end-to-end backend test:

1. seat player at 1 if needed
2. mark player alive
3. move to day
4. open nominations
5. self-nominate
6. start vote
7. cast/lock YES
8. resolve vote
9. sync entire server state to client

It should leave the player marked for execution. Run `/bots execute` to test
execution/death-state propagation.

## Deliberately deferred

These are presentation/integration layers, not missing authoritative state:

- rotating timed vote clock
- physical lever reads
- seat pistons / indicator blocks
- nomination glow/team effects
- vote/result sounds and titles
- Election HUD / Exile HUD
- nomination highlighting UI
- Storyteller daytime GUI controls
- Simple Voice Chat hooks

## Next bulk batch

0.6.0 should be the large client/UI restoration: grimoire screen, setup/role
assignment screens, HUDs, role display, script UI, and the daytime election
presentation hooks required to make the restored 0.5.0 backend visible and
interactive without diagnostic commands.
