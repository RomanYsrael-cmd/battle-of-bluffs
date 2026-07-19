# Battle of Bluffs — Game Rules Specification

Status: **Approved rules baseline**

Scope: rules shared by clients, the game server, automated tests, match records, and the multiplayer protocol.

This document is the authoritative rules specification for Battle of Bluffs. Where the online product intentionally selects among traditional Game of the Generals variants, the selected behavior is stated as an **ONLINE RULE**.

## 1. Rule classifications

- **CLASSIC RULE** — part of the traditional over-the-board game adopted by this specification.
- **ONLINE RULE** — required specifically for a fair, deterministic online match.
- **OPTIONAL HOUSE RULE** — a supported candidate only; it is inactive unless a match configuration explicitly enables it.
- **OPEN DECISION** — unresolved and not implementable until product-owner confirmation. This baseline currently contains no open decisions.

“Must” and “must not” are normative. Unless a subsection says otherwise, its rules are **CLASSIC RULES**. Online security, timing, disconnect, and protocol requirements are labeled separately.

## 2. Game overview

**CLASSIC RULE.** Battle of Bluffs is a two-player, turn-based, hidden-information strategy game based on Game of the Generals. Each player commands 21 pieces. A player wins by capturing the opposing Flag, by successfully advancing their own Flag to the opponent's back row under section 9, or through another explicitly supported victory condition in section 9.

Each player sees the identities of their own pieces. Throughout an active match, the opponent sees occupied enemy cells but never their ranks, including after battle. Players may arrange pieces to disguise their Flag, threaten with weak pieces, or invite attacks with strong pieces. Bluffing changes decisions but never changes battle strength.

A player's **side** is the three-row deployment zone nearest that player. **Opponent territory** means the four board rows on the opponent's side of the center line; entering it has no special effect except for the Flag rule on the opponent's back row. There is no ownership restriction on ordinary cells after play starts.

## 3. Board and coordinates

### 3.1 Dimensions and cells

**CLASSIC RULE.** The board is a rectangular grid of 8 ranks by 9 files, for 72 cells. Every cell is playable. There are no lakes, blocked cells, safe zones, headquarters, or terrain effects.

### 3.2 Canonical application coordinates

**ONLINE RULE.** The canonical coordinate is `(row, column)`:

- `row` is an integer from `0` through `7`.
- `column` is an integer from `0` through `8`.
- Player 1's back row is row `0`; Player 1 advances toward increasing row numbers.
- Player 2's back row is row `7`; Player 2 advances toward decreasing row numbers.
- Columns do not reverse between players. Column `0` is the same physical file for both players.

Coordinates in protocol messages, persistence, tests, and match history always use this canonical orientation. A client may rotate the visual board so its local player appears at the bottom, but it must not transform stored or transmitted coordinates.

| Meaning | Player 1 | Player 2 |
|---|---:|---:|
| Back/home row | `0` | `7` |
| Initial formation rows | `0–2` | `5–7` |
| Opponent's back row | `7` | `0` |
| Forward row delta | `+1` | `-1` |

Rows `3` and `4` begin empty and form the neutral middle. “Forward” is descriptive only: movable pieces may also move backward or sideways.

## 4. Complete piece set

**CLASSIC RULE.** Each player has exactly 21 pieces. Battle strength is ordered from strongest to weakest below, subject to the Spy/Private exceptions. Stars are part of the rank names and are not quantities.

