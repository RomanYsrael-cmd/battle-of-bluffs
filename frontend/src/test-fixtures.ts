import type {
  CommandResponse,
  CurrentMatchSummary,
  OwnPieceView,
  PlayerMatchView,
  PlayerSide,
} from './api/types'
import { createInventory } from './game/ranks'

export const fullOwnFormation = (side: PlayerSide): OwnPieceView[] => {
  const startRow = side === 'PLAYER_ONE' ? 0 : 5
  return createInventory().map((piece, index) => ({
    ...piece,
    position: { row: startRow + Math.floor(index / 9), column: index % 9 },
    alive: true,
  }))
}

export const matchView = (overrides: Partial<PlayerMatchView> = {}): PlayerMatchView => ({
  matchId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  roomCode: 'ABC234',
  version: 1,
  liveSequence: 1,
  phase: 'FORMATION',
  mode: 'CASUAL',
  timerMode: 'CASUAL_UNTIMED',
  requestingPlayerId: 'dev-player-a',
  requestingSide: 'PLAYER_ONE',
  playerOneOccupied: true,
  playerTwoOccupied: false,
  playerOneLocked: false,
  playerTwoLocked: false,
  currentPlayer: null,
  ownPieces: [],
  opponentPieces: [],
  events: [],
  pendingFlagChallenge: null,
  terminalResult: null,
  postMatchPieces: [],
  timer: {
    playerOneRemainingMillis: 0,
    playerTwoRemainingMillis: 0,
    formationDeadline: null,
    activeTurnDeadline: null,
    incrementMillis: 0,
    serverTimestamp: '2026-07-19T10:00:00Z',
  },
  presence: {
    playerOneConnected: true,
    playerTwoConnected: true,
    playerOneDisconnectedSince: null,
    playerTwoDisconnectedSince: null,
    playerOneCumulativeDisconnectedMillis: 0,
    playerTwoCumulativeDisconnectedMillis: 0,
    disconnectGraceMillis: 60_000,
    rankedCumulativeAllowanceMillis: 0,
    serverTimestamp: '2026-07-19T10:00:00Z',
  },
  ...overrides,
})

export const commandResponse = (view: PlayerMatchView): CommandResponse => ({
  commandId: null,
  version: view.version,
  matchId: view.matchId,
  roomCode: view.roomCode,
  view,
})

export const currentMatchSummary = (
  overrides: Partial<CurrentMatchSummary> = {},
): CurrentMatchSummary => ({
  matchId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  mode: 'CASUAL',
  phase: 'FORMATION',
  version: 1,
  roomCode: 'ABC234',
  side: 'PLAYER_ONE',
  opponentPresent: false,
  ownFormationSubmitted: false,
  ownLocked: false,
  opponentLocked: false,
  currentPlayer: null,
  createdAt: '2026-07-19T09:00:00Z',
  updatedAt: '2026-07-19T10:00:00Z',
  canResume: true,
  canCancel: true,
  canLeave: false,
  resumeRoute: '/matches/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  ...overrides,
})

export const jsonResponse = (body: unknown, status = 200): Response => new Response(
  JSON.stringify(body),
  { status, headers: { 'Content-Type': 'application/json' } },
)
