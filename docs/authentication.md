# Authentication

## Browser session

Authentication uses Spring Security's server-side `JSESSIONID` session. The cookie is HTTP-only, SameSite=Lax and configurable as Secure with `SESSION_COOKIE_SECURE=true`. Login migrates the session ID to prevent fixation. Logout invalidates the session, clears authentication and deletes the cookie. A successful password reset expires every registered live session for that account.

Production instead uses host-only `__Host-GOTGSESSION` with Secure, HttpOnly, SameSite=Strict and Path `/`, a 30-minute idle timeout and a 12-hour absolute lifetime. It has no Domain attribute and never enters URLs or browser storage. Development retains `JSESSIONID`, SameSite=Lax and an explicitly insecure local cookie. At most five sessions are registered per account; account status is revalidated at HTTP and protected STOMP boundaries.

Passwords are validated as 12–72 characters with upper case, lower case, a number and a symbol, then stored with Spring Security's delegating encoder (bcrypt by default). Login accepts normalized username or email and always returns the same `INVALID_CREDENTIALS` message for an unknown account or wrong password. Suspended and deleted accounts cannot log in; disabled principals and sessions are denied at HTTP and STOMP boundaries.

## CSRF

`GET /api/auth/csrf` creates/returns the session token:

```json
{"headerName":"X-CSRF-TOKEN","token":"opaque-session-token"}
```

The frontend keeps this token in memory and includes the returned header on every POST, PUT, PATCH and DELETE with `credentials: include`. Missing or incorrect tokens receive a structured 403. The explicit `dev` profile compatibility routes are the only CSRF exception.

## Account endpoints

- `POST /api/auth/register` — username, email, display name and password; returns 201.
- `POST /api/auth/login` — `login` plus `password`; establishes a session.
- `POST /api/auth/logout` — invalidates the current session; returns 204.
- `GET /api/auth/me` — current account summary.
- `POST /api/auth/verify-email` — one-time raw token from mail.
- `POST /api/auth/resend-verification` — authenticated; returns 204.
- `POST /api/auth/forgot-password` — always returns the same accepted message.
- `POST /api/auth/reset-password` — one-time token and replacement password; returns 204.

Registration stores normalized username/email under unique database constraints. Verification tokens expire after 24 hours; reset tokens expire after one hour. Raw 256-bit secrets exist only in the outgoing link. PostgreSQL stores SHA-256 hashes, expiry, creation and one-time use metadata. Issuing a replacement invalidates older unused tokens.

Identity lookup applies Unicode NFKC normalization followed by locale-independent lowercasing; display names are stored in NFC and reject control/format characters. Usernames remain ASCII letters, digits and underscore. Spring's delegating encoder writes bcrypt cost-10 hashes and preserves an upgrade path.

Reset and verification pages retain the token only in ephemeral component memory and immediately replace the browser URL. A 401 clears in-memory CSRF, React Query data and match navigation data before returning to login.

Registration, login, resend, forgot-password and reset-password boundaries use configurable single-instance rate limits. See `.env.example`. The local counters are deliberately not a distributed abuse-prevention system.
