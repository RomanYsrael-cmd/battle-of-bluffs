# Local development

## Services

| Service | Address |
|---|---|
| PostgreSQL 18 | `localhost:5432` |
| Mailpit SMTP | `localhost:1025` |
| Mailpit UI/API | `http://localhost:8025` |
| Spring Boot | `http://localhost:8080` |
| Vite | `http://localhost:5173` |

From the repository root:

```bash
cp .env.example .env
docker compose --env-file .env -f infra/compose.yaml up -d
docker compose --env-file .env -f infra/compose.yaml ps
```

The named PostgreSQL volume is persistent. Normal startup must not use `down -v`, delete the volume, disable Flyway/JPA, or substitute an in-memory match repository.

Run `cd backend && ./mvnw spring-boot:run`, then `cd frontend && npm ci && npm run dev` in another terminal. Confirm `/actuator/health/liveness`, the landing page and Mailpit UI. Registration messages contain local verification links; reset messages use the same inbox.

## Configuration

`.env.example` contains safe datasource, Compose, cookie, frontend-origin, SMTP, sender, account/chat rate-limit and ranked timer examples. Spring reads these environment variables through `application.yml`. Use real secrets outside source control for any nonlocal environment.

The timer values are ISO-8601-compatible shorthand accepted by Spring (`5m`, `15m`, `5s`). Changing them affects newly opened or legacy-recovered deadlines; already persisted absolute deadlines and remaining times remain authoritative.

## Validation

```bash
cd backend && ./mvnw test
cd ../frontend && npm test -- --run && npm run build
npm run typecheck:e2e
npx playwright install chromium
npm run test:e2e
```

The backend suite uses Testcontainers when Docker is available. Playwright requires all five services, retrieves verification mail from Mailpit, creates isolated accounts and leaves diagnostic artifacts only on failure. The E2E suite uses unique identities but a dedicated disposable database is recommended.

Stop only the services started for the session:

```bash
docker compose --env-file .env -f infra/compose.yaml stop
```

## Development compatibility profile

`SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run` additionally exposes `/api/dev/**` and a small development-only identity path. It is absent in the normal profile and is never required by the frontend or E2E suite.
