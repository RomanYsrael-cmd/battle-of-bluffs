import type { Position } from '../game/coordinates'
import type { Rank } from '../game/ranks'

export type PlayerSide = 'PLAYER_ONE' | 'PLAYER_TWO'
export type MatchPhase = 'FORMATION' | 'ACTIVE' | 'TERMINAL'

export interface CurrentMatchSummary {
  matchId: string
  mode: 'CASUAL' | 'RANKED'
  phase: MatchPhase
  version: number
  roomCode: string | null
  side: PlayerSide
  opponentPresent: boolean
  ownFormationSubmitted: boolean
  ownLocked: boolean
  opponentLocked: boolean
  currentPlayer: PlayerSide | null
  createdAt: string | null
  updatedAt: string | null
  canResume: boolean
  canCancel: boolean
  canLeave: boolean
  resumeRoute: string
}

export interface CurrentMatchesResponse {
  activities: CurrentMatchSummary[]
  multipleOpenMatches: boolean
}

export interface LifecycleResponse {
  commandId: string
  version: number
  matchId: string
  action: 'ROOM_CANCELLED' | 'LOBBY_LEFT'
}

export interface TerminalResult {
  winner: PlayerSide | null
  reason: string
}

export interface OwnPieceView {
  id: string
  rank: Rank
  position: Position | null
  alive: boolean
}

export interface OpponentPieceView {
  id: string
  position: Position
}

export interface RevealedPieceView {
  id: string
  owner: PlayerSide
  rank: Rank
  position: Position | null
  alive: boolean
}

export type PublicEventType =
  | 'MATCH_CREATED'
  | 'PLAYER_JOINED'
  | 'FORMATION_LOCKED'
  | 'MATCH_STARTED'
  | 'MOVE_APPLIED'
  | 'BATTLE_RESOLVED'
  | 'FLAG_CHALLENGE_STARTED'
  | 'MATCH_ENDED'
  | 'PLAYER_RESIGNED'
  | 'PLAYER_LEFT'
  | 'ROOM_CANCELLED'

export interface EventView {
  sequence: number
  type: PublicEventType
  actor: PlayerSide | null
  source: Position | null
  destination: Position | null
  removedPieceIds: string[]
  ownBattleOutcome: string | null
  terminalResult: TerminalResult | null
}

export interface PendingChallengeView {
  advancedFlagId: string
  flagPosition: Position
  respondingPlayer: PlayerSide
  eligibleOwnChallengerIds: string[]
}

export interface PlayerMatchView {
  matchId: string
  roomCode: string | null
  version: number
  liveSequence: number
  phase: MatchPhase
  mode: 'CASUAL' | 'RANKED'
  timerMode: 'CASUAL_UNTIMED' | 'STANDARD_15_PLUS_5'
  requestingPlayerId: string
  requestingSide: PlayerSide
  playerOneOccupied: boolean
  playerTwoOccupied: boolean
  playerOneLocked: boolean
  playerTwoLocked: boolean
  currentPlayer: PlayerSide | null
  ownPieces: OwnPieceView[]
  opponentPieces: OpponentPieceView[]
  events: EventView[]
  pendingFlagChallenge: PendingChallengeView | null
  terminalResult: TerminalResult | null
  postMatchPieces: RevealedPieceView[]
  timer: TimerView
  presence: PresenceView
}

export interface TimerView {
  playerOneRemainingMillis: number
  playerTwoRemainingMillis: number
  formationDeadline: string | null
  activeTurnDeadline: string | null
  incrementMillis: number
  serverTimestamp: string
}

export interface PresenceView {
  playerOneConnected: boolean
  playerTwoConnected: boolean
  playerOneDisconnectedSince: string | null
  playerTwoDisconnectedSince: string | null
  playerOneCumulativeDisconnectedMillis: number
  playerTwoCumulativeDisconnectedMillis: number
  disconnectGraceMillis: number
  rankedCumulativeAllowanceMillis: number
  serverTimestamp: string
}

export interface CommandResponse {
  commandId: string | null
  version: number
  matchId: string
  roomCode: string | null
  view: PlayerMatchView
}

export interface FormationItem {
  pieceId: string
  rank: Rank
  row: number
  column: number
}

export interface ApiErrorBody {
  code: string
  message: string
  timestamp: string
  context?: unknown
}

export interface ChatMessage {
  id: string
  matchId: string
  sequence: number
  senderDisplayName: string
  ownMessage: boolean
  body: string
  serverTimestamp: string
}

export interface ChatError {
  code: string
  message: string
  serverTimestamp: string
}

export interface ModerationStatus {
  opponentDisplayName: string
  blockedByYou: boolean
}

export type ReportCategory = 'ABUSE' | 'HARASSMENT' | 'CHEATING' | 'SPAM' | 'OTHER'

export interface ReportReceipt {
  reportId: string
  message: string
}