| Rank | Quantity | Ordinary relative strength | May move? | Special battle behavior |
|---|---:|---:|:---:|---|
| Five-Star General | 1 | 14 | Yes | None |
| Four-Star General | 1 | 13 | Yes | None |
| Three-Star General | 1 | 12 | Yes | None |
| Two-Star General | 1 | 11 | Yes | None |
| One-Star General | 1 | 10 | Yes | None |
| Colonel | 1 | 9 | Yes | None |
| Lieutenant Colonel | 1 | 8 | Yes | None |
| Major | 1 | 7 | Yes | None |
| Captain | 1 | 6 | Yes | None |
| First Lieutenant | 1 | 5 | Yes | None |
| Second Lieutenant | 1 | 4 | Yes | None |
| Sergeant | 1 | 3 | Yes | None |
| Private | 6 | 2 | Yes | Defeats a Spy; otherwise follows ordinary rank order |
| Spy | 2 | special | Yes | Defeats every movable rank except a Private; ties another Spy |
| Flag | 1 | 1 | Yes | Loses to any non-Flag; an attacking Flag defeats a defending Flag; back-row rule applies |
| **Total** | **21** |  |  |  |

The numeric strengths above are specification conveniences, not piece identities exposed to an opponent. They must not be used alone to resolve Spy battles.

## 5. Initial formation

### 5.1 Placement constraints

**CLASSIC RULE.** Before play, each player privately places all 21 of their pieces in any 21 distinct cells within their 27-cell formation area: rows `0–2` for Player 1 and rows `5–7` for Player 2. Exactly six cells in each formation area therefore remain empty. Pieces have no prescribed starting cells.

A formation is valid if and only if:

1. it contains exactly the player's full piece inventory from section 4;
2. no two pieces share a cell;
3. every occupied cell is inside that player's formation rows.

There is no additional placement restriction on the Flag.

### 5.2 Editing and locking

**ONLINE RULE.** A player may place, remove, swap, or rearrange their own pieces before locking. Locking submits an immutable formation commitment. After locking, that player cannot edit or unlock it. The server validates the complete formation at lock time.

The match enters normal play only after both valid formations are locked. Neither player may learn whether the other player has locked in a way that leaks formation content.

**ONLINE RULE.** For an initial match, the server selects the first player using an unbiased server-side random process. For each direct rematch between the same two players, the first player alternates from the immediately preceding match. Client input cannot influence the random selection.

### 5.3 Formation secrecy

**ONLINE RULE.** Before and during an active match, a player may receive their own complete formation and the opponent's occupied coordinates, but never any opponent rank. Lock acknowledgements, presence events, and errors must not disclose opponent ranks or formation ordering.

## 6. Legal movement

The following are **CLASSIC RULES**:

- On their turn, a player makes exactly one legal move. A successful battle-producing move is still one move.
- A movable piece moves exactly one cell orthogonally: up, down, left, or right in canonical coordinates.
- Diagonal movement is illegal.
- A piece may not remain in place or move more than one cell.
- A piece may not jump. Because movement is one cell, the destination alone can block the move.
- A piece may not move onto a cell occupied by an allied piece.
- A piece may move to an empty cell or attack an adjacent enemy-occupied cell.
- The Flag is movable and follows exactly the same one-cell orthogonal movement rules.
- Moving off the board, moving an opponent's piece, moving from an empty cell, moving outside one's turn, or moving after the match is terminal is illegal.
- A rejected move changes no board state, turn, history, or clock entitlement; clock time already elapsed while choosing the move is not restored.

**ONLINE RULE.** A legal accepted move atomically updates the board and then passes the turn unless that move makes the match terminal. The server must reject duplicate or stale move submissions rather than apply them twice.

## 7. Battle resolution

### 7.1 Trigger and ordinary result

**CLASSIC RULE.** A battle occurs when a player moves a piece (the attacker) onto an orthogonally adjacent cell occupied by an opponent (the defender). Attacker status gives no strength advantage except in Flag-versus-Flag battle.

- If one participating piece defeats the other, the defeated piece is removed and the winner occupies the contested destination cell.
- If the ranks are equal, both pieces are removed and the destination becomes empty, except that an attacking Flag defeats a defending Flag.
- If a Flag is defeated, the match ends immediately under section 9. The surviving attacker, if any, may be represented on the destination in the final board.

### 7.2 Battle-resolution matrix

Read the row as the attacker and the column as the defender. “Higher/lower” compares the ordinary strengths in section 4.

