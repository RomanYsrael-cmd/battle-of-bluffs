# Production deployment progress

Updated: 2026-07-20 (Asia/Manila)

## Completed safeguards

- PR #3 merged into `main` at `02a8212a36f34c8ae4e5e64d5df32ea3b412989c` after all push, pull-request, CodeQL, dependency scan, SBOM, secret scan, frontend, backend, and Playwright checks passed.
- Deployment branch: `deploy/production-vercel-server`.
- Recovery tag: `backup/pre-production-deploy-20260720-074929`.
- Verified full Git bundle: `/home/romanysrael/battle-of-bluffs-pre-production-20260720-074929.bundle` (local operator recovery asset; not committed).
- Target URLs, server layout, deployment sequence, health checks, and rollback policy are documented in the runbook.

## Server discovery

Read-only audit completed through `ssh romanlms-codex`. No server change was made.

- Host: `romanlms-server`, Ubuntu 26.04 LTS, 6 cores/12 threads.
- Capacity: 7.1 GiB RAM with approximately 5.2 GiB available; 4 GiB swap; root filesystem 109 GiB with 82 GiB free.
- RomanLMS: `romanlms-backend.service` active; internal and public health both returned `UP` before deployment.
- nginx: active, valid configuration; `/etc/nginx/sites-enabled/romanlms` links to `/etc/nginx/sites-available/romanlms` and listens on `127.0.0.1:8081`.
- Cloudflare Tunnel: active, token-managed service; current origin target observed as `http://127.0.0.1:8081`. No tunnel configuration change is required.
- PostgreSQL: 18.4 cluster `18/main`, loopback-only `127.0.0.1:5432`, 100 maximum connections and 21 observed sessions during audit. `battle_of_bluffs_prod` and `gotg_app` did not exist.
- Backups: pgBackRest stanza `romanlms-prod` status `ok`, encrypted repositories, current local/R2/B2 backup chains and WAL archiving. All three backup services last succeeded and their timers remain enabled. Three old failed notification instances remain from 2026-07-17; they do not represent current backup job failure and were not changed or cleared.
- Port: `127.0.0.1:8090` is free.
- Java: OpenJDK 17.0.19 is installed.
- Mail: protected RomanLMS configuration contains a SpaceMail host, authenticated `romanlms.com` mailbox/from address, and password. Existing service uses implicit TLS on 465; GOTG will use SpaceMail's supported 587 authenticated STARTTLS mode with the credential copied securely between separate protected files.
- Runner: existing `romanlms-runner` and `actions.runner.RomanYsrael-cmd-roman-lms.romanlms-prod-01.service` are RomanLMS-only assets and will not be reused or altered. GOTG requires a separate `gotg-runner` under `/opt/gotg/actions-runner`.
- Coexistence note: the existing RomanLMS Java process listens on port 8080; nginx is loopback-only. GOTG must explicitly bind its selected port to `127.0.0.1`.

## Planned production values

- Frontend: `https://bluffs.romanlms.com`
- REST: `https://romanlms.com/bluffs/api`
- WebSocket: `wss://romanlms.com/bluffs/ws`
- Health: `https://romanlms.com/bluffs/health`
- Service: `gotg-backend.service`
- Database/role: `battle_of_bluffs_prod` / `gotg_app`
- Loopback: `127.0.0.1:8090`
- Database pool: minimum idle 1, maximum 8, connection timeout 10 seconds
- SpaceMail: `mail.spacemail.com:587` with authenticated required STARTTLS and a protected existing `romanlms.com` mailbox credential

## Change log

- Read-only production audit only. No production server, Vercel, DNS, nginx, Cloudflare Tunnel, PostgreSQL, SpaceMail, or runner mutation has occurred.
- Repository configuration added for explicit production REST/WebSocket URLs, Vercel SPA routing/security headers, loopback Spring binding, an 8/1 Hikari pool, a hardened systemd unit, narrow nginx locations, atomic backend deployment/rollback, exact CI JAR artifacts, and a dedicated-runner deployment workflow.

## Blockers

None established yet. Credential-dependent items will be marked only after safe discovery.
