# Temporary development match API

Base path: `/api/dev/matches`

This insecure compatibility API is registered only when the explicit Spring `dev` profile is active. Start it with `SPRING_PROFILES_ACTIVE=dev`; it is absent from normal and production profiles. A `playerId` supplied in a body or query parameter is only a temporary development credential and is not authentication. The normal PostgreSQL match repository still persists its data.

New frontend work must use the authenticated `/api/matches` API described below. `/api/dev` exists only for low-level diagnostics and legacy application-layer testing.

Accepted state-changing commands use a UUID `commandId` and the current `expectedVersion`. A match starts at version `1`; every accepted command increments it exactly once. Retry an accepted request with the same command ID and identical payload to receive its recorded result. Reusing the ID with another payload produces `COMMAND_CONFLICT`.

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
- `POST /api/matches/join` — join by `commandId`, `roomCode`, and `expectedVersion`.
- `GET /api/matches/{matchId}` — retrieve the authenticated participant's secrecy-safe view.
- `PUT /api/matches/{matchId}/formation`
- `POST /api/matches/{matchId}/lock`
- `POST /api/matches/{matchId}/moves`
- `POST /api/matches/{matchId}/resign`

All state-changing browser requests include the CSRF header returned by `GET /api/auth/csrf` and `credentials: include`. Anonymous users, nonparticipants, stale versions, reused command IDs with changed payloads, and cross-player actions are rejected.