| Attacker | Defender | Result |
|---|---|---|
| Ordinary non-Spy rank | Lower ordinary non-Spy rank | Attacker survives on destination; defender removed |
| Ordinary non-Spy rank | Higher ordinary non-Spy rank | Attacker removed; defender remains |
| Same rank | Same rank | Both removed; destination empty |
| Spy | General through Sergeant | Spy survives; officer removed |
| General through Sergeant | Spy | Officer removed; Spy remains |
| Spy | Private | Spy removed; Private remains |
| Private | Spy | Private survives; Spy removed |
| Spy | Spy | Both removed |
| Private | General through Sergeant | Private removed; officer remains |
| General through Sergeant | Private | Officer survives; Private removed |
| Private | Private | Both removed |
| Any non-Flag movable piece | Flag | Flag is captured; attacker wins the match |
| Flag | Any non-Flag enemy piece | Attacking Flag is captured; defender wins the match |
| Flag | Flag | Attacking Flag captures defending Flag and wins immediately; no split |

“Ordinary non-Spy rank” excludes the Flag. Flag interactions are resolved only by the final three rows of the matrix. A Flag loses against any non-Flag enemy piece regardless of which piece initiated the battle. Flag-versus-Flag is the sole asymmetric rank pairing: the attacking Flag wins.

### 7.3 Spy and Private edge cases

**CLASSIC RULE.** Spy and Private behavior is symmetric: it does not depend on which one attacks.

- A Spy defeats every General and every officer down through Sergeant.
- A Private defeats a Spy.
- Equal Spies remove each other.
- Equal Privates remove each other.
- A Private loses to every rank from Sergeant through Five-Star General.
- A Spy-versus-Flag battle results in capture of the Flag when the Spy is the non-Flag piece.
- No rank, attacker/defender status, prior revelation, board location, or previous battle modifies these outcomes.

These cases are mandatory `BattleResolver` test partitions: each ordered Spy/officer pairing; both ordered Spy/Private pairings; Spy/Spy; Private/Private; each ordered Private/officer pairing; each non-Flag rank against Flag; and every equal-rank pairing.

Mandatory Flag test partitions include every ordered Flag/non-Flag pairing, attacking Flag versus defending Flag, Flag capture from ordinary play, and Flag capture during a pending back-row challenge.

### 7.4 Revelation and history

**ONLINE RULE.** Opponent ranks are never disclosed during an active match, including in battle events and battle history. After a battle, each player receives only:

- whether their own participating piece won, lost, or split;
- which piece ID or piece IDs were removed;
- the contested coordinate;
- attacker and defender ownership;
- the move number; and
- the terminal outcome and reason, when applicable.

Each player already knows the rank of their own participating piece. Neither player's active-match event may contain the opposing participant's rank. The two player-specific outcome views may therefore differ: for example, one says `OWN_PIECE_WON` and the other says `OWN_PIECE_LOST`.

Active-match battle history permanently records these outcome facts without opponent ranks. Full ranks become available only after the match is terminal under section 13.

## 8. Worked battle examples

1. **Spy attacks Five-Star General.** Authoritatively, the General is removed and the Spy moves onto the contested cell. During the match, each participant learns their own outcome and the removal, but neither receives the opponent's rank.
2. **Private attacks Spy.** Authoritatively, the Spy is removed and the Private moves onto the contested cell. If the Spy attacks the Private, the Spy is removed and the Private stays in place. Active events still omit the opponent's rank.
3. **Two Captains meet.** Both Captains are removed and the destination is empty. Each player receives `OWN_PIECE_SPLIT`; the event does not disclose that the opponent was a Captain.
4. **Sergeant attacks Private.** The Private is removed and the Sergeant occupies the destination. A Private attacking a Sergeant is instead removed, leaving the Sergeant in place.
5. **Non-Flag attacks Flag.** The Flag is removed and its owner loses immediately; no later move or animation can reverse the result.
6. **Flag attacks non-Flag.** The attacking Flag is removed and its owner loses immediately, regardless of the defender's rank.
7. **Flag attacks Flag.** The defending Flag is removed and the attacking Flag's owner wins immediately. The Flags do not split.

