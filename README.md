# Games of the Generals

Games of the Generals is a local-development-ready, server-authoritative implementation of the Filipino hidden-information strategy game. It supports persistent browser accounts, private casual rooms, ranked pairing, live play, clocks, text chat, safety controls, profiles, history, ratings and leaderboards. The authoritative rules are in [docs/game-rules.md](docs/game-rules.md).

Production must use `SPRING_PROFILES_ACTIVE=prod`, external database/SMTP secrets and an exact HTTPS `FRONTEND_URL`; the development Compose stack and sample credentials are not production-safe. Review [production deployment security](docs/production-deployment-security.md), [security controls](docs/security.md), and the [production audit](docs/security-audit-production.md). Report vulnerabilities privately to the repository owner without live secrets or personal data.

The production frontend is built for `https://bluffs.romanlms.com` with public `VITE_API_BASE_URL=https://romanlms.com/bluffs/api` and `VITE_WS_URL=wss://romanlms.com/bluffs/ws`. The dedicated backend binds only to `127.0.0.1:8090`; nginx translates the public `/bluffs` routes. See the [production runbook](docs/production-deployment-runbook.md) and [deployment progress](docs/production-deployment-progress.md). Server secrets are never Vite variables or repository files.

Video and voice are intentionally outside this project.

## Architecture

- `backend/` — Java 17, Spring Boot 4.1, Spring Security sessions/CSRF, STOMP, JPA, Flyway and PostgreSQL
- `frontend/` — React 19, TypeScript, Vite, TanStack Query and STOMP
- `infra/compose.yaml` — persistent PostgreSQL 18 and Mailpit services
- `docs/` — protocol, security, persistence and local-development references

The backend is one modular monolith. REST accepts authoritative game commands, PostgreSQL stores accounts and match aggregates, and participant-specific WebSocket messages deliver rank-safe updates. Redis, external OAuth and media infrastructure are not required.

## Prerequisites

- Java 17
- Node.js 22 and npm
- Docker Engine with Docker Compose

The Maven Wrapper is included.

## Start locally

Copy the safe development defaults if you want to customize them:

```bash
cp .env.example .env
```

Start PostgreSQL 18 and Mailpit without deleting the persistent database volume:

```bash
docker compose --env-file .env -f infra/compose.yaml up -d
docker compose --env-file .env -f infra/compose.yaml ps
```

Start the backend:

```bash
cd backend
./mvnw spring-boot:run
```

Start the frontend in another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Backend health is at `http://localhost:8080/actuator/health`; Mailpit is at `http://localhost:8025`.

Register an account, open its verification message in Mailpit, and follow the `http://localhost:5173/verify-email?...` link. Verification unlocks ranked matchmaking. Password-reset messages use the same local inbox.

## Playing

For a casual match, sign in from two separate browser profiles or private contexts. One player creates a room and shares its six-character code; the other joins. Both place exactly 21 pieces, submit, lock, make legal canonical-coordinate moves, and may chat, block, report or resign.

Open matches are recovered from the authenticated account, not from browser storage. The play dashboard shows every current lobby or match and the navigation bar keeps a compact Continue Game indicator on other account screens. Before play begins, a host may cancel its casual room; an unlocked guest may leave and free Player 2 for a replacement. Host departure cancels the room. Once play is active—or for any ranked pairing—the match must be resumed and may end only through the normal resignation, clock, disconnect, or game-result rules. A cancelled room uses `ROOM_CANCELLED` and affects neither ratings nor game statistics.

For ranked play, two verified players select **Enter ranked queue**. The range begins at ±200 rating and expands by 50 every 30 seconds to ±600. Pairing creates a persistent 15+5 match without a room code.

The server owns versions, idempotency, formations, moves, battles, clocks, presence, terminal results and rating changes. Player 2 sees a rotated board, but requests always use canonical coordinates. Active opponent ranks and authoritative IDs are never sent; complete formations are disclosed to participants only after termination.

## Live updates and reconnects

The browser uses the authenticated session at `/ws`, subscribes only to its authorized user destinations, and refetches a safe REST view after connect or reconnect. It ignores duplicate live sequences and refetches on a gap. A 15-second REST fallback runs only while live synchronization is unavailable.

Formation, 15+5 play, disconnect and cumulative ranked clocks are authoritative persisted deadlines. The browser countdown is a display estimate corrected by server messages.

## Tests

```bash
cd backend
./mvnw test

cd ../frontend
npm test -- --run
npm run build
npm run typecheck:e2e
```

The PostgreSQL integration test uses Testcontainers and runs when Docker is accessible. To run the four isolated-context browser flows, first start the normal Docker, backend and frontend services, then:

```bash
cd frontend
npx playwright install chromium
npm run test:e2e
```

Playwright retrieves verification messages from Mailpit and covers accounts, abandoned-lobby recovery, cancellation and seat reuse, casual play, ranked rating application, outsider denial, hidden-rank secrecy and inert hostile chat text.

## Development-only API

The client-supplied identity compatibility API under `/api/dev/**` exists only with `SPRING_PROFILES_ACTIVE=dev`. Normal browser work must use authenticated `/api/**` routes. See [docs/development-api.md](docs/development-api.md).

## Current limitations

- Ranked queue entries are in memory and intentionally single-instance; created matches are persistent.
- Private lobbies have explicit owner cancellation but no automatic inactivity expiry yet; stale-lobby cleanup remains a future configurable policy.
- No administrator moderation UI is included.
- No production deployment, OAuth, spectators, matchmaking clusters, Redis, video or voice is included.
- Email delivery is SMTP-only; Mailpit is the supported local target.

More detail: [architecture](docs/architecture.md), [local development](docs/local-development.md), [authentication](docs/authentication.md), [WebSocket protocol](docs/websocket-protocol.md), [database schema](docs/database-schema.md), [ratings](docs/rating-and-leaderboard.md) and [security](docs/security.md).
