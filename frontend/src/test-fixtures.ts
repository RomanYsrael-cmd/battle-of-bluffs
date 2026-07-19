import type {
  CommandResponse,
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
  ...overrides,
})

export const commandResponse = (view: PlayerMatchView): CommandResponse => ({
  commandId: null,
  version: view.version,
  matchId: view.matchId,
  roomCode: view.roomCode,
  view,
})

export const jsonResponse = (body: unknown, status = 200): Response => new Response(
  JSON.stringify(body),
  { status, headers: { 'Content-Type': 'application/json' } },
)
