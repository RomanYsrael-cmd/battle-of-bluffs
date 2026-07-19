# Chat and player safety

Match chat is private to the two authenticated participants. It is deliberately separate from match commands and match-state sequences: chat cannot move a piece, alter a clock, decide a result or disclose a hidden rank.

## Message rules

- Plain text only; the UI renders bodies as text nodes and never interprets HTML or turns links into executable markup.
- Leading and trailing whitespace is removed. Empty messages and messages over 500 UTF-16 characters are rejected.
- The server generates the UUID, timestamp and ordered per-match sequence.
- A player may send five messages in a rolling ten-second window for each match by default; `CHAT_RATE_LIMIT` and `CHAT_RATE_WINDOW` configure local deployments.
- The latest 100 persisted messages are returned for initial history; an `afterSequence` cursor supports ordered incremental reads.
- Deleted or unavailable accounts appear as `Former player` rather than exposing stale private account details.

## Mute, block and report

Mute is a browser-local display preference. It hides the opponent's messages only for that browser and has no server-side effect.

A block is durable and directional in storage, but either direction disables chat between the pair. A block also prevents those accounts from joining each other's future casual rooms. Blocking during a match does not change the board, timer, terminal result or disclosure rules. Unblocking is explicit and CSRF-protected.

Reports are private records and are not exposed through public profiles. A participant selects a category and may include a trimmed comment of up to 1000 characters. Up to 20 chat references may be included, but the server accepts only messages authored by the reported opponent in that same match. The opponent identity is derived by the server; clients cannot choose an arbitrary reported account.

No administrator dashboard is part of this milestone.
