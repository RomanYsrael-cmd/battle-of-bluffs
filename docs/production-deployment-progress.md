# Production deployment progress

Updated: 2026-07-20 (Asia/Manila)

## Repository and CI

- PR #3 merged at `02a8212a36f34c8ae4e5e64d5df32ea3b412989c` after every push, pull-request, CodeQL, dependency scan, SBOM, secret scan, frontend, backend, and Playwright check passed.
- The PR #3 dependency-scan failure was NVD bootstrap throttling (HTTP 429), not a vulnerable dependency. Dependency-Check 12.2.2 now uses cached, serialized official NIST feed updates and a fail-closed offline scan. CVSS 7+ still fails CI and scanner errors remain fatal.
- Deployment PR #9 merged at `7e8153432058c9f40fb9284135aee1491daba62c` with Vercel, backend, nginx, systemd, runner workflow, and production routing configuration.
- Health-probe PR #10 merged at `9c2523c2b1129b14e536c40c96ff979c5e27efe4`. It supplies the permitted production Host header to the internal actuator probe so host allowlisting cannot cause a false rollback.
- Vercel routing PR #11 merged at `25c21ebc44295f6e29812c487131ec5fe583f2de`. It uses filesystem-first Vercel rewrites so SPA fallback and security headers both apply.
- Main CI and CodeQL passed for `25c21ebc44295f6e29812c487131ec5fe583f2de`.
- Recovery tag: `backup/pre-production-deploy-20260720-074929`.
- Verified bundle: `/home/romanysrael/battle-of-bluffs-pre-production-20260720-074929.bundle` (local operator asset; not committed).

## Production architecture and current state

- Frontend project: Vercel `games-of-the-generals`, GitHub repository connected, production branch `main`, root directory `frontend`.
- Ready Vercel production deployment: `dpl_ENorJRP3ZuhTGXLB2d82Q38NLtbC`, built from `25c21ebc44295f6e29812c487131ec5fe583f2de`.
- Stable generated alias: `https://games-of-the-generals-phi.vercel.app`.
- Production frontend origin: `https://bluffs.romanlms.com`; Cloudflare DNS is DNS-only at `76.76.21.21`, Vercel reports the production deployment Ready, and HTTPS is active.
- REST: `https://romanlms.com/bluffs/api`.
- WebSocket: `wss://romanlms.com/bluffs/ws`.
- Minimal health: `https://romanlms.com/bluffs/health`.
- Backend: `gotg-backend.service`, running as `gotg:gotg` on loopback `127.0.0.1:8090`.
- Current backend release: `/opt/gotg/releases/dcaea23c37932f7e38608b70485b5a6369b9a2cc/gotg-backend.jar`, deployed from the exact successful main artifact by the dedicated runner.
- Database: `battle_of_bluffs_prod`; bounded non-superuser role: `gotg_app`; Hikari minimum 1, maximum 8, 10-second connection timeout.
- Mail: SpaceMail `mail.spacemail.com:587`, authenticated STARTTLS, protected `romanlms.com` sender credential.

## Server changes

- Created isolated runtime account `gotg` and `/opt/gotg/{app,releases,config,backups,scripts}` with restrictive ownership.
- Created `battle_of_bluffs_prod` and `gotg_app`; the role has no superuser, createdb, createrole, or replication rights and has a connection limit of 10.
- Generated the database password on the server and stored it only in `/opt/gotg/config/gotg.env` (`root:gotg`, `0640`). SpaceMail credentials were copied between protected files without printing them.
- Installed `/etc/systemd/system/gotg-backend.service` and `/usr/local/bin/gotg-deploy-backend`. The service has a 1 GiB memory ceiling, 512-task ceiling, read-only application tree, no new privileges, private temporary/device namespaces, and conservative JVM settings.
- Installed the current exact main artifact atomically. Prior releases `7e8153432058c9f40fb9284135aee1491daba62c` and `9c2523c2b1129b14e536c40c96ff979c5e27efe4` remain available for rollback.
- Added only `/bluffs/api/`, `/bluffs/ws`, and `/bluffs/health` to `/etc/nginx/sites-available/romanlms`. Backup: `/opt/gotg/backups/romanlms.nginx.20260720-082258.conf`.
- Corrected deploy-helper backup: `/opt/gotg/backups/gotg-deploy-backend.20260720-082735`.
- No Cloudflare Tunnel configuration or token changed. nginx continues to listen on `127.0.0.1:8081` behind the existing tunnel.
- Installed official GitHub Actions runner 2.335.1 at `/opt/gotg-runner` as `gotg-runner`; its published SHA-256 was verified, it cannot read `/opt/gotg/config/gotg.env`, and its only sudo permission is the fixed root-owned GOTG deployment helper. Repository runner `gotg-production-01` is online with the dedicated `gotg-production` label. The stale queued deployment was cancelled before registration; exact-main run `29710691020` then deployed successfully.
- The existing RomanLMS runner and service were not altered.