## 9. Flag rules and victory conditions

### 9.1 Flag capture

**CLASSIC RULE.** A player wins immediately when any of their pieces defeats the opponent's Flag in battle. A non-Flag always defeats a Flag. When two Flags meet, the attacking Flag defeats the defending Flag. The server records `FLAG_CAPTURE` as the reason. The losing player receives no reply turn.

### 9.2 Flag reaches the opponent's back row

**ONLINE RULE.** After a Flag reaches any cell of the opponent's back row, the server examines the orthogonally adjacent cells:

1. If no adjacent enemy piece can legally move onto and challenge the Flag immediately, its owner wins immediately with reason `FLAG_BACK_ROW`.
2. Otherwise, the server enters a pending Flag challenge state and gives the opponent exactly one challenge turn.
3. The required challenge is a move by an adjacent enemy piece onto the advanced Flag's cell. Any non-Flag challenger defeats it. An enemy Flag may challenge it and, as the attacking Flag, also defeats it.
4. If that challenge move is accepted, the advanced Flag is captured and the challenger wins with reason `FLAG_CAPTURE`.
5. If the opponent submits a different move that would be legal during an ordinary turn, that choice immediately ends the challenge and the advanced Flag's owner wins with reason `FLAG_BACK_ROW`; the different move is not applied. A malformed or otherwise illegal command is merely rejected while the challenge and clock continue. If the opponent resigns, times out, disconnect-forfeits, or otherwise loses the turn without an accepted challenge, that terminal reason takes precedence as specified in sections 11 and 12.

The pending challenge marker, advanced Flag ID and coordinate, eligible challenger IDs, and responding player are authoritative state. They must be included in persistence, state restoration, repetition identity, and transition tests.

Example: Player 1 moves their Flag to row `7`. If Player 2 has no adjacent piece able to move onto it, Player 1 wins immediately. If Player 2 has an eligible adjacent piece, a challenge turn begins. Capturing the Flag wins for Player 2; allowing the authoritative deadline to expire instead loses by `TIMEOUT`.

### 9.3 Other wins and losses

- **CLASSIC RULE.** A player may resign; the opponent wins immediately with reason `RESIGNATION`.
- **ONLINE RULE.** If an enabled play clock reaches zero, that player loses with reason `TIMEOUT`.
- **ONLINE RULE.** A player who begins their turn with no legal move loses with reason `IMMOBILIZATION`.
- **ONLINE RULE.** Eliminating all of an opponent's non-Flag pieces is not a victory condition. A surviving Flag remains movable and may attempt to reach the opposing back row. Immobilization applies only if that player actually has no legal move at the start of their turn.

Only the server may set a terminal outcome. Once terminal, no further formation, move, timer, resignation, or reconnection event changes the result.

## 10. Draw conditions

These are **ONLINE RULES** introduced to make online matches finite and deterministic:

- The match is automatically drawn when the same complete authoritative position occurs for the third time. The occurrences need not be consecutive. The server evaluates a win or loss caused by the accepted move before evaluating repetition.
- Position identity includes every live piece's owner, immutable rank, and coordinate; the player to move; all clock-independent game state; and the complete pending Flag challenge state. Clock values, deadlines, connection state, and move number are excluded.
- The match is automatically drawn immediately after the 300th accepted move resolves, unless that move first creates a win or loss. A battle move and a pending-challenge move each count as one accepted move. Rejected commands do not count.
- Casual matches support mutual draw agreement. A draw offer does not consume the offerer's move or pause a clock; the match becomes terminal only when the opponent accepts it. Ranked matches reject draw-offer and draw-accept commands.
- There is no “no progress” draw. A player with no legal move loses by immobilization.

The server computes repetition from hidden authoritative state. Position hashes, ranks, or other hidden inputs used for repetition must never be exposed to clients. A client may receive only the occurrence count if that value cannot reveal hidden information; otherwise it receives only the terminal draw event.

