# Production deployment runbook

## Architecture and public endpoints

- Frontend: Vercel project `games-of-the-generals`, production origin `https://bluffs.romanlms.com`.
- REST: `https://romanlms.com/bluffs/api`, translated by nginx to the GOTG loopback service's `/api` routes.
- WebSocket: `wss://romanlms.com/bluffs/ws`, translated by nginx to `/ws` with the original browser `Origin` preserved.
- Health: `https://romanlms.com/bluffs/health`, translated only to the minimal Spring Boot health endpoint.
- Backend: Java 17 Spring Boot service `gotg-backend.service` on confirmed-free loopback `127.0.0.1:8090`.
- Database: dedicated PostgreSQL database `battle_of_bluffs_prod` and non-superuser role `gotg_app` in the existing private PostgreSQL 18 cluster.
- Mail: SpaceMail submission over authenticated STARTTLS on port 587 using an already provisioned mailbox or approved alias. Credentials stay only in protected server configuration.

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

The selected database pool is minimum idle 1 and maximum 8. Exact change-time backup paths, JVM limits, runner registration status, and redacted SpaceMail sender status are recorded in [production deployment progress](production-deployment-progress.md).

## Deployment sequence

1. Confirm the merged production commit and all repository gates.
2. Confirm RomanLMS, nginx, Cloudflare Tunnel, PostgreSQL, backups, capacity, and the selected loopback port are healthy.
3. Back up each exact server file immediately before changing it; validate replacements before activation.
4. Provision the isolated GOTG runtime user, directories, database/role, protected environment, systemd unit, and deployment script.
5. Build `backend/` from the exact merged commit with `./mvnw clean verify` and the project security gates.
6. Install the versioned JAR atomically, start only `gotg-backend.service`, and validate internal health and Flyway.
7. Add only the three `/bluffs` locations to the existing nginx configuration, run `nginx -t`, reload nginx, and immediately recheck RomanLMS and GOTG.
8. Deploy `frontend/` to its dedicated Vercel project with only public `VITE_*` production configuration.
9. Attach `bluffs.romanlms.com` using Vercel's reported DNS target and validate DNS, TLS, CSP, CORS, CSRF, cookies, REST, and WebSocket behavior.
10. Run controlled authentication, casual, ranked, persistence, restart, and coexistence smoke tests.
11. Merge the deployment PR, deploy only the merged main commit, and create the production tag only after complete validation.

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
- Database: never run Flyway clean or manually reverse destructive migrations. Preserve forward-compatible migrations and use the existing pgBackRest physical recovery process for disaster recovery. The GOTG database is included in cluster-level backups once created in the protected cluster.

Concrete commands and validated backup paths will be added after server discovery. No rollback step may restart or modify `romanlms-backend.service`.
