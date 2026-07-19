# Production security audit

Audit date: 2026-07-19  
Scope: commit `b47f1f5` and the local `security/production-hardening` branch  
Conclusion wording: **Production security audit completed with documented residual risks.** This is not a certification or a guarantee of security.

## Threat model

The trust boundary is the Spring backend and PostgreSQL database. The React browser, REST/STOMP frames, proxy headers, match identifiers and all user-controlled text are untrusted. Threat actors include unauthenticated internet users, malicious authenticated users and opponents, account-takeover attempts, bots, forged/stale/duplicated/reordered REST or STOMP commands, users inspecting browser/network state, attackers controlling forwarded headers, injection attempts against database/HTML/log/email/command contexts, malicious dependencies, and accidental deployment misconfiguration.

Protected assets are passwords and accounts, email/profile data, verification/reset tokens, sessions and CSRF tokens, formations and hidden ranks, match commands/results, ratings/leaderboards, chat/report evidence, database integrity, availability, and production secrets.

## Baseline and method

- Backend: 405 tests discovered; 404 passed and 1 Docker-dependent Testcontainers test was skipped because this host cannot access the Docker socket.
- Frontend: 15 files / 92 tests passed; TypeScript/Vite production build passed.
- Playwright: baseline failed before edits because no local application stack was running; the first test timed out waiting for the registration form and four tests did not run.
- `npm ci` reported zero vulnerabilities. `git diff --check` and the initial worktree were clean.
- Reviewed all tracked source/configuration, the single Flyway migration, controllers/services/repositories, security/STOMP configuration, CI, Compose, browser storage and HTTP/STOMP clients.
- Secret search covered the current tree and full Git patch history using `rg`/Git. `gitleaks` and `osv-scanner` were not installed. Matches were development/test credentials and token-shaped test fixtures; no real credential was identified.

## Findings

### SEC-001 — Production inherited development credentials and insecure fallbacks

- Affected component: Spring production configuration
- Severity / likelihood / impact: **HIGH** / likely deployment error / database compromise, token email interception, or session theft
- Attack scenario: an operator starts the default configuration in production and silently uses a known database password, Mailpit/local SMTP, HTTP links, or an insecure cookie.
- Evidence: `application.yml` supplied defaults for database password, SMTP, frontend URL and `SESSION_COOKIE_SECURE=false`; there was no production profile.
- Affected files: `backend/src/main/resources/application.yml`, `application-prod.yml`, `ProductionConfigurationValidator.java`
- Remediation: added a fail-closed `prod` profile with required external variables, HTTPS URL validation, mandatory Secure/Strict cookie, required SMTP STARTTLS, bounded pool, safe error settings, Flyway validation and incompatible-profile rejection.
- Regression tests: `ProductionConfigurationValidatorTest`; missing-variable startup check.
- Status: **FIXED**
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-002 — Debug request/response logging disclosed credentials and private game data

- Affected component: MVC/STOMP DTO logging
- Severity / likelihood / impact: **HIGH** / realistic during troubleshooting / passwords, reset tokens, formations, ranks, chat or reports enter logs
- Attack scenario: an operator enables Spring web DEBUG logging and request/response processors invoke record `toString()` on sensitive DTOs.
- Evidence: baseline logs rendered `LoginRequest[..., password=...]`, formation requests, and complete `PlayerMatchView` values.
- Affected files: account, profile, match, chat and report DTOs
- Remediation: sensitive DTO string representations now expose only safe metadata and `REDACTED`; production web/SQL logging remains restricted.
- Regression tests: `AccountApiControllerTest.credentialAndTokenDtosNeverRenderSecretsIntoLogs`, `MatchUpdateEnvelopeSerializationTest`.
- Status: **FIXED**
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-003 — Browser origin and response-header policy was incomplete

