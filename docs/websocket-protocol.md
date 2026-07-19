# WebSocket protocol

Games of the Generals uses authenticated STOMP over a native WebSocket for participant-specific match updates and private match chat. REST remains the only authoritative game-command channel.

## Connection and authentication

- Endpoint: `GET /ws` (WebSocket upgrade)
- Authentication: the existing Spring Security `JSESSIONID` HTTP session
- Allowed browser origin: `app.frontend-url` / `FRONTEND_URL`
- Heartbeat: 10 seconds in both directions
- Browser reconnect delay: 3 seconds

The browser does not send a player identifier. The WebSocket principal is inherited from the authenticated HTTP upgrade. Anonymous upgrades are rejected.

## Match subscription

Participants subscribe to:

```text
/user/queue/matches/{matchId}
```

Every `SUBSCRIBE` frame is authorized on the server by loading that authenticated account's safe match view. A nonparticipant, an anonymous session, a malformed match ID, or any unrecognized destination is rejected. Formation, lock, move and resignation commands continue through CSRF-protected REST endpoints.

## Update envelope

```json
{
  "type": "MOVE_APPLIED",
  "matchId": "00000000-0000-0000-0000-000000000000",
  "sequence": 8,
  "version": 8,
  "serverTimestamp": "2026-07-19T10:15:30Z",
  "view": {}
}
```

`view` is generated separately for each participant. During an active match, opponent pieces contain only their stable opaque public ID and position. The message never contains the opponent's rank or authoritative piece ID.

Current match update types are:

- `PLAYER_JOINED`
- `FORMATION_SUBMITTED`
- `FORMATION_LOCKED`
- `MATCH_STARTED`
- `MOVE_APPLIED`
- `BATTLE_RESOLVED`
- `FLAG_CHALLENGE_STARTED`
- `MATCH_ENDED`

`sequence` is the persisted `liveSequence`, while `version` is the authoritative command/state version. They normally advance together, but a periodic `TIMER_SYNC` advances only `liveSequence`. Exact command retries are replayed without publishing another update. Each state update is published only after its PostgreSQL save returns.

## Recovery

The frontend waits for the STOMP subscription receipt, then refetches `GET /api/matches/{matchId}` after every connection or reconnection. It applies only the immediately following live sequence, ignores duplicate or delayed sequences, and performs another full safe REST refetch for a gap, wrong match ID, inconsistent version, or invalid message. Normal 1.5-second polling is disabled while synchronized. A 15-second fallback refetch runs only while connecting, disconnected, or recovering.

## Match chat

Both participants may subscribe to these separate user destinations:

```text
/user/queue/matches/{matchId}/chat
/user/queue/matches/{matchId}/chat/errors
```

The only allowed client `SEND` destination is:

```text
/app/matches/{matchId}/chat
```

Its JSON payload contains only `{ "body": "plain text" }`. The server checks membership again, trims surrounding whitespace, rejects empty or over-500-character bodies, enforces five messages per ten seconds per player/match, and commits the message before publishing it. Game commands sent through STOMP and direct sends to broker destinations remain forbidden.

Successful delivery contains a server-generated message ID, match ID, monotonically increasing chat sequence, display name, participant-specific `ownMessage` flag, plain-text body and server timestamp. History is available only to participants through `GET /api/matches/{matchId}/chat`; reconnect loads up to the latest 100 persisted messages and deduplicates by message ID.

Blocking either direction disables subsequent delivery. The client receives validation, blocking and rate-limit failures on the chat-error destination. These errors do not modify the authoritative match version or disclose game-piece information.
