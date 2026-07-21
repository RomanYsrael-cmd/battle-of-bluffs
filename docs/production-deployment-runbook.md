# Production deployment runbook

## Architecture and public endpoints

- Frontend: Vercel project `games-of-the-generals`, production origin `https://bluffs.romanlms.com`.
- REST: `https://romanlms.com/bluffs/api`, translated by nginx to the GOTG loopback service's `/api` routes.
- WebSocket: `wss://romanlms.com/bluffs/ws`, translated by nginx to `/ws` with the original browser `Origin` preserved.
- Health: `https://romanlms.com/bluffs/health`, translated only to the minimal Spring Boot health endpoint.
- Backend: Java 17 Spring Boot service `gotg-backend.service` on confirmed-free loopback `127.0.0.1:8090`.
- Database: dedicated PostgreSQL database `battle_of_bluffs_prod` and non-superuser role `gotg_app` in the existing private PostgreSQL 18 cluster.
- Mail: SpaceMail submission over authenticated STARTTLS on port 587 using an already provisioned mailbox or approved alias. Credentials stay only in protected server configuration.
- Optional media: dedicated LiveKit Cloud project; its URL is public, while its API key/secret stay only in protected backend configuration.

## Server layout

- Runtime root: `/opt/gotg`
- Stable artifact: `/opt/gotg/app/gotg-backend.jar`
- Versioned releases: `/opt/gotg/releases/<git-sha>/`
- Protected configuration: `/opt/gotg/config/gotg.env` (`root:gotg`, mode `0640`)
- Operational backups: `/opt/gotg/backups/`
- Application scripts: `/opt/gotg/scripts/`
- Atomic deploy entrypoint: `/usr/local/bin/gotg-deploy-backend`
- Runtime identity: `gotg:gotg`
- CI identity: `gotg-runner`, with sudo restricted to the fixed GOTG deployment entrypoint
- Dedicated runner home: `/opt/gotg-runner` (kept outside `/opt/gotg` so it cannot traverse the protected configuration tree)

The selected database pool is minimum idle 1 and maximum 8. Exact change-time backup paths, JVM limits, runner registration status, and redacted SpaceMail sender status are recorded in [production deployment progress](production-deployment-progress.md).

## Deployment sequence

1. Confirm the merged production commit and all repository gates.
2. Confirm RomanLMS, nginx, Cloudflare Tunnel, PostgreSQL, backups, capacity, and the selected loopback port are healthy.
3. Back up each exact server file immediately before changing it; validate replacements before activation.
4. Provision the isolated GOTG runtime user, directories, database/role, protected environment, systemd unit, and deployment script.
5. Build `backend/` from the exact merged commit with `./mvnw clean verify` and the project security gates.
6. Install the versioned JAR atomically, start only `gotg-backend.service`, and validate internal health and Flyway.
7. Add only the three `/bluffs` locations to the existing nginx configuration, run `nginx -t`, reload nginx, and immediately recheck RomanLMS and GOTG.
8. If media is enabled, configure the dedicated LiveKit Cloud project, server-only backend credentials, public `VITE_LIVEKIT_URL`, and the exact project origins in Vercel CSP as specified in [audio-video.md](audio-video.md). Confirm a missing credential fails closed.
9. Deploy `frontend/` to its dedicated Vercel project with only public `VITE_*` production configuration. For frontend-only releases, confirm the merged diff contains no `backend/` or `ops/gotg-deploy-backend` change; the production workflow intentionally skips the backend artifact and service restart in that case.
10. Attach `bluffs.romanlms.com` using Vercel's reported DNS target and validate DNS, TLS, CSP, CORS, CSRF, cookies, REST, gameplay WebSocket, and optional media behavior.
11. Run controlled authentication, casual, ranked, persistence, restart, coexistence, and—when enabled—two-browser media isolation smoke tests. For desktop-workspace changes, also validate 1366×768 and 1920×1080 at 100% zoom: the document must not scroll, the full board/timers/captures/actions must remain visible, and expanded media/chat must stay inside the utility dock. Recheck the stacked layout below 1024px.
12. Merge the deployment PR, deploy only the merged main commit, and create the production tag only after complete validation.

