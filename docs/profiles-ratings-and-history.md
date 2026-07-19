# Profiles, ratings and match history

Profiles and competition data are derived from authoritative persisted matches. Browser requests use the authenticated Spring Security session; clients never supply a player identity.

## HTTP API

- `GET /api/profile/me` returns the signed-in player's private profile, including email and verification status.
- `PATCH /api/profile/me` changes only the display name and requires the session CSRF token.
- `GET /api/profiles/{username}` returns a public profile without email, account secrets, reports, sessions or block data.
- `GET /api/profile/me/matches?page=0&size=20` returns the signed-in player's paginated history.
- `GET /api/matches/{matchId}/history` returns a participant-safe match view to a participant and a minimal public summary to anyone else. Active views continue to enforce hidden-rank secrecy. Chat history remains available only through its participant-authorized endpoint.
- `GET /api/leaderboards/seasonal?page=0&size=25` returns the active-season rating board.
- `GET /api/leaderboards/all-time?page=0&size=25` returns accumulated competitive statistics.

History responses use the same safe view mapper as live matches. Participants receive the persisted event timeline and complete formation disclosure only after the match is terminal. Nonparticipants never receive room codes, formations, pieces, ranks or chat.

## Rating calculation

The active season starts players at 1200. The first 10 rated games use K=40 and later games use K=24. Expected score uses the standard Elo formula:

`E(a) = 1 / (1 + 10 ^ ((R(b) - R(a)) / 400))`

Win, draw and loss scores are 1, 0.5 and 0. Casual matches and `NO_CONTEST` results do not alter ratings; ranked resignations and disconnect forfeits use their terminal winner normally.

When players have different K-factors, their independent raw deltas are reconciled into one zero-sum pair. Player one's integer delta is `round((rawOne - rawTwo) / 2)` and player two receives its exact inverse. This is deterministic, keeps the pair balanced after rounding, and treats neither seat preferentially.

Both player-rating updates and both immutable rating-ledger rows are written in one database transaction. The ledger's match/account uniqueness constraints and the match-level existence check make restart reconciliation, reconnects and command retries idempotent.

## Competitive tiers

| Rating | Competitive tier |
|---:|---|
| below 900 | Cadet |
| 900–1099 | Private |
| 1100–1299 | Sergeant |
| 1300–1499 | Lieutenant |
| 1500–1699 | Captain |
| 1700–1899 | Colonel |
| 1900–2099 | General |
| 2100 and above | Grand General |

The term *competitive tier* is kept distinct from a game piece's rank.

## Leaderboard policy

Public placement requires five completed ranked games. Suspended and deleted accounts are excluded. Seasonal ties are ordered by rating, rated games and stable account UUID; all-time ties are ordered by wins, rated games and username. The authenticated player's own entry is returned separately even when provisional, ineligible or outside the requested page.
