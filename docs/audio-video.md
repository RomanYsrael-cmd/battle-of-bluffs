# Optional audio and video

## Boundary and consent

LiveKit Cloud carries only live microphone and camera tracks between the two authenticated match participants. The Spring/PostgreSQL/STOMP path remains authoritative for gameplay, chat, clocks, presence, reconnect grace, versions, results, and ratings. A media disconnect, denial, mute, browser refresh, or provider outage cannot pause, forfeit, score, or otherwise mutate a match.

The panel is collapsed by default. It requests neither a token nor device access until **Enable audio/video** is selected. After connection, microphone and camera remain off until separately enabled. **Leave media** disconnects only LiveKit. Opponent mute, volume, and video hide are local controls; hidden remote video is unsubscribed when the SDK permits it. Autoplay refusal exposes an explicit **Allow opponent audio** action.

No recording, egress, screen sharing, SIP, broadcasting, transcription, or media storage is implemented. Duplicate tabs deliberately receive the same stable identity for the same match/account/participant cycle; LiveKit's duplicate-identity behavior prevents them from becoming extra human seats.

## Server authorization

`POST /api/matches/{matchId}/media-token` requires the authenticated session and CSRF token. It re-reads the account and allows only email-verified `ACTIVE` accounts that occupy a seat in the requested match after the opponent has joined. Suspended, deleted, unverified, outsider, blocked, missing-opponent, and expired post-match requests fail safely. Requests are rate-limited per account. A small tested JDK HMAC-SHA256 signer emits the documented LiveKit JWT grant shape. This avoids adding vulnerable Kotlin build/runtime or legacy JSON dependencies to the Spring process while retaining an interoperable LiveKit token.

Room and participant names are HMAC-derived server values bound to the match's persisted participant cycle. A casual guest replacement therefore receives a different room, while browser-session consent is also scoped to the account, match, and participant cycle. Clients cannot choose either identifier. Returned JWTs expire after five minutes and grant only join to that room, subscribe, and publication from `camera` and `microphone`; data publication, screen share, room administration, creation, recording, and arbitrary rooms are absent. Only the two server-authorized match identities can mint tokens. Token issuance is read-only with respect to the match aggregate. Tokens remain available for ten minutes after the authoritative terminal timestamp, then new issuance is denied.

## LiveKit Cloud production setup

1. In an operator-controlled LiveKit Cloud account, create a dedicated project for Battle of Bluffs. Do not reuse an unrelated application's project.
2. Copy the project's exact `wss://…livekit.cloud` URL, API key, and API secret. Store the key/secret only in the protected backend environment; never paste them into chat, Git, Vercel public variables, logs, screenshots, or `VITE_*` values.
3. Add to the backend secret environment:

   ```text
   MEDIA_ENABLED=true
   LIVEKIT_URL=wss://EXACT-PROJECT.livekit.cloud
   LIVEKIT_API_KEY=<server-only key>
   LIVEKIT_API_SECRET=<server-only secret>
   LIVEKIT_TOKEN_TTL=5m
   MEDIA_POST_MATCH_WINDOW=10m
   ```

4. Set only `VITE_LIVEKIT_URL=wss://EXACT-PROJECT.livekit.cloud` in the Vercel production environment.
5. In `frontend/vercel.json`, append that exact WebSocket origin—and its exact `https://` equivalent if the selected SDK/provider flow requires it—to `connect-src`. Do not use `*` or `*.livekit.cloud`. Confirm `Permissions-Policy` remains `camera=(self), microphone=(self)`.
6. Build and inspect the public bundle: it may contain the public LiveKit URL, but must contain neither API key nor secret. Start the backend with the `prod` profile once with an intentionally missing LiveKit value and confirm startup fails before restoring the protected value.
7. Deploy only after backend/frontend/unit/security gates pass. Validate with two verified controlled accounts in separate Chromium contexts: opt-in, initial mic/camera off, independent toggles, device switching, two-way audio/video, local mute/hide/volume, refresh/rejoin, retry after interruption, opponent leaving media while gameplay continues, outsider denial, block denial, and token expiration/renewal.

The existing Vercel CSP intentionally does not include a placeholder or wildcard LiveKit origin. Production media will remain blocked until step 5 is completed with the real project URL.

## Operations and incidents

Provider failures are handled as a media-only incident. Verify the normal REST/STOMP match path first, then LiveKit Cloud status, project status, browser permission/autoplay state, token endpoint response codes, and the exact CSP origin. Do not restart or modify the RomanLMS service to repair media.

Rotate a suspected LiveKit secret in the LiveKit dashboard and backend secret store, restart only the isolated Battle of Bluffs backend, and verify old token issuance is no longer possible. Existing short-lived tokens naturally expire in at most five minutes. Disable media immediately with `MEDIA_ENABLED=false` if authorization integrity is uncertain; gameplay remains available.

Rollback the frontend to the prior healthy Vercel deployment and set `MEDIA_ENABLED=false` before rolling back the isolated backend. Never introduce a self-hosted production LiveKit instance as an outage workaround.