## Health checks

- RomanLMS homepage and `/lms/actuator/health` remain healthy before and after every shared-infrastructure change.
- `systemctl is-active gotg-backend.service` is `active`.
- Internal GOTG health responds on the selected loopback port.
- Public `https://romanlms.com/bluffs/health` returns only minimal health data.
- nginx configuration validates and Cloudflare Tunnel remains healthy.
- PostgreSQL remains private with safe connection, memory, and disk headroom.
- Public frontend deep links reload without redirect loops or mixed content.

## Rollback

- Backend: atomically repoint the stable artifact to the prior versioned release, restart only `gotg-backend.service`, then verify internal/public GOTG health and RomanLMS health.
- nginx: restore the timestamped backup of the exact changed file, run `sudo nginx -t`, reload nginx, and verify RomanLMS immediately.
- Frontend: promote the prior healthy Vercel production deployment, then recheck the custom domain and deep links.
- Media: set `MEDIA_ENABLED=false`, restart only `gotg-backend.service`, and promote the prior frontend deployment. Gameplay requires no media fallback and must remain healthy.
- Database: never run Flyway clean or manually reverse destructive migrations. Preserve forward-compatible migrations and use the existing pgBackRest physical recovery process for disaster recovery. The GOTG database is included in cluster-level backups once created in the protected cluster.

Validated rollback assets:

- Previous backend releases remain under `/opt/gotg/releases/<git-sha>/`; use the root-owned atomic deploy entrypoint and verify internal/public health.
- nginx backup: `/opt/gotg/backups/romanlms.nginx.20260720-082258.conf`.
- Deploy-helper backup: `/opt/gotg/backups/gotg-deploy-backend.20260720-082735`.
- Prior healthy Vercel deployment: `dpl_5528qJaYsYoX1mxzwLft39um9eds`; use Vercel rollback/promotion only after inspecting the target.
- Pre-media environment backup: `/opt/gotg/backups/gotg.env.pre-livekit.20260721-040045` (root-only, content verification performed without display).
- Pre-media backend backup: `/opt/gotg/backups/gotg-backend.pre-livekit.20260721-040045.jar`.
- Pre-media Git tag and verified bundle: `backup/pre-livekit-production-20260721-034720` and `/home/romanysrael/battle-of-bluffs-pre-livekit-production-20260721-034720.bundle`.
- Immediately preceding media frontend rollback deployment: `dpl_H2nvmPb8iif4s4nD13GkjyS9tpyF`; inspect before promotion because later healthy deployments also remain available.

The non-destructive dry run validated the prior JAR target and saved nginx file without changing the live symlink or reloading nginx. No rollback step may restart or modify `romanlms-backend.service`.

## Custom-domain activation

Vercel reported the exact required record as `A bluffs.romanlms.com 76.76.21.21`. Add only that DNS-only Cloudflare record. Do not alter the apex, MX, SPF, DKIM, DMARC, nameservers, tunnel records, or Cloudflare Tunnel token. Then run Vercel domain verification and validate DNS, TLS, headers, deep links, and browser API/WebSocket access before treating the custom domain as active.

## Dedicated runner

Runner 2.335.1 is registered as repository runner `gotg-production-01` from `/opt/gotg-runner` under the isolated `gotg-runner` account. Its service is `actions.runner.RomanYsrael-cmd-battle-of-bluffs.gotg-production-01.service`, and workflows must require `[self-hosted, linux, x64, gotg-production]`.

The runner cannot traverse or read `/opt/gotg/config/gotg.env`. `/etc/sudoers.d/gotg-runner` permits only `/usr/local/bin/gotg-deploy-backend`; it does not grant shell access, RomanLMS deployment access, or unrestricted sudo. The existing RomanLMS runner remains separate and unchanged.

Before runner maintenance or re-registration, check GitHub Status, cancel any stale queued deployment, verify the intended exact-main SHA, and keep the manual atomic deployment path operational. Never place a registration token in a command argument, shell history, Git, or logs.
