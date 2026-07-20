# Privacy

Battle of Bluffs processes account/profile information, security/session metadata, match records, ratings, and participant text chat as described by the application. Optional audio/video adds a direct real-time media channel operated through LiveKit Cloud only after each participant explicitly enables it.

Microphone and camera are off initially. The browser requests access only after a user enters the optional media experience and activates the relevant device. A user can independently stop publishing mic or camera, mute/hide the opponent locally, change local playback volume, select devices, or leave media without leaving or affecting the match.

Participant authorization may continue for a ten-minute post-match conversation window, but each issued credential expires after five minutes. Navigation away disconnects media. Permission denial, unavailable devices, provider failure, or leaving media does not alter match clocks, moves, presence, results, chat, or ratings.

This application does not record, transcribe, store, stream, broadcast, or analyze audio/video and does not implement recording, egress, screen sharing, or SIP. Media packets and the minimum connection metadata needed to operate the room transit LiveKit Cloud under that provider's terms and retention practices. The application backend does not receive media tracks. It mints a short-lived scoped credential containing an opaque match room identity, opaque participant identity, and display name.

Text chat is separate and remains persisted for participant history and safety/report evidence. Local mute/hide/volume preferences do not change the opponent's device or server state. Blocking prevents future media authorization in either direction and immediately leaves media in the blocking browser; it does not change an authoritative game result.

Users should avoid sharing sensitive information over live media. Browser/site permission can also be revoked through browser settings. For access, deletion, safety, or privacy requests, contact the repository/site operator through the published private support channel; do not include passwords, reset links, session cookies, or LiveKit credentials.
