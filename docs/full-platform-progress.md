# Full platform progress

This document tracks the local implementation of the complete **Games of the Generals** platform on `feat/full-platform-no-video`. Checkpoint commits remain local and are never pushed by this workflow.

## Phase status

| Phase | Status | Summary |
|---|---|---|
| 1. Insignia and branding | Complete | Original SVG insignia, neutral Flag and centralized Games of the Generals branding. |
| 2. Persistence | Complete | PostgreSQL schema, authoritative aggregate snapshots, restart recovery and bounded idempotency history. |
| 3. Accounts | Complete | Session authentication, CSRF, verification/reset tokens, Mailpit configuration and frontend account routes. |
| 4. Authenticated matches | Complete | Session-derived player identity, production match API, casual clock configuration and authenticated frontend. |
| 5. WebSocket | Complete | Authenticated STOMP, participant-authorized subscriptions, safe sequenced views and reconnect recovery. |
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
- CSRF tokens are bound to the server session, fetched from `/api/auth/csrf`, held in frontend memory and attached through the response-provided header name.
- Verification and reset secrets use 256 bits of secure random entropy; PostgreSQL stores only SHA-256 hashes, expiry and one-time-use metadata.
- Unverified accounts may sign in to reach verification status, while suspended and deleted accounts cannot authenticate. Password reset expires every registered live session for the account.
- Match updates are player-specific so active opponent ranks and authoritative piece IDs remain secret.
- REST is the sole authoritative game-command channel. Authenticated STOMP publishes post-persistence safe views to user destinations; the frontend validates match/version sequences and refetches after connect, reconnect or gaps.
- The application remains a single Spring Boot modular monolith without Redis or message brokers.

## Migrations

- `V1__full_platform_foundation.sql` creates the account, session, match, command, event, chat, safety, season and rating foundation tables with the required indexes and initial rating season.

## Validation log

- Starting point: backend 293 tests; frontend 55 tests after insignia refinement; frontend production build passed.
- Phase 1: frontend 55/55 tests passed; TypeScript/Vite production build passed; `git diff --check` passed.
- Phase 2: backend 294/294 tests passed. Flyway applied V1 to PostgreSQL 18.4, an authoritative match was created through an alternate local backend on port 18080, and the same versioned player view was recovered after a full backend restart. The regular development services on ports 8080 and 5173 were not disturbed.
- Phase 3: backend 303/303 tests passed; frontend 56/56 tests and the production build passed. A normal-profile backend started successfully against PostgreSQL 18.4 on port 18080, issued a session-bound CSRF token and returned a structured 401 for an anonymous account request. Compose configuration, including Mailpit SMTP 1025 and UI 8025, validated successfully. Real Mailpit delivery could not be exercised because the local user was denied access to `/var/run/docker.sock`; the fake-mail tests cover hashed one-time verification/reset issuance without external SMTP.
- Phase 4: backend 308/308 tests passed; frontend 56/56 tests and the production build passed. Production match requests contain no player identity input, use the authenticated account UUID for every command and view, reject anonymous/cross-match access, preserve CSRF/version/idempotency behavior, and complete an authenticated create/join/form/form-lock/move/resign/terminal-disclosure MockMvc flow. The frontend now gates game routes on `/api/auth/me`, stores no player credential, uses `/api/matches`, supports untimed or 15+5 casual rooms and sends credentials plus CSRF. `/api/dev` is registered only under the explicit `dev` profile.
- Phase 5: authenticated STOMP is available at `/ws` with same-origin session identity, 10-second heartbeats and per-subscription participant authorization. Accepted commands publish version-sequenced, server-timestamped views independently for each player. Frontend reconnects automatically, refetches a complete safe REST view after connection, detects sequence gaps, ignores duplicate delivery, and uses a slow 15-second fallback only outside synchronized state. Backend 316/316 tests prove outsider/anonymous subscriptions and forged client broker sends are rejected, both recipients receive different rank-safe payloads, exact retries are not published twice, Jackson 3 envelopes serialize, and broker failure cannot reverse a persisted command. Frontend 59/59 tests and the production build passed. A normal application context also started against PostgreSQL 18.4 on alternate port 18080 with the STOMP broker available; an anonymous `/ws` upgrade returned the expected structured 401, and that temporary instance was stopped.

## Blockers and remaining work

- Docker API access is currently blocked with: `permission denied while trying to connect to the docker API at unix:///var/run/docker.sock`. This prevents starting Mailpit and leaves the aggregate actuator health `DOWN` because the mail health contributor cannot reach port 1025; PostgreSQL remains healthy and account flows are covered with an in-memory mail sender.
- Phases 6–11 remain.
