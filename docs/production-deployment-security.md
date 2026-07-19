# Production deployment security

## Minimal topology

Internet traffic terminates TLS at a maintained reverse proxy. The proxy serves the built frontend and forwards only `/api` and `/ws` to one private backend instance. The backend alone reaches PostgreSQL and the authenticated STARTTLS SMTP relay. PostgreSQL, SMTP administration and Actuator are never internet-bound. `infra/compose.yaml` is development-only.

Forwarded headers are disabled by default (`server.forward-headers-strategy=none`), so the application uses the socket peer for IP throttles. If a deployment later enables forwarded headers, restrict direct backend access to the proxy and configure an exact trusted-proxy boundary first. Verification/reset links always derive from `FRONTEND_URL`, never Host or forwarded-host headers.

## Required configuration

Start with `SPRING_PROFILES_ACTIVE=prod`. Supply `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `FRONTEND_URL`, `ALLOWED_HOSTS`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, and `MAIL_FROM` through the platform secret/configuration store. `FRONTEND_URL` must be the exact public HTTPS origin. `ALLOWED_HOSTS` is a comma-separated exact hostname allowlist for the public proxy/health-check names, without schemes or paths. Production refuses insecure cookies, non-TLS SMTP, mixed dev/test profiles, unsafe sender values, hostile Host values, missing required variables, and active `DEBUG`/`TRACE` environment flags. Some Linux environments define `DEBUG` for unrelated tooling; explicitly remove it from the backend process environment rather than passing an arbitrary value.

Optional capacity controls include `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_CONNECTION_TIMEOUT_MS`, `SESSION_IDLE_TIMEOUT`, and `SESSION_ABSOLUTE_TIMEOUT`. Keep the defaults unless load testing justifies a measured change. Never put secrets in `VITE_*`; frontend variables are public bundle content.

## Database and backups

Create a non-superuser application role limited to its schema and required DML/sequence operations. Use a separate migration owner in mature deployments; do not grant schema/database creation or server-file roles to the runtime account. Require PostgreSQL TLS and certificate verification across untrusted networks. Set role-level `statement_timeout`, `lock_timeout` and `idle_in_transaction_session_timeout` appropriate to observed game transactions.

Encrypt backups, restrict restore credentials, record retention/deletion policy, and test restoration into an isolated environment. Backups contain account PII, password hashes, chat/report evidence and complete private match snapshots. Never place dumps under the repository or web root.

## Proxy and operational controls

- Redirect HTTP to HTTPS, then enable HSTS only on the HTTPS production host; Spring emits HSTS for secure requests.
- Preserve the exact Origin header and support WebSocket upgrades. Allow only the configured frontend origin.
- Limit JSON/request bodies to 1 MiB and enforce connection/rate ceilings at the proxy; current app DTO/HTTP/STOMP limits and queues are single-instance.
- Preserve or generate a safe `X-Request-ID` at the edge. The backend accepts only 1–64 ASCII letters, digits, dots, underscores or hyphens and replaces all other values.
- Do not log cookies, authorization/CSRF headers, request bodies, WebSocket bodies or query strings on token routes.
- Protect logs and failure artifacts; CI retains browser diagnostics for seven days only on failure.
- Monitor authentication throttles, authorization failures, account disable/reset events, abnormal socket counts, database saturation, 5xx rates and rating reconciliation errors.

## Secret rotation and release

Rotate database and SMTP credentials using overlapping credentials where supported, update the secret store, restart the backend, verify health, then revoke old values. A suspected token/session disclosure requires account-session invalidation and incident handling; an exposed committed secret must be rotated before any separately approved history rewrite.

Before release, run the acceptance commands in `docs/security.md`, start once without required production variables to confirm failure, then start with isolated safe production-like values and verify cookie/header/origin/Actuator/dev-route behavior. Never use Mailpit or `.env.example` credentials in production.
