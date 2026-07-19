# Rating and leaderboard

Ranked players start each season at 1200. The expected score is standard Elo:

`E(a) = 1 / (1 + 10 ^ ((R(b) - R(a)) / 400))`

Actual scores are 1 for a win, 0.5 for a draw and 0 for a loss. The first ten rated games use K=40; later games use K=24. Independent raw changes are reconciled to a deterministic zero-sum pair: player one receives `round((rawOne - rawTwo) / 2)` and player two receives the inverse.

Both rating rows and two immutable ledger entries are saved in one transaction. `(match_id, user_id)` uniqueness plus a match-level ledger check makes repeated terminal reads, retries and restart reconciliation idempotent. Casual and `NO_CONTEST` matches never change ratings.

## Competitive tiers

| Rating | Tier |
|---:|---|
| below 900 | Cadet |
| 900–1099 | Private |
| 1100–1299 | Sergeant |
| 1300–1499 | Lieutenant |
| 1500–1699 | Captain |
| 1700–1899 | Colonel |
| 1900–2099 | General |
| 2100+ | Grand General |

Tier names describe competition standing, not board-piece rank.

`GET /api/leaderboards/seasonal?page=0&size=25` returns the active season. `GET /api/leaderboards/all-time?page=0&size=25` returns accumulated results. Public placement requires five rated games. Suspended/deleted accounts are excluded. Stable tie-breakers prevent page reshuffling, and the authenticated player's own row is returned even when provisional, ineligible or off-page.