- Affected component: Spring Security HTTP boundary
- Severity / likelihood / impact: **MEDIUM** / plausible / cross-origin misuse and increased XSS/clickjacking impact
- Attack scenario: an untrusted site attempts credentialed API requests, framing, or content injection against a browser session.
- Evidence: no explicit CORS configuration, CSP, Referrer-Policy or Permissions-Policy.
- Affected files: `SecurityConfig.java`
- Remediation: exact configured origin, credentialed CORS without wildcard, CSP with no `unsafe-eval`, frame denial, no-referrer, feature restrictions and existing nosniff/HSTS behavior. Forwarded headers remain disabled unless deployment is deliberately changed.
- Regression tests: `AccountApiControllerTest.corsAllowsOnlyTheConfiguredFrontendOriginWithCredentials` and header assertions.
- Status: **FIXED**
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-004 — Abuse-key maps could grow past their intended bound

- Affected component: authentication and chat throttling
- Severity / likelihood / impact: **MEDIUM** / realistic automated abuse / heap exhaustion and credential stuffing
- Attack scenario: many distinct peer/key combinations keep adding live buckets after the map exceeds 10,000 entries.
- Evidence: cleanup only removed expired entries and never rejected insertion while all entries were live.
- Affected files: `AccountRateLimiter.java`, `MatchChatService.java`, `AccountApiController.java`
- Remediation: hard 10,000-key caps with expired-key cleanup and fail-closed behavior; login is limited by socket peer and normalized account identity; peer identity ignores forwarded headers.
- Regression tests: `AccountRateLimiterTest.attackerControlledKeysCannotGrowTheLimiterPastItsHardBound`, forwarded-header test.
- Status: **FIXED**
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-005 — Production session policy lacked an absolute lifetime

- Affected component: authenticated HTTP sessions
- Severity / likelihood / impact: **MEDIUM** / plausible / long-lived stolen session
- Attack scenario: continued activity preserves a compromised session indefinitely within the previous 12-hour idle window.
- Evidence: idle timeout only; no absolute-age enforcement.
- Affected files: `AbsoluteSessionLifetimeFilter.java`, `SecurityConfig.java`, profile configuration
- Remediation: 30-minute production idle timeout, 12-hour absolute lifetime, five-session maximum, rotation at login, reset/logout invalidation, and `__Host-GOTGSESSION` Secure/HttpOnly/SameSite=Strict cookie.
- Regression tests: `AbsoluteSessionLifetimeFilterTest`, security MVC/session tests and production validator tests.
- Status: **FIXED**
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-006 — Reset/verification secrets remained in the address bar and expired sessions retained caches

- Affected component: React authentication state
- Severity / likelihood / impact: **MEDIUM** / plausible on shared devices or screenshots / token or prior-account data exposure
- Attack scenario: browser history retains an account token, or a 401 leaves match/query state available to the next user.
- Evidence: screens read tokens directly from `useSearchParams`; 401 only displayed a banner.
- Affected files: `AuthScreens.tsx`, `http.ts`, `App.tsx`
- Remediation: capture tokens in component memory and immediately replace the URL; clear CSRF, navigation state and React Query caches on session expiration, then route to login.
- Regression tests: `AuthScreens.security.test.tsx` and existing logout/cache tests.
- Status: **FIXED**
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-007 — WebSocket transport had no explicit resource ceilings

- Affected component: STOMP/WebSocket transport
- Severity / likelihood / impact: **MEDIUM** / plausible / memory and connection-slot exhaustion
- Attack scenario: authenticated clients send oversized frames or hold incomplete connections.
- Evidence: broker heartbeat existed, but transport message/buffer/send/first-message limits were defaults.
- Affected files: `WebSocketConfig.java`
- Remediation: 16 KiB messages, 64 KiB send buffers, 10-second send limit and 15-second first-message deadline. Existing interceptor restricts every SUBSCRIBE/SEND and revalidates account status/membership.
- Regression tests: existing `MatchSubscriptionInterceptorTest`; configuration compiles in the full context.
- Status: **FIXED**
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-008 — Extreme valid page numbers could overflow multiplication

