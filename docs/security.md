# Security

## Enforced boundaries

- Server sessions and HTTP-only SameSite cookies; no browser-stored bearer token
- Session fixation migration, CSRF on state changes, logout/password-reset invalidation
- Delegating password encoding, normalized identity uniqueness and generic login/forgot responses
- Hashed, random, expiring, one-time verification/reset secrets; raw tokens are never logged or stored
- Configurable registration, login, reset and chat limits
- Active account checks for HTTP and every STOMP send/subscription
- Session-derived account UUID on all normal match, chat, profile and matchmaking operations
- Match membership checks on view, command, history, chat and STOMP destinations
- Server-generated opaque public opponent-piece UUIDs distinct from submitted/internal IDs
- Separate player view mapping; active opponent ranks never enter REST or STOMP payloads
- Terminal disclosure only through an authorized participant view; outsiders receive result metadata only
- Plain-text chat persistence and React text-node rendering; no HTML interpretation
- Bidirectional chat/join blocking and server-derived report opponent/evidence
- Exactly-once rating ledger with database uniqueness and transactional pair updates
- Suspended/deleted accounts excluded from leaderboards

Security does not depend on disabled controls or hidden DOM. Clients cannot claim player ID, result, rank reveal, clock, rating or battle outcome.

## Error and logging policy

API errors use a stable code, safe message and timestamp. Authentication failures do not distinguish username, email, status or password. Unexpected stack details are not returned. Each HTTP response carries a sanitized or generated `X-Request-ID`; production log levels include the same correlation value. Application logs identify persisted match/message/account UUIDs only where operationally necessary; verification/reset secrets, passwords, cookies and CSRF values are never logged by application code.

`.env` is ignored. `.env.example` contains development placeholders only. Dependency manifests contain no credentials. SMTP and datasource values come from environment-backed configuration.

## Tested attacks

Automated tests prove an outsider cannot read or command another match, occupy both seats, submit another formation, subscribe/send to another match, read private chat or bypass block delivery. They also prove active views/events hide ranks and translate hostile submitted piece labels to random stable UUIDs, suspended principals are denied, unverified players cannot queue ranked, CSRF is required, chat is limited/inert, and rating reconciliation cannot apply twice.

The Playwright security flow repeats outsider REST/STOMP denial in a real browser, inspects an active API response for missing opponent `rank`, checks all opponent DOM pieces remain generic, and sends hostile markup to confirm it remains text.

## Limitations

Rate limits, WebSocket broker and ranked queue are single-instance controls. They are appropriate for this milestone, not a claim of horizontally scaled abuse resistance. There is no administrator console, external identity provider, production secrets manager, WAF, Redis or media channel.

## Production controls and verification

The `prod` profile is fail-closed: database/SMTP credentials, sender and the exact HTTPS frontend origin are mandatory; Secure `__Host-GOTGSESSION` cookies, SMTP STARTTLS, Flyway validation, safe errors and health-only Actuator exposure cannot inherit development defaults. Forwarded headers are disabled. Deployment, proxy, database, backup and rotation requirements are in [production-deployment-security.md](production-deployment-security.md).

Responses set CSP, frame restrictions, nosniff, no-referrer and a restrictive Permissions-Policy. HSTS applies on secure requests, not ordinary HTTP development. CSP permits inline styles for one current dynamic progress indicator, but never inline scripts or `unsafe-eval`.

```bash
cd backend && ./mvnw test && ./mvnw dependency:analyze
cd ../frontend && npm ci && npm test -- --run && npm run build && npm run test:e2e
npm audit --omit=dev --audit-level=high
cd .. && git diff --check
```

See [security-audit-production.md](security-audit-production.md) for evidence and residual risk. This project does not claim formal certification or guaranteed security.
