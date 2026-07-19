# Games of the Generals

Monorepo for **Games of the Generals**, a server-authoritative hidden-information strategy game. The authoritative specification is [docs/game-rules.md](docs/game-rules.md).

## Repository structure

- `backend/` — Java 17 and Spring Boot application plus the pure Java rules domain
- `frontend/` — React, TypeScript, Vite, Tailwind, and TanStack Query match client
- `infra/compose.yaml` — local PostgreSQL 18 service
- `docs/` — game rules and architecture documentation
- `contracts/` — reserved multiplayer protocol contracts

## Prerequisites

- Java 17
- Docker with Docker Compose
- Node.js 20 or newer and npm

The Maven Wrapper is included, so a system Maven installation is not required.

## Start PostgreSQL

```bash
docker compose -f infra/compose.yaml up -d
docker compose -f infra/compose.yaml ps
```

The local database uses the development-only credentials in `infra/compose.yaml`. Backend defaults match them. Override `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` when needed; `.env.example` lists the expected variables.

## Backend

Run the pure domain and backend tests:

```bash
cd backend
./mvnw test
```

Start the application while PostgreSQL is healthy:

```bash
cd backend
./mvnw spring-boot:run
```

Health and application information are available at:

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/actuator/info`

### In-memory development match API

The backend currently supports a complete two-player private match in memory: room creation/joining, server-validated formations, locking, random first-player selection, authoritative moves and battles, resignation, optimistic versioning, idempotent commands, public event history, and player-specific secret-safe views. Restarting the backend removes every match.

Create a match:

```bash
curl -sS -X POST http://localhost:8080/api/dev/matches \
  -H 'Content-Type: application/json' \
  -d '{"playerId":"alice-dev"}'
```

Join it using the room code:

```bash
curl -sS -X POST http://localhost:8080/api/dev/matches/join \
  -H 'Content-Type: application/json' \
  -d '{"commandId":"NEW_UUID","roomCode":"ROOM_CODE","playerId":"bob-dev","expectedVersion":1}'
```

The `playerId` values in request bodies and query parameters are temporary development credentials. They are **not authentication** and must not be exposed as a production security design. See [docs/development-api.md](docs/development-api.md) for the remaining endpoints and request shapes.

## Frontend

```bash
cd frontend
npm ci
npm run dev
```

Vite serves the frontend at `http://localhost:5173` and proxies `/api` requests to the backend at `http://localhost:8080`. Set `VITE_API_BASE_URL` to override the backend origin when needed.

Open two browser tabs or windows. Create a private match in the first, then enter its room code in the second. Session identity is stored in `sessionStorage`, so separate tabs can act as separate players. These temporary IDs are development conveniences, not authentication; use **Leave local session** before reusing a tab for another player.

For repeatable validation:

```bash
npm test
npm run build
```

The frontend currently synchronizes through REST polling about every 1.5 seconds while a match is waiting, in formation setup, or active. Terminal matches stop polling. This is temporary synchronization rather than real-time push; WebSocket delivery is deferred to the next milestone.

## Current scope

Implemented in this foundation:

- Spring Boot application configuration, development security policy, Actuator, JPA wiring, and Flyway wiring
- Pure Java board, movement, battle, and resolved victory-rule domain foundation
- Exhaustive ordered-rank battle tests and focused movement/invariant tests
- PostgreSQL development Compose service
- Responsive local 8×9 formation placement, swapping, removal, reset, validation, and lock interaction
- In-memory, server-authoritative private-match application service and temporary development REST API
- REST-connected private-room frontend with per-tab identity, formation setup, active play, polling, resignation, event history, and terminal disclosure

Explicitly deferred:

- authentication and secure player identities
- matchmaking and lobby behavior
- WebSocket live synchronization and finalized event contracts (next milestone)
- complete persistence entities and database schema
- video/voice calling
- rankings, replay delivery, and production deployment