## 11. Timer behavior

Timers are **ONLINE RULES**. The MVP supports exactly these play-clock modes:

1. **Untimed casual:** no play clock and no play-timeout victory.
2. **Timed:** each player starts with 15 minutes. After each accepted nonterminal move, five seconds are added to the mover's remaining time. The increment is not added after a terminal move or rejected command.

Every match also has a separate five-minute, server-authoritative formation timer beginning when formation setup opens:

- If both players lock valid formations before its deadline, normal play begins.
- If exactly one player has locked a valid formation when it expires, that player wins with reason `SETUP_TIMEOUT`.
- If neither player has locked when it expires, the match ends with outcome `NO_CONTEST`.

For a timed match, only the active player's play clock decreases. It starts when the server opens that player's normal or pending-challenge turn and stops when the server accepts a legal move or makes the match terminal. Time spent submitting rejected commands remains charged. After an accepted nonterminal move, the server applies the five-second increment before starting the opponent's clock.

A move received and accepted by the server before the authoritative deadline resolves before timeout, even if the result reaches clients after the deadline. If the active clock reaches zero first, its owner loses with reason `TIMEOUT`. This includes a player responding to a pending Flag challenge. Only one play clock runs at a time, so simultaneous play-clock expiration is invalid state.

Client rendering, network delivery of an accepted result, battle animation, and UI acknowledgement consume neither player's clock. The server's monotonic time source and recorded deadlines are authoritative; client countdowns are estimates only. Timer configuration is fixed before formation locking and included in public match metadata.

## 12. Disconnect and reconnection

All rules in this section are **ONLINE RULES**.

Video/voice transport is independent of the game connection. Its loss, muting, or refusal has no gameplay effect and does not start a gameplay reconnection grace period. Loss of the authenticated game-server connection starts a 60-second grace period for that disconnected player. The authoritative play clock, when applicable, continues throughout disconnection.

- Except while both players remain disconnected as specified below, a player forfeits when their current 60-second disconnection grace period expires.
- In a ranked match, a player also forfeits when their cumulative game-server disconnection time reaches 120 seconds. Each disconnected interval contributes its elapsed duration, including an interval ending in reconnection or terminal state.
- Casual private rooms have no cumulative disconnection limit, but the 60-second per-disconnection grace still applies.
- A disconnected active player in a timed match also loses if their play clock reaches zero.
- Whichever applicable terminal condition occurs first determines the recorded reason: `DISCONNECT_FORFEIT`, `CUMULATIVE_DISCONNECT_FORFEIT`, or `TIMEOUT`. Equal timestamps are resolved by the server's deterministic event ordering and persisted as the single authoritative result.
- **ONLINE RULE:** a reconnecting player authenticates again and resumes the same player seat; reconnection never permits changing sides or formation.
- **ONLINE RULE:** after authentication, the server sends that player a fresh, versioned state containing the current board, turn, clocks/deadlines, connection allowances, match status, rank-free active battle history, pending challenge state, and that player's own ranks. Opponent pieces contain only opaque IDs and coordinates, never ranks.
- **ONLINE RULE:** commands based on an obsolete state version are rejected or idempotently acknowledged; they are not replayed as new moves.

If both players disconnect, the server tracks both grace periods, cumulative allowances, and any running play clock independently. If neither reconnects before both current grace periods expire, the match ends as `NO_CONTEST`; the first grace expiry alone does not end the match while both remain disconnected. If one player reconnects, the other player's grace period and applicable clock continue normally and may cause that player to forfeit. A play-clock timeout that occurs before both grace periods expire remains a `TIMEOUT` because the earliest terminal condition controls.

Video reconnection must not trigger a board-state restoration containing game secrets. Game-state restoration occurs only over the authenticated game channel.

## 13. Server-authoritative and secrecy requirements

The following are non-negotiable **ONLINE RULES**:

