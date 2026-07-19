# Database schema

Flyway migration `V1__full_platform_foundation.sql` creates the complete milestone schema. Hibernate validation never creates or mutates tables (`ddl-auto: none`). PostgreSQL 18 is used locally and in integration/E2E validation.

## Main tables

- `users` — original and normalized identities, encoded password, display name, status and audit timestamps
- `email_verification_tokens`, `password_reset_tokens` — only token hashes, expiry and one-time use
- `user_sessions` — reserved durable session metadata foundation; Spring's current HTTP session remains server-managed
- `matches` — room/mode/timer metadata, optimistic persistence version and current authoritative JSON snapshot
- `match_players` — side assignment and account/player key
- `match_formations` — authoritative and public piece IDs, rank, position, alive/locked state
- `match_snapshots` — immutable snapshots by aggregate version
- `match_events` — ordered public event payloads
- `match_commands` — bounded accepted command fingerprints/results used for restart-safe idempotency
- `match_chat_messages` — participant message UUID, per-match sequence, plain-text body and time
- `user_blocks`, `player_reports` — safety relationships and scoped evidence
- `rating_seasons`, `player_ratings`, `rating_changes` — season state, current totals and immutable exactly-once match ledger

Foreign keys remove dependent private data with deleted users/matches where appropriate. Unique constraints protect normalized identities, room codes, public piece IDs, command IDs, chat sequences and one rating row per match/player. Leaderboard, history, token-expiry, session and chat indexes support current access patterns.

`PostgresMatchRepository` restores every persisted aggregate at startup, reconstructs room lookup, marks prior live connections disconnected, evaluates overdue deadlines, and retains accepted command history. The Testcontainers integration test applies Flyway to a fresh PostgreSQL 18 container and proves exact command replay after constructing a new repository instance.

Future schema changes must add a new ordered migration; never edit an already-deployed migration.
