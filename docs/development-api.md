# Temporary development match API

Base path: `/api/dev/matches`

This insecure compatibility API is registered only when the explicit Spring `dev` profile is active. Start it with `SPRING_PROFILES_ACTIVE=dev`; it is absent from normal and production profiles. A `playerId` supplied in a body or query parameter is only a temporary development credential and is not authentication. The normal PostgreSQL match repository still persists its data.

New frontend work must use the authenticated `/api/matches` API described below. `/api/dev` exists only for low-level diagnostics and legacy application-layer testing.

Accepted state-changing commands use a UUID `commandId` and the current `expectedVersion`. A match starts at version `1`; every accepted command increments it exactly once. Retry an accepted request with the same command ID and identical payload to receive its recorded result. Reusing the ID with another payload produces `COMMAND_CONFLICT`. The authenticated production room-code join is the exception: `expectedVersion` is optional because a replacement guest cannot know the room version after an earlier guest leaves. If supplied, it is still enforced; seat assignment remains serialized and idempotent.

## Endpoints

- `POST /api/dev/matches` — create a room. Body may contain `{"playerId":"alice"}`; an opaque development ID is generated when omitted.
- `POST /api/dev/matches/{matchId}/join` — join the room. Body: `commandId`, `roomCode`, optional `playerId`, and `expectedVersion`.
- `POST /api/dev/matches/join` — join with the same body when the caller knows only the room code.
- `PUT /api/dev/matches/{matchId}/formation` — submit or replace the requesting player's unlocked formation.
- `POST /api/dev/matches/{matchId}/lock` — permanently lock the requesting player's valid formation.
- `POST /api/dev/matches/{matchId}/moves` — make one canonical-coordinate move.
- `POST /api/dev/matches/{matchId}/resign` — make the opponent the winner by resignation.
- `GET /api/dev/matches/{matchId}?playerId=...` — retrieve only that participant's safe view.

A formation request has this shape and must contain exactly 21 entries with the inventory specified in the rules:

```json
{
  "commandId": "72d9b632-4d8a-455e-a31e-ed72808470b4",
  "playerId": "alice",
  "expectedVersion": 2,
  "pieces": [
    {"pieceId": "5dc3ef7a-9e67-41fd-a8f3-d99493eeaf86", "rank": "FLAG", "row": 0, "column": 0}
  ]
}
```

A move request contains no rank or claimed outcome:

```json
{
  "commandId": "98a277f9-5630-4928-b18b-66e05de09df1",
  "playerId": "alice",
  "expectedVersion": 6,
  "source": {"row": 2, "column": 0},
  "destination": {"row": 3, "column": 0}
}
```

Errors consistently contain `code`, `message`, and `timestamp`. Active opponent pieces use a separate DTO containing only position and a server-generated public UUID. That public UUID is stable within the match but is distinct from the formation submitter's `pieceId`; the same translation applies to opponent removals and post-match opponent pieces. Ranks are disclosed to both participants only after the match is terminal.

## Authenticated match API

The normal frontend uses a Spring Security session and session-bound CSRF token. Player identity always comes from the authenticated `AccountPrincipal`; production request bodies have no `playerId` property.

- `POST /api/matches` — create a casual private match with `CASUAL_UNTIMED` or `STANDARD_15_PLUS_5`.
- `POST /api/matches/join` — join by `commandId` and `roomCode`; `expectedVersion` is optional and enforced when supplied.
- `GET /api/matches/current` — return every nonterminal lobby or match for the authenticated account, including version, side, phase, safe setup/turn status, lifecycle permissions, timestamps and `resumeRoute`.
- `GET /api/matches/{matchId}` — retrieve the authenticated participant's secrecy-safe view.
- `PUT /api/matches/{matchId}/formation`
- `POST /api/matches/{matchId}/lock`
- `POST /api/matches/{matchId}/moves`
- `POST /api/matches/{matchId}/resign`
- `POST /api/matches/{matchId}/cancel` — host-only, versioned cancellation of a casual pre-game room.
- `POST /api/matches/{matchId}/leave` — remove an unlocked casual guest; host use cancels the room.

All state-changing browser requests include the CSRF header returned by `GET /api/auth/csrf` and `credentials: include`. Anonymous users, nonparticipants, stale versions, reused command IDs with changed payloads, and cross-player actions are rejected.

Create and command responses use this envelope:

```json
{
  "commandId": "accepted-command-uuid-or-null",
  "version": 2,
  "matchId": "match-uuid",
  "roomCode": "ABC234",
  "view": {
    "phase": "FORMATION",
    "requestingSide": "PLAYER_TWO",
    "ownPieces": [],
    "opponentPieces": []
  }
}
```

Ranked `roomCode` is `null`. An active `opponentPieces` entry contains only opaque `id` and canonical `position`; rank is not serialized.

Current-match summaries never contain pieces, ranks, opponent account identity or authoritative piece IDs. Terminal matches, including `ROOM_CANCELLED`, are omitted. When multiple legacy open matches exist, all are returned with `multipleOpenMatches: true`; the server never silently chooses one. Cancel and leave bodies use the normal `{commandId, expectedVersion}` contract and return `{commandId, version, matchId, action}`. Exact retries return the persisted lifecycle result after restart, while a changed retry receives `COMMAND_CONFLICT`.

An unlocked Player 2 may leave before either formation is locked. Their seat, formation, public piece mapping, presence/timer state and participant chat cycle are removed; a replacement cannot read the prior guest's chat. A host may cancel during pre-game formation, including after a lock, producing the non-rated `ROOM_CANCELLED` terminal reason. Active and ranked matches reject these lobby operations and must be resumed or resigned. There is currently no automatic stale-lobby expiry.

## Account and competition routes

- `GET /api/auth/csrf`
- `POST /api/auth/register`, `/login`, `/logout`, `/verify-email`, `/resend-verification`, `/forgot-password`, `/reset-password`
- `GET /api/auth/me`
- `GET|PATCH /api/profile/me`
- `GET /api/profiles/{username}`
- `GET /api/profile/me/matches?page=0&size=20`
- `GET /api/matches/{matchId}/history`
- `GET /api/leaderboards/seasonal?page=0&size=25`
- `GET /api/leaderboards/all-time?page=0&size=25`
- `POST|DELETE /api/matchmaking/ranked`
- `GET /api/matchmaking/status`

## Chat and safety routes

- `GET /api/matches/{matchId}/chat?afterSequence=10`
- `GET /api/matches/{matchId}/moderation`
- `POST|DELETE /api/matches/{matchId}/block`
- `POST /api/matches/{matchId}/reports`

Only participants may use these routes. Report payloads contain `category`, optional `comment` and up to 20 `chatMessageReferences`; the reported player is always derived by the server.

## Error response

```json
{
  "code": "STALE_VERSION",
  "message": "Expected version does not match current match version",
  "timestamp": "2026-07-19T00:00:00Z"
}
```

Typical statuses are 400 for malformed input, 401 for missing/invalid authentication, 403 for CSRF or authorization, 404 for unknown resources, 409 for version/idempotency/state conflicts, 422 for legal-rule rejection and 429 for throttling. Messages are safe for display but clients should branch on `code`.

Ranked enqueue blocked by a nonterminal match returns HTTP 409 with `code: OPEN_MATCH_EXISTS` and a `context.blockingMatches` array containing only the same safe current-match summaries. The dashboard and queue notice use that context to resume or cancel an eligible unused room.