- Affected component: history and leaderboard pagination
- Severity / likelihood / impact: **LOW** / easy / avoidable 500 response
- Attack scenario: `page=2147483647&size=100` wraps `page * size` negative.
- Evidence: integer multiplication preceded bounds clamping.
- Affected files: `ProfileService.java`, `LeaderboardService.java`
- Remediation: perform offset multiplication as `long` before clamping.
- Regression tests: service suites and full backend suite.
- Status: **FIXED**
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-009 — Supply-chain workflow references were mutable

- Affected component: GitHub Actions and dependency maintenance
- Severity / likelihood / impact: **MEDIUM** / uncommon but material / CI code execution and artifact compromise
- Attack scenario: a compromised mutable action tag changes code executed by CI.
- Evidence: all actions used `@v4`; no Dependabot or CodeQL configuration; workflow permissions were implicit.
- Affected files: `.github/workflows/*.yml`, `.github/dependabot.yml`
- Remediation: action SHAs resolved from upstream repositories and pinned, least-privilege permissions, CodeQL, weekly ecosystem updates, production npm audit, Maven dependency analysis, and seven-day failure-artifact retention.
- Regression tests: YAML review; local npm audit and Maven dependency analysis.
- Status: **FIXED**
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-010 — Development infrastructure listened on every interface

- Affected component: local Compose PostgreSQL and Mailpit
- Severity / likelihood / impact: **MEDIUM** / realistic on shared networks / database or email capture access
- Attack scenario: another host reaches ports 5432, 1025 or 8025 while local development is running with known credentials.
- Evidence: Compose used unrestricted host port bindings.
- Affected files: `infra/compose.yaml`, `.env.example`
- Remediation: bind development services to `127.0.0.1`, identify the stack as development-only, and pin PostgreSQL to 18.4.
- Regression tests: Compose configuration validation when Docker access is available.
- Status: **FIXED**
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-011 — Single-instance and endpoint-specific abuse controls remain limited

- Affected component: rate limits, matchmaking queue, sessions and broker
- Severity / likelihood / impact: **MEDIUM** / plausible / availability degradation across replicas or high-cost authenticated traffic
- Attack scenario: an attacker distributes traffic across application instances or heavily exercises endpoints without a dedicated limit.
- Evidence: counters and queue are process-local; not every read/write endpoint has a separate quota.
- Remediation: current critical authentication/chat maps are bounded; HTTP/header and STOMP message ceilings were added. Enforce proxy/WAF connection and request limits and deploy one application instance until shared state is designed.
- Regression tests: bounded limiter, chat limits, STOMP authorization and transport configuration.
- Status: **ACCEPTED RISK** — distributed enforcement would require product/deployment redesign explicitly excluded from this pass.

### SEC-012 — CSP retains a narrow inline-style allowance

- Affected component: frontend CSP
- Severity / likelihood / impact: **LOW** / low / reduced style-injection defense
- Attack scenario: an independent HTML injection bug gains style capabilities, though scripts and event handlers remain blocked.
- Evidence: one React progress indicator uses an inline dynamic width.
- Remediation: CSP forbids inline scripts and `unsafe-eval`; `style-src 'unsafe-inline'` is retained for current rendering compatibility.
- Status: **ACCEPTED RISK** — remove after migrating dynamic styles to nonce/hash-compatible CSS.

### SEC-013 — Secret scanner coverage is tool-limited locally

- Affected component: repository/history scanning
- Severity / likelihood / impact: **INFORMATIONAL** / low / a non-obvious encoded secret could be missed
- Evidence: `gitleaks`/`osv-scanner` were unavailable; Git/history pattern searches found only obvious development/test values.
- Remediation: pinned Gitleaks now scans full history in CI; rotate immediately if a real secret is ever identified. Do not rewrite history without explicit approval.
- Status: **FIXED** — local execution remains unavailable, with CI coverage added
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-014 — Outsiders could retrieve summaries for active match-history identifiers

