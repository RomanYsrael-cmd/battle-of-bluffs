# Full platform progress

This document tracks the local implementation of the complete **Games of the Generals** platform on `feat/full-platform-no-video`. Checkpoint commits remain local and are never pushed by this workflow.

## Phase status

| Phase | Status | Summary |
|---|---|---|
| 1. Insignia and branding | Complete | Original SVG insignia, neutral Flag and centralized Games of the Generals branding. |
| 2. Persistence | Complete | PostgreSQL schema, authoritative aggregate snapshots, restart recovery and bounded idempotency history. |
| 3. Accounts | Pending | Session authentication, verification, reset, Mailpit, frontend auth. |
| 4. Authenticated matches | Pending | Session-derived player identity and production match API. |
| 5. WebSocket | Pending | Safe player-specific push synchronization. |
| 6. Timers and presence | Pending | Authoritative clocks, reconnect and disconnect outcomes. |
| 7. Chat and safety | Pending | Chat, mute, block and reports. |
| 8. Profiles and ratings | Pending | History, Elo, tiers and leaderboards. |
| 9. Ranked matchmaking | Pending | Single-instance verified-player queue. |
| 10. Frontend integration | Pending | Complete routed authenticated application. |
| 11. Security and delivery | Pending | Security audit, E2E, CI and documentation. |

## Architectural decisions

- The existing pure Java `game/domain` package remains framework-independent.
- PostgreSQL adapters, security, HTTP and WebSocket code live outside the domain.
- Browser authentication uses Spring Security sessions and CSRF, not browser-stored JWTs.
- Match updates are player-specific so active opponent ranks and authoritative piece IDs remain secret.
- The application remains a single Spring Boot modular monolith without Redis or message brokers.

## Migrations

- `V1__full_platform_foundation.sql` creates the account, session, match, command, event, chat, safety, season and rating foundation tables with the required indexes and initial rating season.

## Validation log

- Starting point: backend 293 tests; frontend 55 tests after insignia refinement; frontend production build passed.
- Phase 1: frontend 55/55 tests passed; TypeScript/Vite production build passed; `git diff --check` passed.
- Phase 2: backend 294/294 tests passed. Flyway applied V1 to PostgreSQL 18.4, an authoritative match was created through an alternate local backend on port 18080, and the same versioned player view was recovered after a full backend restart. The regular development services on ports 8080 and 5173 were not disturbed.

## Blockers and remaining work

- Docker API access may require the local user to have Docker socket permission. The existing healthy PostgreSQL service was sufficient for the Phase 2 restart validation.
- Phases 3–11 remain.
