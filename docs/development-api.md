# Temporary development match API

Base path: `/api/dev/matches`

This API exists to exercise the in-memory match application layer. A `playerId` supplied in a body or query parameter is only a temporary development credential and is not secure authentication. Matches and command histories disappear whenever the backend restarts.

Accepted state-changing commands use a UUID `commandId` and the current `expectedVersion`. A match starts at version `1`; every accepted command increments it exactly once. Retry an accepted request with the same command ID and identical payload to receive its recorded result. Reusing the ID with another payload produces `COMMAND_CONFLICT`.

## Endpoints

- `POST /api/dev/matches` — create a room. Body may contain `{"playerId":"alice"}`; an opaque development ID is generated when omitted.
- `POST /api/dev/matches/{matchId}/join` — join the room. Body: `commandId`, `roomCode`, optional `playerId`, and `expectedVersion`.
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

WebSocket delivery, authentication, database match persistence, matchmaking, scheduling, disconnect handling, and frontend integration remain deferred.
