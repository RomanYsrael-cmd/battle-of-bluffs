import type { Position } from '../game/coordinates'
import type { Rank } from '../game/ranks'

export type PlayerSide = 'PLAYER_ONE' | 'PLAYER_TWO'
export type MatchPhase = 'FORMATION' | 'ACTIVE' | 'TERMINAL'

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
  roomCode: string
  version: number
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
}

export interface CommandResponse {
  commandId: string | null
  version: number
  matchId: string
  roomCode: string
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
}