1. The server validates formation inventory, placement, lock state, turn ownership, source ownership, movement geometry, destination occupancy, match state, and state version for every command.
2. The server resolves every battle from its authoritative hidden state. A client never submits or chooses a battle outcome.
3. The server alone advances turns, applies clocks, determines Flag state, and declares victory, loss, draw, or cancellation.
4. Client animations, predicted movement, cached ranks, countdowns, and video evidence have no authority. A client must reconcile to the server result.
5. During an active match, every opponent rank—whether or not that piece has battled—must be omitted from player-visible frontend state, API responses, WebSocket messages, errors, telemetry or logs visible to that player, notifications, battle history, and replay data accessible to that player.
6. Active battle events disclose outcomes and removals only as authorized by section 7.4. They never disclose the opponent's participating rank.
7. Opaque opponent piece identifiers must not encode rank, original formation slot, or another reversible secret.
8. Active spectators, if supported later, never receive hidden ranks.
9. After a match is terminal, both participating players may access both complete formations, every piece rank, the complete move and battle replay, and the terminal outcome and reason.
10. Spectators may receive full-information replay data only after the match is terminal.

Security logging available only to trusted operators may contain authoritative state when operationally necessary, but access must be restricted and it must never be routed to a participant during an active match.

## 14. Invariants

These statements must remain true after every accepted command and are suitable for property or state-transition tests:

1. The board has exactly 72 addressable cells with canonical coordinates in range.
2. At most one live piece occupies a cell, and every live piece occupies exactly one cell.
3. Each piece belongs permanently to exactly one of the two players and has one immutable rank.
4. No player ever has more than the initial inventory for any rank or more than 21 live pieces.
5. Pieces are removed, never created or transferred, after formation lock.
6. Before both locks, each submitted locked formation has exactly 21 pieces in its owner's three formation rows; afterward, formation content is immutable.
7. In a nonterminal play state after formation, exactly one player has the turn; a pending challenge turn belongs to the opponent of the advanced Flag's owner.
8. An accepted nonterminal move changes the turn exactly once. A rejected command changes no authoritative board, history, or turn state, although authoritative clocks and disconnection durations continue with elapsed time.
9. An ordinary move changes a piece's coordinate by Manhattan distance exactly one.
10. A battle has exactly one attacker and one defender of different owners and produces only a matrix-authorized result.
11. Equal ranks remove both pieces, including Spy/Spy and Private/Private, except that an attacking Flag defeats a defending Flag without a split.
12. A terminal match has exactly one immutable outcome and reason; no later game command is accepted.
13. During an active match, player-visible state contains ranks only for that player's pieces. Opponent ranks never appear in battle events or active history.
14. Canonical coordinates never depend on which player or client is viewing them.
15. Server state, not client presentation, determines legality, time, battles, and outcome.
16. A pending Flag challenge accepts only an eligible adjacent challenge move and is included in persistence, restoration, and repetition identity.
17. Only one play clock runs at a time; an accepted nonterminal timed move adds exactly five seconds to the mover before the next clock starts.
18. A terminal match makes full-information replay data available to both participants, but never retroactively exposes ranks in an active-match event stream.

## 15. Resolved decisions

All previously open decisions are approved and incorporated into the normative sections above:

| IDs | Resolution summary |
|---|---|
| OD-01–OD-03 | Movable Flag; server-selected/alternating first player; explicit Flag battle behavior |
| OD-04 | Active battles expose outcomes and removals, never opponent ranks |
| OD-05–OD-07 | Exact back-row challenge; immobilization loss; no separate non-Flag-elimination victory |
| OD-08 | Threefold repetition, 300-move limit, and casual-only mutual draws |
| OD-09–OD-10 | MVP setup/play timers, increment, deadline ordering, and timeout loss |
| OD-11–OD-15 | Grace periods, continuing clocks, ranked cumulative limit, forfeiture, and dual disconnect |
| OD-16 | Full information for participants and eligible spectators only after terminal state |

There are no remaining **OPEN DECISION** rules in this baseline.