## Validation completed

- Exact main backend: 421 tests passed, one Docker-only test skipped locally; Maven verify, dependency declaration analysis, OWASP Dependency-Check, and CycloneDX XML/JSON SBOM generation passed.
- Frontend: 97 tests passed; `npm ci` reported zero vulnerabilities; the production build passed.
- Playwright: all five isolated local platform flows passed in 1.0 minute after explicitly routing Vite's development proxy and browser runtime to the same temporary backend. Push and pull-request E2E checks also passed.
- Vercel: all required deep links return 200; static hashed assets return 200; CSP, HSTS, nosniff, frame denial, referrer, and permissions headers are present; required REST/WebSocket URLs are embedded; server-secret markers and source maps are absent.
- Flyway applied and validated only version 1 in `battle_of_bluffs_prod`. Flyway clean and Hibernate mutation remain disabled.
- SpaceMail authentication succeeded with TLS 1.3 and `TLS_AES_256_GCM_SHA384`; the From domain is `romanlms.com`. Real verification and password-reset messages reached the explicitly controlled Gmail address, and their links used `https://bluffs.romanlms.com`.
- Exact-origin CORS preflight succeeds for `https://bluffs.romanlms.com`; hostile-origin preflight returns 403. Unauthorized REST returns 401 and `/api/dev/**` is absent.
- Public and internal GOTG health return `UP`. Public and internal RomanLMS health return `UP`; the RomanLMS homepage returns 200.
- `gotg-backend.service`, `romanlms-backend.service`, nginx, cloudflared, and PostgreSQL are active. GOTG uses approximately 388 MiB under its 1 GiB ceiling; server memory, disk, and PostgreSQL connection headroom remain safe.
- RomanLMS has not restarted during this deployment (`ActiveEnterTimestamp` remains 2026-07-18 06:22:37 PST, `NRestarts=0`).
- RomanLMS local, R2, and B2 backup services most recently completed successfully and their timers remain unchanged. The separate GOTG database is covered by the existing physical PostgreSQL backup design.
- A non-destructive rollback dry run resolved the prior JAR and validated the saved nginx configuration with `nginx -t`; no live rollback or RomanLMS restart was performed.
- No secret value was printed or committed. The generated local Vercel OIDC env file was deleted after linking.

## Production validation completed

- Authoritative and public DNS return only `76.76.21.21`; the custom domain serves HTTP 200 with a valid certificate for `bluffs.romanlms.com`.
- The home page and production deep links for authentication, lobby, profile, leaderboard, and game routes reload with HTTP 200. The public bundle contains only the intended REST and WebSocket endpoints, has no server-secret markers or source maps, and retains the production security headers.
- Three controlled production accounts registered. Real verification delivery was proven and the primary and Alpha accounts activated. Browser login passed for all three accounts; logout invalidated the primary session and a fresh login succeeded.
- Forgot-password delivery and the Vercel reset URL were proven. The fresh reset token succeeded, invalidated the existing session and old password, and allowed login only with the new password. No active reset token remains.
- Authenticated WebSocket synchronization, casual room creation/join, both formations, chat, a legal move, resignation, terminal disclosure, and history passed through the production URLs.
- Ranked matchmaking paired the two verified accounts. PostgreSQL contains exactly two rating-change rows for the single ranked match, one per participant, with no duplicates; each player has exactly one rated game and the leaderboard/profile views updated.
- Outsider REST and WebSocket access failed, active opponent ranks were absent from REST, DOM, and accessibility output, and hostile chat remained inert text.
- Abandoned-room recovery, cancellation, guest replacement, and active-game recovery passed. A coordinated test restarted only `gotg-backend.service`, re-authenticated both players, recovered the exact persisted open match in `FORMATION`, and cleaned it up normally.
- RomanLMS remained HTTP 200/`UP` throughout. `romanlms-backend.service` retained its original `2026-07-18 06:22:37 PST` activation timestamp and `NRestarts=0`; its runner, backups, database contents, service, and deployment path were not changed.

## Release status

- No external production blocker remains.
- All validation gates for annotated release tag `v0.3.0-production-alpha` have passed. Create and push the tag only from the final merged documentation commit after its CI and exact-main deployment succeed.
