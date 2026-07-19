# Timers and presence

The server is authoritative for formation deadlines, play clocks, reconnect grace, cumulative ranked disconnect time and terminal ordering. Browser countdowns are estimates rendered from server values.

## Formation timer

The five-minute formation deadline begins when Player 2 joins and both seats are occupied. At expiry:

- exactly one locked valid formation wins by `SETUP_TIMEOUT`;
- neither locked formation produces `NO_CONTEST`;
- both locked formations have already started normal play, so no setup deadline remains.

The deadline is stored in the authoritative match snapshot and evaluated under the same per-match lock used for commands.

## Play clock

`STANDARD_15_PLUS_5` gives each side 15 minutes. Only the current side's stored time is reduced. A legal command received strictly before its deadline is resolved before timeout; elapsed time is charged at receipt. A nonterminal move then adds five seconds to its mover and starts the opponent's clock. Rejected commands leave the deadline unchanged, so elapsed time is never restored.

`CASUAL_UNTIMED` has no play deadline, but still uses the formation timer and disconnect rules.

The safe player view includes:

- remaining milliseconds for each side;
- formation and active-turn deadlines;
- increment;
- server timestamp;
- timer mode in match metadata.

The backend evaluates deadlines every 250 milliseconds and sends a persisted `TIMER_SYNC` about every 30 seconds, plus synchronization on turns and reconnects. It does not broadcast every second. The browser animates locally and replaces its estimate on every authoritative view.

Defaults are environment configurable with `RANKED_FORMATION_LIMIT`, `RANKED_INITIAL_CLOCK`, `RANKED_MOVE_INCREMENT`, `RANKED_DISCONNECT_GRACE` and `RANKED_CUMULATIVE_DISCONNECT_LIMIT`. Scheduler cadence uses `MATCH_DEADLINE_EVALUATION_MS` and `MATCH_TIMER_SYNC_MS`. Persisted absolute deadlines and remaining time are never recomputed merely because configuration changes.

## Presence and reconnect

Presence comes from authenticated STOMP subscriptions and disconnect events, not `beforeunload`. Multiple browser sessions count as one connected participant until the last subscribed WebSocket closes.

Once both seats are occupied:

- a disconnect starts a 60-second grace period;
- a timed active player's play clock continues;
- casual matches have no cumulative disconnect limit;
- ranked matches forfeit at 120 cumulative disconnected seconds;
- one disconnected player forfeits when their grace expires;
- while both remain disconnected, the first grace expiry alone does not end the match;
- if both grace periods expire, the result is `NO_CONTEST`;
- if one reconnects, an already-expired grace for the opponent is evaluated immediately;
- the earliest play timeout, cumulative forfeit, disconnect forfeit, setup timeout or no-contest deadline becomes the immutable result.

Connection state, disconnect start times and accumulated durations are persisted in the match snapshot. On backend restart, formerly connected sessions are changed to disconnected at startup, overdue deadlines are finalized idempotently, and reconnecting clients register their STOMP subscriptions before refetching a complete player-safe view.

## Versions and live sequence

`version` changes for authoritative match state, including presence and terminal transitions. `liveSequence` orders every pushed update, including timer synchronizations that do not create a new command version. Both are persisted. Clients accept only the next live sequence, ignore duplicate delivery and recover a gap through safe REST refetch.