- Affected component: match history authorization
- Severity / likelihood / impact: **MEDIUM** / requires a leaked high-entropy match UUID / active room metadata disclosure
- Attack scenario: an authenticated outsider obtains an active match ID and queries its history endpoint, learning that it exists and its phase/mode/timestamps even though no private player view was returned.
- Evidence: `ProfileService.history` returned `PublicMatchSummary` for nonparticipants regardless of phase.
- Affected files: `ProfileService.java`, `ProfileHistoryAuthorizationTest.java`
- Remediation: nonparticipants now receive the same not-found response for every nonterminal match; terminal public summaries remain available without private piece projections.
- Regression tests: `ProfileHistoryAuthorizationTest.activeMatchHistoryGivesParticipantSafeViewAndHidesExistenceFromOutsider`.
- Status: **FIXED**
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-015 — Requests lacked a safe end-to-end correlation identifier

- Affected component: HTTP observability and incident response
- Severity / likelihood / impact: **LOW** / common operational condition / delayed abuse investigation and event reconstruction
- Attack scenario: high-volume or distributed abuse produces interleaved events that cannot be reliably tied back to the triggering HTTP request.
- Evidence: no request correlation filter or response identifier; accepting an arbitrary raw identifier would itself permit log injection or excessive log values.
- Affected files: `RequestCorrelationFilter.java`, `SecurityConfig.java`, `application-prod.yml`
- Remediation: accept only a 1–64 character conservative identifier alphabet, generate a UUID otherwise, return it in `X-Request-ID`, scope it in MDC with guaranteed cleanup, and include it in the production log-level pattern.
- Regression tests: `RequestCorrelationFilterTest` covers safe propagation, unsafe replacement and MDC cleanup.
- Status: **FIXED**
- Correction commit: `34f6311` (`security: harden production boundaries`)

### SEC-016 — JSON transport size depends on the production edge

- Affected component: HTTP request-body availability controls
- Severity / likelihood / impact: **LOW** / plausible / excess bandwidth, parsing work or connection occupancy
- Attack scenario: a client streams an oversized or chunked JSON request before DTO length validation rejects its fields.
- Evidence: Tomcat form/swallow limits do not provide a universal pre-parser ceiling for every JSON transfer mode; application DTOs and WebSocket transport do have explicit field/message bounds.
- Remediation: the deployment runbook requires a 1 MiB proxy body limit plus connection/rate ceilings; backend DTO validation remains defense in depth.
- Status: **ACCEPTED RISK** — a universal servlet wrapper would add buffering/streaming complexity and the trusted production reverse proxy is the correct early-rejection boundary.

### SEC-017 — Development and CI container images use version tags rather than digests

- Affected component: Compose and GitHub Actions service containers
- Severity / likelihood / impact: **LOW** / uncommon / upstream tag replacement could change executed container content
- Attack scenario: a registry tag is maliciously or accidentally republished after review and a later development or CI pull executes different bytes.
- Evidence: PostgreSQL is pinned to `18.4` and Mailpit to `v1.30.4`, but neither reference includes a platform-specific digest; this host cannot query the Docker socket to verify the locally selected architecture manifest.
- Remediation: explicit versions replace broad floating tags and Dependabot monitors Docker references. Resolve and review the correct multi-architecture digest in the deployment/CI environment during the next image update.
- Status: **ACCEPTED RISK** — inserting an unverified architecture digest here could break CI and would provide false assurance.

### SEC-018 — Managed runtime dependencies contained published 2026 vulnerabilities

