# Battle of Bluffs

Monorepo foundation for a server-authoritative Game of the Generals–style game. The authoritative specification is [docs/game-rules.md](docs/game-rules.md).

## Repository structure

- `backend/` — Java 17 and Spring Boot application plus the pure Java rules domain
- `frontend/` — React, TypeScript, Vite, and Tailwind local formation interface
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

Join it using the returned `matchId`, `roomCode`, and current `version`:

```bash
curl -sS -X POST http://localhost:8080/api/dev/matches/MATCH_ID/join \
  -H 'Content-Type: application/json' \
  -d '{"commandId":"NEW_UUID","roomCode":"ROOM_CODE","playerId":"bob-dev","expectedVersion":1}'
```

The `playerId` values in request bodies and query parameters are temporary development credentials. They are **not authentication** and must not be exposed as a production security design. See [docs/development-api.md](docs/development-api.md) for the remaining endpoints and request shapes.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

For repeatable validation:

```bash
npm test
npm run build
```

The formation screen is local-only. It does not send pieces or ranks to the backend.

## Current scope

Implemented in this foundation:

- Spring Boot application configuration, development security policy, Actuator, JPA wiring, and Flyway wiring
- Pure Java board, movement, battle, and resolved victory-rule domain foundation
- Exhaustive ordered-rank battle tests and focused movement/invariant tests
- PostgreSQL development Compose service
- Responsive local 8×9 formation placement, swapping, removal, reset, validation, and lock interaction
- In-memory, server-authoritative private-match application service and temporary development REST API

Explicitly deferred:

- authentication and secure player identities
- matchmaking and lobby behavior
- WebSocket live synchronization and finalized event contracts
- complete persistence entities and database schema
- video/voice calling
- rankings, replay delivery, and production deployment
