import type {
  CommandResponse,
  FormationItem,
  PlayerMatchView,
  ChatMessage,
  ModerationStatus,
  ReportCategory,
  ReportReceipt,
} from './types'
import type { Position } from '../game/coordinates'
import { apiRequest, HttpApiError } from './http'

const MATCHES_URL = '/api/matches'

export const ERROR_MESSAGES: Record<string, string> = {
  MATCH_NOT_FOUND: 'That room could not be found.',
  MATCH_FULL: 'That room already has two players.',
  INVALID_FORMATION: 'The server rejected this formation. Review every piece and position.',
  ALREADY_LOCKED: 'Your formation is already locked.',
  PLAYER_NOT_IN_MATCH: 'Your account does not belong to this match.',
  STALE_VERSION: 'The match changed. Synchronizing the latest state…',
  COMMAND_CONFLICT: 'That command identifier was already used for another action.',
  ILLEGAL_MOVE: 'The server rejected that move as illegal.',
  TERMINAL_MATCH: 'This match has already ended.',
  INVALID_ROOM_STATE: 'That action is not available in the current match phase.',
  INVALID_REQUEST: 'The request was incomplete or invalid.',
  AUTHENTICATION_REQUIRED: 'Sign in to continue.',
  SERVER_UNAVAILABLE: 'The game server is unavailable. Check that the backend is running.',
}

export class MatchApiError extends Error {
  constructor(public readonly code: string, public readonly status: number) {
    super(ERROR_MESSAGES[code] ?? 'The match server could not complete that action.')
    this.name = 'MatchApiError'
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  try {
    return await apiRequest<T>(`${MATCHES_URL}${path}`, init)
  } catch (error) {
    if (error instanceof HttpApiError) throw new MatchApiError(error.code, error.status)
    throw new MatchApiError('SERVER_UNAVAILABLE', 0)
  }
}

export const createMatch = (timerMode: 'CASUAL_UNTIMED' | 'STANDARD_15_PLUS_5' = 'CASUAL_UNTIMED'):
Promise<CommandResponse> => request('', { method: 'POST', body: JSON.stringify({ timerMode }) })

export const joinMatch = (roomCode: string): Promise<CommandResponse> =>
  request('/join', {
    method: 'POST',
    body: JSON.stringify({ commandId: crypto.randomUUID(), roomCode, expectedVersion: 1 }),
  })

export const getPlayerView = (matchId: string): Promise<PlayerMatchView> => request(`/${matchId}`)

export const submitFormation = (
  matchId: string,
  expectedVersion: number,
  pieces: FormationItem[],
): Promise<CommandResponse> => request(`/${matchId}/formation`, {
  method: 'PUT',
  body: JSON.stringify({ commandId: crypto.randomUUID(), expectedVersion, pieces }),
})

export const lockFormation = (matchId: string, expectedVersion: number): Promise<CommandResponse> =>
  request(`/${matchId}/lock`, {
    method: 'POST',
    body: JSON.stringify({ commandId: crypto.randomUUID(), expectedVersion }),
  })

export const makeMove = (
  matchId: string,
  expectedVersion: number,
  source: Position,
  destination: Position,
): Promise<CommandResponse> => request(`/${matchId}/moves`, {
  method: 'POST',
  body: JSON.stringify({ commandId: crypto.randomUUID(), expectedVersion, source, destination }),
})

export const resign = (matchId: string, expectedVersion: number): Promise<CommandResponse> =>
  request(`/${matchId}/resign`, {
    method: 'POST',
    body: JSON.stringify({ commandId: crypto.randomUUID(), expectedVersion }),
  })

export const getChatHistory = (matchId: string): Promise<ChatMessage[]> =>
  request(`/${matchId}/chat`)

export const getModerationStatus = (matchId: string): Promise<ModerationStatus> =>
  request(`/${matchId}/moderation`)

export const blockOpponent = (matchId: string): Promise<ModerationStatus> =>
  request(`/${matchId}/block`, { method: 'POST' })

export const unblockOpponent = (matchId: string): Promise<ModerationStatus> =>
  request(`/${matchId}/block`, { method: 'DELETE' })

export const reportOpponent = (
  matchId: string,
  category: ReportCategory,
  comment: string,
  chatMessageReferences: string[],
): Promise<ReportReceipt> => request(`/${matchId}/reports`, {
  method: 'POST',
  body: JSON.stringify({ category, comment, chatMessageReferences }),
})
