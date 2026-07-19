# Full platform progress

This document tracks the local implementation of the complete **Games of the Generals** platform on `feat/full-platform-no-video`. Checkpoint commits remain local and are never pushed by this workflow.

## Phase status

| Phase | Status | Summary |
|---|---|---|
| 1. Insignia and branding | Complete | Original SVG insignia, neutral Flag and centralized Games of the Generals branding. |
| 2. Persistence | Pending | PostgreSQL schema, adapters, snapshots, restart recovery. |
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

None yet.

## Validation log

- Starting point: backend 293 tests; frontend 55 tests after insignia refinement; frontend production build passed.
- Phase 1: frontend 55/55 tests passed; TypeScript/Vite production build passed; `git diff --check` passed.

## Blockers and remaining work

- Docker API access may require the local user to have Docker socket permission; implementation and non-container tests can proceed independently.
- Phases 2–11 remain.