- Affected component: embedded Tomcat, PostgreSQL JDBC and Log4j API runtime dependencies
- Severity / likelihood / impact: **HIGH** / configuration-dependent / database channel-binding downgrade or container security-control defects
- Attack scenario: a network-positioned attacker downgrades a PostgreSQL connection configured with `channelBinding=require`, or a deployment later enables an affected Tomcat connector/rewrite feature. Log4j JSON `MapMessage` output could also be malformed by a non-finite attacker-controlled value, although this application does not configure that layout.
- Evidence: OWASP Dependency-Check 12.2.2 identified PostgreSQL JDBC 42.7.11 (CVE-2026-54291), Tomcat Embed 11.0.22 (multiple CVE-2026 advisories), and Log4j API 2.25.4 (CVE-2026-49844). The pgJDBC vendor rates its issue High; Apache rates the applicable Tomcat 11.0.22/11.0.23 issues Low, while NVD scores several more aggressively.
- Affected files: `backend/pom.xml`
- Remediation: override Spring Boot's managed versions to pgJDBC 42.7.12, Tomcat 11.0.24 and Log4j 2.25.5, the upstream fixed releases, without changing framework APIs.
- Regression tests: full backend suite and a repeated OWASP Dependency-Check scan with a CVSS 7 failure gate.
- Status: **FIXED**
- Correction commit: pending final dependency verification checkpoint

## Verified existing controls

CSRF covers normal state-changing endpoints; JSON is not exempt. Development match routes require the explicit `dev` profile. Authentication derives identity from the server session. Token values use 256-bit randomness, SHA-256 storage, expiry, one-time state, replacement invalidation and pessimistic write locking. Suspended/deleted status is rechecked for HTTP and STOMP. Match services derive player identity at controllers/service boundaries, check participant membership, enforce expected versions/idempotency and publish player-specific views. Active opponent ranks and authoritative identifiers remain absent; terminal disclosure is participant-only. Chat is bounded, paginated and React-rendered as text. Rating changes have per-match/user uniqueness and transactional exactly-once tests. Flyway is authoritative and Hibernate mutation is disabled. Actuator production exposure is health-only with details hidden.

## Residual deployment obligations

Use HTTPS, an explicit trusted reverse proxy, database TLS/least privilege/timeouts, protected encrypted backups, centralized sanitized logs/alerts, external connection/request limits, secret rotation, and vulnerability response. Do not expose the development Compose stack. Production database/content encryption at rest, distributed throttling/session storage, formal penetration testing and disaster-recovery exercises remain operator responsibilities.

## Hardened verification results

- Backend after hardening: 421 tests discovered; 420 passed and the Docker-socket-dependent Testcontainers migration test was skipped.
- Frontend after hardening: 16 files / 94 tests passed; the production build passed; full and production-only npm audits reported zero vulnerabilities.
- Browser: all five final isolated Playwright journeys passed in 1.4 minutes against PostgreSQL 18.4, native Mailpit, the hardened backend and a dedicated Vite port. This covers independent accounts, lobby lifecycle, formation/chat/move/terminal history, ranked exactly-once rating, outsider REST/STOMP denial, rank secrecy and inert hostile chat.
- Production profile: failed closed with missing environment; started with isolated safe test values; Flyway validated schema version 1. `Set-Cookie` used `__Host-GOTGSESSION; Path=/; Secure; HttpOnly; SameSite=Strict`; CSP/nosniff/no-referrer/Permissions-Policy were present; hostile Host returned 400; hostile Origin and missing CSRF returned 403; `/api/dev/**` and `/actuator/info` returned 404. HSTS was correctly absent from the plain-HTTP local smoke test and is emitted only for secure requests. HTTP correlation IDs are validated/generated and included in production logging context.
- Supply chain: Maven dependency analysis succeeded with known Spring Boot starter-aggregation warnings; npm audit was clean. OWASP Dependency-Check initially blocked on the published pgJDBC/Tomcat/Log4j issues in SEC-018, then reported zero vulnerable dependencies after the fixed-version overrides. CI now pins actions, runs Gitleaks, CodeQL and a CVSS 7 backend dependency gate, audits production npm dependencies, and generates Maven/npm CycloneDX SBOM artifacts.
- All temporary backend/frontend/Mailpit processes started by this audit were stopped. Pre-existing workspace services on ports 8080/5173 were not altered.
