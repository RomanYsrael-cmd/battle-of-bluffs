# Architecture

Games of the Generals is one Spring Boot modular monolith with one React browser application and PostgreSQL 18. The pure Java `game/domain` package has no Spring dependency. Application services coordinate validated domain operations; adapters provide HTTP, STOMP, PostgreSQL, mail and scheduling.

## Request and update flow

1. Spring Security authenticates a server-side browser session and requires its CSRF token for state changes.
2. Controllers derive the player UUID from `AccountPrincipal`; normal payloads never contain identity.
3. `MatchApplicationService` synchronizes on one in-memory aggregate, validates expected version and command fingerprint, then invokes the reusable movement, battle and victory rules.
4. `PostgresMatchRepository` writes the aggregate snapshot and normalized player, formation, event and bounded command records transactionally.
5. After persistence, the update publisher maps a different secrecy-safe view for each participant and sends it to that user's STOMP destination.
6. The browser verifies match ID, live sequence and version, or refetches the complete safe REST view.

PostgreSQL snapshots are authoritative across restarts. In-memory aggregate objects and the ranked queue are single-instance coordination mechanisms; no distributed-lock claim is made.

## Modules

- `user` — accounts, password encoding, verification/reset tokens, sessions and account throttling
- `game/domain` — board, movement, battle, Flag and victory rules
- `game/application` — match aggregate, versioning, opaque public IDs, persistence codec, clocks and presence
- `game/web` and `game/websocket` — authenticated REST, STOMP authorization and participant-specific delivery
- `social` — persisted participant chat, block and report controls
- `competition` — transactional Elo ledger, tiers and leaderboards
- `profile` — private/public profiles and authorization-scoped history
- `matchmaking` — verified-player in-memory queue that creates persistent ranked matches

## Data and trust boundaries

The browser is untrusted. It proposes formation inventory, coordinates and command IDs but never results, clocks, ratings, player identity or opponent ranks. Internal piece UUIDs remain authoritative; each match generates separate random public UUIDs for opponent-visible pieces. Chat is independent of game state and always rendered as plain text.

The simple STOMP broker is an in-process delivery mechanism, not the source of truth. REST and persisted snapshots recover missed, duplicate or reordered messages.

## Operations

Flyway owns schema evolution. JPA has `ddl-auto: none`. Schedulers evaluate deadlines, send timer synchronization and reconcile missing rating ledgers. All time-sensitive services use the injected UTC `Clock`, enabling deterministic unit tests.

There are no microservices, Redis, JPA-backed queue, video, voice, LiveKit or external identity provider in this milestone.
