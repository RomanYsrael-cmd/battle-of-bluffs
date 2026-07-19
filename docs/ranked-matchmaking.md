# Ranked matchmaking

Ranked matchmaking is a deliberately single-instance, in-memory queue that creates normal PostgreSQL-backed authoritative matches.

## API and delivery

All endpoints require an authenticated Spring Security session. State changes also require the session CSRF token.

- `POST /api/matchmaking/ranked` joins the queue or returns the existing queue/match status for an exact retry.
- `DELETE /api/matchmaking/ranked` removes only the signed-in player's queue entry.
- `GET /api/matchmaking/status` returns `IDLE`, `QUEUED` or `MATCH_FOUND` and is the recovery path after a missed WebSocket notification.
- `/user/queue/matchmaking` receives the user-scoped `MATCHMAKING_FOUND` STOMP event with the player's authorized initial view.

Only active accounts with verified email may queue. A player has at most one entry and cannot queue while participating in any nonterminal lobby or match. Account eligibility and open-match state are checked again before pairing. A conflict returns HTTP 409 `OPEN_MATCH_EXISTS` with safe current-match recovery summaries rather than a dead-end generic error.

## Pair selection

Entries preserve insertion order. The matcher considers the oldest entry first and pairs it with the oldest later compatible entry. A player never matches their own entry.

The acceptable rating difference begins at 200, grows by 50 for every complete 30 seconds each player has waited, and stops at 600. A pair is compatible only when its rating difference fits both players' current ranges.

Match creation and removal of both entries occur inside the queue's single synchronization boundary. Persistence must succeed before either entry is removed. Broker delivery happens afterward and cannot reverse a persisted match; clients recover with the status endpoint if delivery fails.

Ranked matches:

- have both account seats assigned at creation;
- use `STANDARD_15_PLUS_5` regardless of client input;
- start in formation with the authoritative setup deadline;
- have no room code because no join step is required;
- use the existing snapshot, presence, secrecy, command and result machinery.

## Frontend behavior

The play dashboard provides queue join/cancel controls, elapsed time, current search range, rating, connection state and a direct transition into the matched formation. When another match blocks entry, the queue card explains that a game is already in progress and offers Continue Game plus Cancel Unused Room only when the server authorizes it. Successful cancellation refreshes both current activity and queue eligibility. A synchronous request guard prevents rapid duplicate clicks before the pending UI renders, while the server also treats a duplicate enqueue as an idempotent status read.

## Deployment limitation

Queue entries are intentionally not persisted and disappear when the single application instance restarts. Persisted matches do not disappear. A future multi-instance deployment would require shared queue state, leader election or another distributed coordination mechanism; Redis and distributed locking are outside this milestone.
